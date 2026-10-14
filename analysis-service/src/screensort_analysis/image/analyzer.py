"""Coordinate one prepared image without HTTP, database or filesystem access."""

from __future__ import annotations

from contextlib import closing
from hashlib import sha256
from typing import TYPE_CHECKING

from screensort_analysis.api.schemas import ImageAnalysisResult, OcrOptions
from screensort_analysis.image.preparation import prepare_image
from screensort_analysis.ocr.recognizer import OcrRecognizer

if TYPE_CHECKING:
    # OCR-only imports must not require the optional visual runtime.
    from screensort_analysis.vision.analyzer import VisionAnalyzer


class ImageAnalyzer:
    """Reuse caller-created engines; propagate failures instead of fake success."""

    def __init__(self, ocr: OcrRecognizer, vision: VisionAnalyzer):
        self._ocr = ocr
        self._vision = vision

    def analyze(
        self,
        image_bytes: bytes,
        options: OcrOptions,
    ) -> ImageAnalysisResult:
        with closing(prepare_image(image_bytes)) as image:
            source_sha256 = sha256(image_bytes).hexdigest()

            ocr_result = self._ocr.recognize_prepared(image, options)
            visual_result = self._vision.analyze(image)

        return ImageAnalysisResult(
            source_sha256=source_sha256,
            ocr=ocr_result,
            visual=visual_result,
        )
