"""Check the OCR wrapper without loading models or accessing the network."""

from contextlib import closing
from io import BytesIO
from types import SimpleNamespace
from unittest.mock import Mock

import pytest
from PIL import Image

from screensort_analysis.api.schemas import OcrOptions
from screensort_analysis.ocr import recognizer as recognizer_module


@pytest.fixture
def ocr(monkeypatch):
    engine = Mock(return_value=SimpleNamespace(txts=("ScreenSort", "Order 12345")))
    engine_factory = Mock(return_value=engine)
    monkeypatch.setattr(recognizer_module, "RapidOCR", engine_factory)
    instance = recognizer_module.OcrRecognizer()
    return instance, engine, engine_factory


def png_bytes():
    with closing(Image.new("RGB", (4, 3), "white")) as image, BytesIO() as output:
        image.save(output, format="PNG")
        return output.getvalue()


def test_existing_bytes_entry_point_returns_ocr_and_closes_its_image(ocr):
    instance, engine, _ = ocr

    result = instance.recognize(png_bytes(), OcrOptions())

    assert result.text == "ScreenSort\nOrder 12345"
    assert result.engine == "rapidocr"
    assert result.duration_ms >= 0
    with pytest.raises(ValueError, match="closed"):
        engine.call_args.args[0].getpixel((0, 0))


def test_prepared_entry_point_uses_the_same_image_without_closing_it(ocr):
    instance, engine, _ = ocr

    with closing(Image.new("RGB", (4, 3), "white")) as image:
        result = instance.recognize_prepared(image, OcrOptions())
        engine.assert_called_once_with(image)
        assert image.getpixel((0, 0)) == (255, 255, 255)
        assert result.text == "ScreenSort\nOrder 12345"


def test_ocr_engine_is_created_once_for_multiple_images(ocr):
    instance, engine, engine_factory = ocr

    instance.recognize(png_bytes(), OcrOptions())
    instance.recognize(png_bytes(), OcrOptions())

    engine_factory.assert_called_once()
    assert engine.call_count == 2


def test_no_recognized_lines_is_a_successful_empty_text_not_a_model_failure(ocr):
    instance, engine, _ = ocr
    engine.return_value = SimpleNamespace(txts=None)

    assert instance.recognize(png_bytes(), OcrOptions()).text == ""


@pytest.mark.parametrize("languages", [[], ["de"]])
def test_invalid_language_options_do_not_run_the_engine(ocr, languages):
    instance, engine, _ = ocr
    options = OcrOptions.model_construct(languages=languages)

    with closing(Image.new("RGB", (4, 3))) as image:
        with pytest.raises(ValueError, match="languages"):
            instance.recognize_prepared(image, options)

    engine.assert_not_called()


def test_non_rgb_prepared_input_does_not_run_the_engine(ocr):
    instance, engine, _ = ocr

    with closing(Image.new("RGBA", (4, 3))) as image:
        with pytest.raises(ValueError, match="RGB"):
            instance.recognize_prepared(image, OcrOptions())

    engine.assert_not_called()


def test_engine_failure_is_propagated_and_owned_image_is_closed(ocr):
    instance, engine, _ = ocr
    engine.side_effect = RuntimeError("OCR failed")

    with pytest.raises(RuntimeError, match="OCR failed"):
        instance.recognize(png_bytes(), OcrOptions())

    with pytest.raises(ValueError, match="closed"):
        engine.call_args.args[0].getpixel((0, 0))
