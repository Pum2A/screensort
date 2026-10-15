"""Opt-in offline full pipeline; nonempty caption is not a quality benchmark."""

import os
from contextlib import closing
from hashlib import sha256
from io import BytesIO
from pathlib import Path

import pytest
from PIL import Image, ImageDraw, ImageFont

from screensort_analysis.api.schemas import ImageAnalysisResult, OcrOptions
from screensort_analysis.vision.model import MODEL_ID, MODEL_REVISION


@pytest.mark.skipif(
    os.environ.get("SCREENSORT_RUN_VISION_TESTS") != "1",
    reason="Real vision is opt-in; install vision dependencies and cache the model.",
)
def test_real_ocr_and_florence_return_a_serializable_result_offline(monkeypatch):
    monkeypatch.setenv("HF_HUB_OFFLINE", "1")
    monkeypatch.setenv("HF_HUB_DISABLE_TELEMETRY", "1")

    def deny_network(*args, **kwargs):
        raise RuntimeError("Network disabled for inference verification.")

    monkeypatch.setattr("socket.socket.connect", deny_network)
    monkeypatch.setattr("socket.socket.connect_ex", deny_network)
    monkeypatch.setattr("requests.sessions.Session.request", deny_network)

    import torch

    from screensort_analysis.image.analyzer import ImageAnalyzer
    from screensort_analysis.ocr.recognizer import OcrRecognizer
    from screensort_analysis.vision.analyzer import VisionAnalyzer

    with closing(Image.new("RGB", (1200, 200), "white")) as image:
        draw = ImageDraw.Draw(image)
        font = ImageFont.load_default(size=40)
        draw.text((30, 30), "ScreenSort OCR test", fill="black", font=font)
        draw.text((30, 100), "Order number 12345", fill="black", font=font)
        with BytesIO() as output:
            image.save(output, format="PNG")
            data = output.getvalue()

    cache = Path(os.environ.get("SCREENSORT_VISION_MODEL_CACHE", "models"))
    previous_threads = torch.get_num_threads()
    torch.set_num_threads(min(4, os.cpu_count() or 1))
    try:
        analyzer = ImageAnalyzer(OcrRecognizer(), VisionAnalyzer(cache.resolve()))
        result = analyzer.analyze(data, OcrOptions())
    finally:
        torch.set_num_threads(previous_threads)

    assert result.source_sha256 == sha256(data).hexdigest()
    assert result.ocr.status == "COMPLETED"
    assert result.ocr.result.text == "ScreenSort OCR test\nOrder number 12345"
    assert result.ocr.error is None
    assert result.visual.status == "COMPLETED"
    assert result.visual.result.description.strip()
    assert result.visual.result.model == MODEL_ID
    assert result.visual.result.model_revision == MODEL_REVISION
    assert result.visual.result.device == "cpu"
    assert result.visual.result.task == "caption"
    assert result.visual.error is None
    assert ImageAnalysisResult.model_validate_json(result.model_dump_json()) == result
