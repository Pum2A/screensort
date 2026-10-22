"""Explicit developer download; analyzing an image never invokes this module."""

import argparse
import os
from pathlib import Path

from screensort_analysis.vision.model import MODEL_FILES, MODEL_ID, MODEL_REVISION


def download_model(model_cache: Path) -> Path:
    os.environ.setdefault("HF_HUB_DISABLE_TELEMETRY", "1")
    os.environ.setdefault("HF_HUB_DISABLE_IMPLICIT_TOKEN", "1")
    from huggingface_hub import snapshot_download

    snapshot = snapshot_download(
        repo_id=MODEL_ID,
        revision=MODEL_REVISION,
        cache_dir=str(model_cache),
        allow_patterns=list(MODEL_FILES),
        token=False,
        max_workers=2,
    )
    return Path(snapshot)


def main() -> None:
    parser = argparse.ArgumentParser(description="Download pinned Florence data files.")
    parser.add_argument("--model-cache", type=Path, default=Path("models"))
    args = parser.parse_args()
    download_model(args.model_cache.resolve())
    print(f"Model: {MODEL_ID}")
    print(f"Revision: {MODEL_REVISION}")
    print("Snapshot downloaded.")


if __name__ == "__main__":
    main()
