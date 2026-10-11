"""Run isolated child processes sequentially, preserve raw data, validate outputs."""
import hashlib
from datetime import datetime
import json
import os
from pathlib import Path
import platform
import statistics
import subprocess
import sys
import threading
import time

import numpy as np
import onnxruntime as ort
import psutil

ROOT = Path(__file__).resolve().parent
RUNTIME = ROOT / '.runtime'
WARMUP = 20
ITERATIONS = 200
REPEATS = 5


def sha256(path):
    with path.open('rb') as source:
        return hashlib.file_digest(source, 'sha256').hexdigest()


def execute(command, environment):
    start = time.perf_counter_ns()
    child = subprocess.Popen(command, env=environment, stdout=subprocess.PIPE,
                             stderr=subprocess.PIPE, text=True, encoding='utf-8')
    lines = []
    errors = []
    ready_ms = []

    def read_stdout():
        for line in child.stdout:
            lines.append(line)
            if '"ready"' in line:
                ready_ms.append((time.perf_counter_ns() - start) / 1e6)

    stdout_thread = threading.Thread(target=read_stdout)
    stderr_thread = threading.Thread(target=lambda: errors.extend(child.stderr.readlines()))
    stdout_thread.start()
    stderr_thread.start()
    process = psutil.Process(child.pid)
    peak_rss = 0
    try:
        while child.poll() is None:
            try:
                info = process.memory_info()
                peak_rss = max(peak_rss, getattr(info, 'peak_wset', info.rss))
            except psutil.NoSuchProcess:
                pass
            if (time.perf_counter_ns() - start) / 1e9 > 120:
                child.kill()
                raise TimeoutError('Benchmark process exceeded 120 s.')
            time.sleep(0.005)
    finally:
        child.wait()
        stdout_thread.join()
        stderr_thread.join()
    if child.returncode:
        raise RuntimeError(''.join(errors))
    messages = [json.loads(line) for line in lines if line.startswith('{')]
    ready = next(message for message in messages if message['event'] == 'ready')
    result = next(message for message in messages if message['event'] == 'result')
    if 'engine_build_info' in result:
        assert result['engine_build_info'] == ort.get_build_info()
        result['engine'] = ort.__version__  # Identical DLL hash and actual native build info.
    assert '1.26.0' in result['engine'], result['engine']
    assert len(result['times_ms']) == ITERATIONS
    assert len(result['output']) == 1000
    return {**ready, **result, 'process_to_first_result_ms': ready_ms[0],
            'peak_working_set_mib': peak_rss / 2**20}


def main():
    assert ort.__version__ == '1.26.0', 'Install pinned requirements in an isolated environment.'
    assert sys.platform == 'win32', 'This harness currently targets Windows x64.'
    RUNTIME.mkdir(exist_ok=True)
    # Fixed, synthetic RGB pixels; NOT a screenshot quality evaluation.
    rng = np.random.default_rng(20261011)
    inputs = rng.random((16, 1, 3, 224, 224), dtype=np.float32)
    mean = np.array([0.485, 0.456, 0.406], dtype=np.float32).reshape(1, 1, 3, 1, 1)
    std = np.array([0.229, 0.224, 0.225], dtype=np.float32).reshape(1, 1, 3, 1, 1)
    inputs = ((inputs - mean) / std).astype('<f4')
    input_file = RUNTIME / 'inputs.f32'
    inputs.tofile(input_file)
    engine_dll = RUNTIME / 'onnxruntime.dll'
    python_dll = Path(ort.__file__).parent / 'capi' / 'onnxruntime.dll'
    assert sha256(engine_dll) == sha256(python_dll), 'Native engines must be identical.'
    model = RUNTIME / 'mobilenetv2-12.onnx'
    jar = RUNTIME / 'onnxruntime-1.26.0.jar'
    commands = {
        'Python': [sys.executable, str(ROOT / 'python_bench.py')],
        'Java': ['java', '--enable-native-access=ALL-UNNAMED',
                 f'-Donnxruntime.native.path={RUNTIME}', '-cp', f'{RUNTIME};{jar}', 'JavaBench'],
        'Rust': [str(RUNTIME / 'rust-target' / 'release' / 'screensort-vision-bench.exe')],
    }
    environment = os.environ.copy()
    environment['ORT_DYLIB_PATH'] = str(engine_dll)
    runs = []
    reference = None
    for threads in (1, 4):
        for repeat in range(REPEATS):
            names = list(commands)
            offset = repeat % len(names)
            for name in names[offset:] + names[:offset]:
                command = commands[name] + [str(model), str(input_file), str(threads), str(WARMUP), str(ITERATIONS)]
                result = execute(command, environment)
                output = np.array(result['output'], dtype=np.float32)
                if reference is None:
                    reference = output
                # Check numeric output, not just that the process ran.
                np.testing.assert_allclose(output, reference, atol=1e-4, rtol=1e-4)
                result.pop('output')
                result.update(language=name, threads=threads, repeat=repeat,
                              median_ms=statistics.median(result['times_ms']),
                              p95_ms=float(np.percentile(result['times_ms'], 95)),
                              max_output_difference=float(np.max(np.abs(output - reference))))
                runs.append(result)
                print(f"{name:6} threads={threads} repeat={repeat + 1}: "
                      f"median={result['median_ms']:.3f} ms; peak={result['peak_working_set_mib']:.1f} MiB", flush=True)
    measured_at = datetime.now().astimezone()
    report = {
        'date': measured_at.date().isoformat(), 'measured_at': measured_at.isoformat(), 'platform': platform.platform(),
        'python': sys.version, 'numpy': np.__version__, 'onnxruntime': ort.__version__,
        'java': subprocess.check_output(['java', '-version'], stderr=subprocess.STDOUT, text=True).strip(),
        'rust': subprocess.check_output(['rustc', '--version'], text=True).strip(),
        'logical_processors': psutil.cpu_count(), 'physical_cores': psutil.cpu_count(logical=False),
        'ram_gib': psutil.virtual_memory().total / 2**30,
        'model_sha256': sha256(model), 'engine_sha256': sha256(engine_dll),
        'input_sha256': sha256(input_file), 'jar_sha256': sha256(jar),
        'warmup': WARMUP, 'iterations': ITERATIONS, 'repeats': REPEATS,
        'input': '16 synthetic normalized RGB tensors, 1x3x224x224, rotating, batch size 1',
        'provider': 'CPUExecutionProvider', 'runs': runs,
        'reference_output': reference.tolist(),
    }
    result_file = ROOT / f'results-{measured_at.date().isoformat()}.json'
    result_file.write_text(json.dumps(report, indent=2), encoding='utf-8')
    print('\nAggregate: median of five process-level medians (ms)')
    for threads in (1, 4):
        for name in commands:
            subset = [run for run in runs if run['language'] == name and run['threads'] == threads]
            print(name, threads, {field: round(statistics.median(run[field] for run in subset), 3)
                  for field in ('median_ms', 'p95_ms', 'load_ms', 'process_to_first_result_ms', 'peak_working_set_mib')})


if __name__ == '__main__':
    main()
