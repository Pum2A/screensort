"""Test coordination with explicit fake engines, not fabricated model evidence."""

from contextlib import closing
from hashlib import sha256
from io import BytesIO
from threading import Event
from types import SimpleNamespace
from unittest.mock import Mock

import pytest
from PIL import Image
from pydantic import ValidationError

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
    # The steps run side by side, so only both calls are guaranteed, not their order.
    assert sorted(call[0] for call in calls.mock_calls) == ["ocr", "vision"]
    assert result.source_sha256 == sha256(data).hexdigest()
    assert result.ocr.status == "COMPLETED"
    assert result.ocr.result == ocr.recognize_prepared.return_value
    assert result.ocr.error is None
    assert result.visual.status == "COMPLETED"
    assert result.visual.result == vision.analyze.return_value
    assert result.visual.error is None
    assert ImageAnalysisResult.model_validate_json(result.model_dump_json()) == result
    with pytest.raises(ValueError, match="closed"):
        image.getpixel((0, 0))


def test_ocr_and_caption_run_at_the_same_time(data, engines):
    ocr, vision = engines
    caption_started = Event()
    text = ocr.recognize_prepared.return_value

    def recognize(image, options):
        # Fails if the caption only starts after OCR has finished.
        assert caption_started.wait(5)
        return text

    def describe(image):
        caption_started.set()
        return vision.analyze.return_value

    ocr.recognize_prepared.side_effect = recognize
    vision.analyze.side_effect = describe

    result = ImageAnalyzer(ocr, vision).analyze(data, OcrOptions())

    assert result.ocr.status == "COMPLETED"
    assert result.visual.status == "COMPLETED"


def test_bad_input_stops_before_either_engine(engines):
    ocr, vision = engines

    with pytest.raises(OSError):
        ImageAnalyzer(ocr, vision).analyze(b"fake image", OcrOptions())

    ocr.recognize_prepared.assert_not_called()
    vision.analyze.assert_not_called()


@pytest.mark.parametrize("failed_engine", ["ocr", "vision"])
def test_engine_failure_preserves_the_other_result_and_closes_the_shared_image(
    data, engines, failed_engine, caplog
):
    ocr, vision = engines
    failing_call = ocr.recognize_prepared if failed_engine == "ocr" else vision.analyze
    failing_call.side_effect = RuntimeError("Private engine details must stay private")

    result = ImageAnalyzer(ocr, vision).analyze(data, OcrOptions())

    failed = result.ocr if failed_engine == "ocr" else result.visual
    completed = result.visual if failed_engine == "ocr" else result.ocr
    expected_code = "OCR_FAILED" if failed_engine == "ocr" else "VISUAL_FAILED"
    expected_result = (
        vision.analyze.return_value
        if failed_engine == "ocr"
        else ocr.recognize_prepared.return_value
    )
    assert failed.status == "FAILED"
    assert failed.result is None
    assert failed.error.code == expected_code
    assert completed.status == "COMPLETED"
    assert completed.result == expected_result
    assert result.source_sha256 == sha256(data).hexdigest()
    assert ImageAnalysisResult.model_validate_json(result.model_dump_json()) == result
    assert "Private engine details" not in result.model_dump_json()
    assert "Private engine details" not in caplog.text
    assert f"{expected_code} [RuntimeError]" in caplog.text
    image = ocr.recognize_prepared.call_args.args[0]
    vision.analyze.assert_called_once_with(image)
    with pytest.raises(ValueError, match="closed"):
        image.getpixel((0, 0))


def test_both_engine_failures_are_reported_without_a_fabricated_result(data, engines):
    ocr, vision = engines
    ocr.recognize_prepared.side_effect = RuntimeError("OCR unavailable")
    vision.analyze.side_effect = RuntimeError("Vision unavailable")

    result = ImageAnalyzer(ocr, vision).analyze(data, OcrOptions())

    assert result.ocr.status == "FAILED"
    assert result.ocr.error.code == "OCR_FAILED"
    assert result.ocr.result is None
    assert result.visual.status == "FAILED"
    assert result.visual.error.code == "VISUAL_FAILED"
    assert result.visual.result is None
    assert ImageAnalysisResult.model_validate_json(result.model_dump_json()) == result


