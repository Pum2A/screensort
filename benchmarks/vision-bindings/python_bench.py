"""Inference-only benchmark. No OCR, image decoding, HTTP or catalog access."""
import json
import sys
import time

import numpy as np
import onnxruntime as ort

model, input_file, thread_count, warmup, iterations = sys.argv[1:]
options = ort.SessionOptions()
options.intra_op_num_threads = int(thread_count)
options.inter_op_num_threads = 1
options.execution_mode = ort.ExecutionMode.ORT_SEQUENTIAL
options.graph_optimization_level = ort.GraphOptimizationLevel.ORT_ENABLE_ALL
start = time.perf_counter_ns()
session = ort.InferenceSession(model, options, providers=['CPUExecutionProvider'])
load_ms = (time.perf_counter_ns() - start) / 1e6
assert ort.__version__ == '1.26.0'
inputs = np.fromfile(input_file, dtype='<f4').reshape(-1, 1, 3, 224, 224)
name = session.get_inputs()[0].name


def infer(index):
    # Each binding reuses input tensors and materializes 1000 output floats.
    return session.run(None, {name: inputs[index % len(inputs)]})[0].ravel().copy()


start = time.perf_counter_ns()
output = infer(0)
first_ms = (time.perf_counter_ns() - start) / 1e6
print(json.dumps({'event': 'ready', 'load_ms': load_ms, 'first_ms': first_ms}), flush=True)
for index in range(int(warmup)):
    infer(index)
times = []
for index in range(int(iterations)):
    start = time.perf_counter_ns()
    output = infer(index)
    times.append((time.perf_counter_ns() - start) / 1e6)
print(json.dumps({'event': 'result', 'engine': ort.__version__, 'times_ms': times,
                  'output': output.tolist()}), flush=True)
