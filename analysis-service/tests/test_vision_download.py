"""Check the download contract without network traffic or model files."""

import sys
from types import SimpleNamespace
from unittest.mock import Mock

from screensort_analysis.vision.download_model import download_model
from screensort_analysis.vision.model import MODEL_FILES, MODEL_ID, MODEL_REVISION


def test_download_is_pinned_and_does_not_use_an_account_or_remote_code(
    monkeypatch, tmp_path
):
    cache = tmp_path / "models"
    snapshot = cache / "snapshots" / MODEL_REVISION
    download = Mock(return_value=str(snapshot))
    monkeypatch.setitem(
        sys.modules, "huggingface_hub", SimpleNamespace(snapshot_download=download)
    )
    monkeypatch.delenv("HF_HUB_DISABLE_TELEMETRY", raising=False)
    monkeypatch.delenv("HF_HUB_DISABLE_IMPLICIT_TOKEN", raising=False)

    assert download_model(cache) == snapshot
    download.assert_called_once_with(
        repo_id=MODEL_ID,
        revision=MODEL_REVISION,
        cache_dir=str(cache),
        allow_patterns=list(MODEL_FILES),
        token=False,
        max_workers=2,
    )
    assert "model.safetensors" in MODEL_FILES
    assert not any(name.endswith((".py", ".bin", ".pt")) for name in MODEL_FILES)
