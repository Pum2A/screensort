"""Developer-only local image inspection; not an HTTP source-path endpoint."""

import argparse
import json
import os
import platform
import sys
from importlib.metadata import version
from pathlib import Path
from time import perf_counter

from screensort_analysis.image.preparation import MAX_IMAGE_BYTES


def read_image_bytes(path: Path) -> bytes:
    """Read only the explicitly selected regular file, with a bounded buffer."""
    if path.is_symlink() or not path.is_file():
        raise ValueError(
            "Choose a regular image file, not a directory or symbolic link."
        )

    with path.open("rb") as source:
        data = source.read(MAX_IMAGE_BYTES + 1)

    if not data:
        raise ValueError("Image must not be empty.")
    if len(data) > MAX_IMAGE_BYTES:
        raise ValueError("Image exceeds the 50 MiB limit.")
    return data


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Run local OCR and Florence on one file."
    )
    parser.add_argument("--image", type=Path, required=True)
    parser.add_argument("--model-cache", type=Path, default=Path("models"))
    parser.add_argument("--cpu-threads", type=int, default=min(4, os.cpu_count() or 1))
    parser.add_argument("--runs", type=int, default=2)
    args = parser.parse_args()
    if not 1 <= args.cpu_threads <= (os.cpu_count() or 1):
        parser.error("cpu-threads must be between 1 and the logical CPU count.")
    if not 1 <= args.runs <= 5:
        parser.error("runs must be between 1 and 5.")

    data = read_image_bytes(args.image)
    # These settings affect only this developer process, not the system environment.
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["HF_HUB_DISABLE_TELEMETRY"] = "1"
    os.environ["HF_HUB_DISABLE_IMPLICIT_TOKEN"] = "1"

    import torch

    from screensort_analysis.api.schemas import OcrOptions
    from screensort_analysis.image.analyzer import ImageAnalyzer
    from screensort_analysis.ocr.recognizer import OcrRecognizer
    from screensort_analysis.vision.analyzer import VisionAnalyzer

    torch.set_num_threads(args.cpu_threads)
    started_at = perf_counter()
    analyzer = ImageAnalyzer(
        ocr=OcrRecognizer(),
        vision=VisionAnalyzer(args.model_cache.resolve()),
    )
    initialization_ms = (perf_counter() - started_at) * 1000

    runs = []
    for number in range(1, args.runs + 1):
        started_at = perf_counter()
        result = analyzer.analyze(data, OcrOptions())
        elapsed_ms = (perf_counter() - started_at) * 1000
        runs.append(
            {
                "run": number,
                "total_ms": elapsed_ms,
                "analysis": result.model_dump(mode="json"),
            }
        )

    report = {
        "runtime": {
            "python": platform.python_version(),
            "torch": torch.__version__,
            "transformers": version("transformers"),
            "device": "cpu",
            "cpu_threads": torch.get_num_threads(),
            "cuda_build": torch.version.cuda,
        },
        "initialization_ms": initialization_ms,
        "runs": runs,
    }
    sys.stdout.reconfigure(encoding="utf-8")
    print(json.dumps(report, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
