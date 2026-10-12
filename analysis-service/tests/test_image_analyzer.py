"""Test coordination with explicit fake engines, not fabricated model evidence."""

from contextlib import closing
from hashlib import sha256
from io import BytesIO
from types import SimpleNamespace
from unittest.mock import Mock

import pytest
from PIL import Image

from screensort_analysis.api.schemas import (
    ImageAnalysisResult,
    OcrOptions,
    OcrResult,
    VisualResult,
)
from screensort_analysis.image import analyzer as analyzer_module
from screensort_analysis.image.analyzer import ImageAnalyzer


@pytest.fixture
def data():
    with closing(Image.new("RGBA", (4, 3), (20, 40, 60, 128))) as image:
        with BytesIO() as output:
            image.save(output, format="PNG")
            return output.getvalue()


@pytest.fixture
def engines():
    ocr = SimpleNamespace(
        recognize_prepared=Mock(
            return_value=OcrResult(
                text="Test text", engine="test-only", engine_version="0", duration_ms=1
            )
        )
    )
    vision = SimpleNamespace(
        analyze=Mock(
            return_value=VisualResult(
                description="Test description; no real model was run.",
                model="test-only",
                model_revision="test-only",
                task="caption",
                device="cpu",
                duration_ms=2,
            )
        )
    )
    return ocr, vision


def test_prepares_once_and_passes_the_same_image_and_options_to_both_engines(
    monkeypatch, data, engines
):
    prepare = Mock(wraps=analyzer_module.prepare_image)
    monkeypatch.setattr(analyzer_module, "prepare_image", prepare)
    ocr, vision = engines
    calls = Mock()
    calls.attach_mock(ocr.recognize_prepared, "ocr")
    calls.attach_mock(vision.analyze, "vision")
    options = OcrOptions()

    result = ImageAnalyzer(ocr, vision).analyze(data, options)

    prepare.assert_called_once_with(data)
    image = ocr.recognize_prepared.call_args.args[0]
    ocr.recognize_prepared.assert_called_once_with(image, options)
    vision.analyze.assert_called_once_with(image)
    assert [call[0] for call in calls.mock_calls] == ["ocr", "vision"]
    assert result.source_sha256 == sha256(data).hexdigest()
    assert result.ocr == ocr.recognize_prepared.return_value
    assert result.visual == vision.analyze.return_value
    assert ImageAnalysisResult.model_validate_json(result.model_dump_json()) == result
    with pytest.raises(ValueError, match="closed"):
        image.getpixel((0, 0))


def test_bad_input_stops_before_either_engine(engines):
    ocr, vision = engines

    with pytest.raises(OSError):
        ImageAnalyzer(ocr, vision).analyze(b"fake image", OcrOptions())

    ocr.recognize_prepared.assert_not_called()
    vision.analyze.assert_not_called()


@pytest.mark.parametrize("failed_engine", ["ocr", "vision"])
def test_engine_failure_is_not_hidden_and_the_shared_image_is_closed(
    data, engines, failed_engine
):
    ocr, vision = engines
    failing_call = ocr.recognize_prepared if failed_engine == "ocr" else vision.analyze
    failing_call.side_effect = RuntimeError("Analysis failed")

    with pytest.raises(RuntimeError, match="Analysis failed"):
        ImageAnalyzer(ocr, vision).analyze(data, OcrOptions())

    image = ocr.recognize_prepared.call_args.args[0]
    with pytest.raises(ValueError, match="closed"):
        image.getpixel((0, 0))
    if failed_engine == "ocr":
        vision.analyze.assert_not_called()


def test_existing_ocr_only_result_still_serializes_without_visual_analysis(engines):
    ocr, _ = engines
    result = ImageAnalysisResult(
        source_sha256="0" * 64, ocr=ocr.recognize_prepared.return_value
    )

    assert result.visual is None
    assert result.model_dump(mode="json")["visual"] is None
