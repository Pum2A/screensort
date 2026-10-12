"""Opt-in real OCR check on generated text; no downloads or personal images."""

import os
from contextlib import closing
from io import BytesIO

import pytest
from PIL import Image, ImageDraw, ImageFont

from screensort_analysis.api.schemas import OcrOptions
from screensort_analysis.ocr.recognizer import OcrRecognizer


@pytest.mark.skipif(
    os.environ.get("SCREENSORT_RUN_OCR_TESTS") != "1",
    reason="Real OCR is opt-in; set SCREENSORT_RUN_OCR_TESTS=1.",
)
def test_real_ocr_recognizes_the_same_text_from_bytes_and_prepared_image(monkeypatch):
    from screensort_analysis.image.preparation import prepare_image

    def deny_network(*args, **kwargs):
        raise RuntimeError("Network disabled for OCR verification.")

    monkeypatch.setattr("requests.sessions.Session.request", deny_network)
    with closing(Image.new("RGB", (1200, 200), "white")) as image:
        draw = ImageDraw.Draw(image)
        font = ImageFont.load_default(size=40)
        draw.text((30, 30), "ScreenSort OCR test", fill="black", font=font)
        draw.text((30, 100), "Order number 12345", fill="black", font=font)
        with BytesIO() as output:
            image.save(output, format="PNG")
            data = output.getvalue()

    recognizer = OcrRecognizer()
    from_bytes = recognizer.recognize(data, OcrOptions())
    with closing(prepare_image(data)) as image:
        from_prepared = recognizer.recognize_prepared(image, OcrOptions())

    assert from_bytes.text == "ScreenSort OCR test\nOrder number 12345"
    assert from_prepared.text == from_bytes.text
