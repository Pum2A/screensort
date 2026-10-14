from pathlib import Path
from time import perf_counter

import torch
from PIL import Image
from transformers import AutoProcessor, Florence2ForConditionalGeneration

from screensort_analysis.api.schemas import VisualResult
from screensort_analysis.image.preparation import (
    MAX_IMAGE_EDGE,
    MAX_IMAGE_PIXELS,
    validate_prepared_image,
)
from screensort_analysis.vision.model import MODEL_ID, MODEL_REVISION


class VisionAnalyzer:
    MODEL_ID = MODEL_ID
    MODEL_REVISION = MODEL_REVISION

    TASK = "<CAPTION>"
    MAX_NEW_TOKENS = 96
    MAX_IMAGE_PIXELS = MAX_IMAGE_PIXELS
    MAX_IMAGE_EDGE = MAX_IMAGE_EDGE

    def __init__(self, model_cache: Path):
        load_options = {
            "revision": self.MODEL_REVISION,
            "cache_dir": str(model_cache),
            "local_files_only": True,
            "trust_remote_code": False,
        }

        self._processor = AutoProcessor.from_pretrained(
            self.MODEL_ID,
            **load_options,
        )

        self._model = Florence2ForConditionalGeneration.from_pretrained(
            self.MODEL_ID,
            dtype=torch.float32,
            use_safetensors=True,
            **load_options,
        ).to("cpu")

        self._model.eval()

    def analyze(self, image: Image.Image) -> VisualResult:
        validate_prepared_image(image)
        started_at = perf_counter()

        with torch.inference_mode():
            inputs = self._processor(
                text=self.TASK,
                images=image,
                return_tensors="pt",
            )

            generated_ids = self._model.generate(
                **inputs,
                max_new_tokens=self.MAX_NEW_TOKENS,
                num_beams=1,
                do_sample=False,
            )

        generated_text = self._processor.batch_decode(
            generated_ids,
            skip_special_tokens=False,
        )[0]

        parsed = self._processor.post_process_generation(
            generated_text,
            task=self.TASK,
            image_size=image.size,
        )

        description = parsed.get(self.TASK)

        if not isinstance(description, str) or not description.strip():
            raise RuntimeError("Model did not return a usable caption.")

        elapsed_ms = (perf_counter() - started_at) * 1000

        return VisualResult(
            description=description.strip(),
            model=self.MODEL_ID,
            model_revision=self.MODEL_REVISION,
            task="caption",
            device="cpu",
            duration_ms=elapsed_ms,
        )
