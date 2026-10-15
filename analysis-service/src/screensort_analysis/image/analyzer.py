"""Coordinate one prepared image without HTTP, database or filesystem access."""

from __future__ import annotations

import logging
from contextlib import closing
from hashlib import sha256
from typing import TYPE_CHECKING

from screensort_analysis.api.schemas import (
    AnalysisError,
    CompletedAnalysis,
    FailedAnalysis,
    ImageAnalysisResult,
    OcrAnalysis,
    OcrOptions,
    OcrResult,
    SkippedAnalysis,
    VisualAnalysis,
    VisualResult,
)
from screensort_analysis.image.preparation import prepare_image
from screensort_analysis.ocr.recognizer import OcrRecognizer

if TYPE_CHECKING:
    # OCR-only imports must not require the optional visual runtime.
    from screensort_analysis.vision.analyzer import VisionAnalyzer

logger = logging.getLogger(__name__)


class ImageAnalyzer:
    """Reuse engines and preserve successful results when another step fails."""

    def __init__(self, ocr: OcrRecognizer, vision: VisionAnalyzer):
        self._ocr = ocr
        self._vision = vision

    def analyze(
        self,
        image_bytes: bytes,
        options: OcrOptions,
        *,
        run_ocr: bool = True,
        run_visual: bool = True,
    ) -> ImageAnalysisResult:
        if not run_ocr and not run_visual:
            raise ValueError("Select OCR or visual analysis.")

        ocr: OcrAnalysis = SkippedAnalysis()
        visual: VisualAnalysis = SkippedAnalysis()

        with closing(prepare_image(image_bytes)) as image:
            source_sha256 = sha256(image_bytes).hexdigest()

            if run_ocr:
                try:
                    ocr_result = self._ocr.recognize_prepared(image, options)
                except MemoryError:
                    raise
                except Exception as exc:
                    logger.error("OCR_FAILED [%s]", type(exc).__name__)
                    ocr = FailedAnalysis(
                        error=AnalysisError(
                            code="OCR_FAILED",
                            message="Text recognition failed.",
                        )
                    )
                else:
                    ocr = CompletedAnalysis[OcrResult](result=ocr_result)

            if run_visual:
                try:
                    visual_result = self._vision.analyze(image)
                except MemoryError:
                    raise
                except Exception as exc:
                    logger.error("VISUAL_FAILED [%s]", type(exc).__name__)
                    visual = FailedAnalysis(
                        error=AnalysisError(
                            code="VISUAL_FAILED",
                            message="Visual analysis failed.",
                        )
                    )
                else:
                    visual = CompletedAnalysis[VisualResult](result=visual_result)

        return ImageAnalysisResult(
            source_sha256=source_sha256,
            ocr=ocr,
            visual=visual,
        )
