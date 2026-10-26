from contextlib import closing
from importlib.metadata import version
from time import perf_counter

from PIL import Image
from rapidocr import RapidOCR

from screensort_analysis.api.schemas import OcrOptions, OcrResult
from screensort_analysis.image.preparation import (
    MAX_IMAGE_BYTES,
    MAX_IMAGE_EDGE,
    MAX_IMAGE_PIXELS,
    prepare_image,
    validate_prepared_image,
)


class OcrRecognizer:
    # Keep the existing console interface; limits are defined in one place.
    MAX_IMAGE_BYTES = MAX_IMAGE_BYTES
    MAX_IMAGE_PIXELS = MAX_IMAGE_PIXELS
    MAX_IMAGE_EDGE = MAX_IMAGE_EDGE

    def __init__(self, threads: int = 2):
        self._engine_version = version("rapidocr")

        self._engine = RapidOCR(
            params={
                "Global.log_level": "warning",
                "EngineConfig.onnxruntime.use_cuda": False,
                "EngineConfig.onnxruntime.intra_op_num_threads": threads,
                "EngineConfig.onnxruntime.inter_op_num_threads": 1,
            }
        )

    def recognize(
        self,
        image_bytes: bytes,
        options: OcrOptions,
    ) -> OcrResult:
        """Prepare bytes for standalone OCR, then release the owned image."""
        with closing(prepare_image(image_bytes)) as image:
            return self.recognize_prepared(image, options)

    def recognize_prepared(
        self,
        image: Image.Image,
        options: OcrOptions,
    ) -> OcrResult:
        """Recognize a borrowed RGB image; timing excludes shared preparation."""
        if not options.languages or not set(options.languages) <= {"pl", "en"}:
            raise ValueError("Supported languages are Polish and English.")

        validate_prepared_image(image)
        started_at = perf_counter()
        output = self._engine(image)

        recognized_text = "\n".join(output.txts or ())
        elapsed_ms = (perf_counter() - started_at) * 1000

        return OcrResult(
            text=recognized_text,
            engine="rapidocr",
            engine_version=self._engine_version,
            duration_ms=elapsed_ms,
        )