@pytest.mark.parametrize("skipped_engine", ["ocr", "vision"])
def test_unrequested_engine_is_not_called(data, engines, skipped_engine):
    ocr, vision = engines

    result = ImageAnalyzer(ocr, vision).analyze(
        data,
        OcrOptions(),
        run_ocr=skipped_engine != "ocr",
        run_visual=skipped_engine != "vision",
    )

    skipped = result.ocr if skipped_engine == "ocr" else result.visual
    completed = result.visual if skipped_engine == "ocr" else result.ocr
    skipped_call = ocr.recognize_prepared if skipped_engine == "ocr" else vision.analyze
    completed_call = (
        vision.analyze if skipped_engine == "ocr" else ocr.recognize_prepared
    )
    assert skipped.status == "SKIPPED"
    assert skipped.result is None
    assert skipped.error is None
    skipped_call.assert_not_called()
    assert completed.status == "COMPLETED"
    assert completed.result == completed_call.return_value
    completed_call.assert_called_once()
    assert ImageAnalysisResult.model_validate_json(result.model_dump_json()) == result
    image = completed_call.call_args.args[0]
    with pytest.raises(ValueError, match="closed"):
        image.getpixel((0, 0))


def test_selecting_neither_engine_stops_before_preparing_image(
    monkeypatch, data, engines
):
    prepare = Mock()
    monkeypatch.setattr(analyzer_module, "prepare_image", prepare)
    ocr, vision = engines

    with pytest.raises(ValueError, match="Select OCR or visual analysis"):
        ImageAnalyzer(ocr, vision).analyze(
            data, OcrOptions(), run_ocr=False, run_visual=False
        )

    prepare.assert_not_called()
    ocr.recognize_prepared.assert_not_called()
    vision.analyze.assert_not_called()


def test_empty_ocr_text_is_a_completed_result(data, engines):
    ocr, vision = engines
    ocr.recognize_prepared.return_value = OcrResult(
        text="", engine="test-only", engine_version="0", duration_ms=1
    )

    result = ImageAnalyzer(ocr, vision).analyze(data, OcrOptions())

    assert result.ocr.status == "COMPLETED"
    assert result.ocr.result.text == ""
    assert result.ocr.error is None


@pytest.mark.parametrize("failed_engine", ["ocr", "vision"])
@pytest.mark.parametrize("failure_type", [MemoryError, KeyboardInterrupt])
def test_fatal_failure_propagates_and_closes_the_shared_image(
    data, engines, failed_engine, failure_type
):
    ocr, vision = engines
    failing_call = ocr.recognize_prepared if failed_engine == "ocr" else vision.analyze
    failing_call.side_effect = failure_type("Stop processing")

    with pytest.raises(failure_type, match="Stop processing"):
        ImageAnalyzer(ocr, vision).analyze(data, OcrOptions())

    # Both steps run side by side, so the other one may already have finished.
    image = ocr.recognize_prepared.call_args.args[0]
    with pytest.raises(ValueError, match="closed"):
        image.getpixel((0, 0))


@pytest.mark.parametrize("invalid_engine", ["ocr", "vision"])
def test_invalid_result_is_a_contract_error_not_an_engine_failure(
    data, engines, invalid_engine
):
    ocr, vision = engines
    invalid_call = ocr.recognize_prepared if invalid_engine == "ocr" else vision.analyze
    invalid_call.return_value = None

    with pytest.raises(ValidationError):
        ImageAnalyzer(ocr, vision).analyze(data, OcrOptions())

    image = ocr.recognize_prepared.call_args.args[0]
    with pytest.raises(ValueError, match="closed"):
        image.getpixel((0, 0))
