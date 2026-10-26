"""One owned model worker; HTTP cancellation never releases it early."""

from __future__ import annotations

import asyncio
import os
from collections.abc import Callable
from concurrent.futures import Future, ThreadPoolExecutor
from contextlib import closing
from io import BytesIO
from pathlib import Path
from threading import BoundedSemaphore, Event
from typing import TYPE_CHECKING

from screensort_analysis.api.schemas import (
    AnalysisOptions,
    ImageAnalysisResult,
    OcrOptions,
)

if TYPE_CHECKING:
    from screensort_analysis.image.analyzer import ImageAnalyzer


def load_analyzer() -> ImageAnalyzer:
    os.environ["HF_HUB_OFFLINE"] = "1"
    os.environ["HF_HUB_DISABLE_TELEMETRY"] = "1"
    os.environ["HF_HUB_DISABLE_IMPLICIT_TOKEN"] = "1"

    import torch
    from PIL import Image

    from screensort_analysis.image.analyzer import ImageAnalyzer
    from screensort_analysis.ocr.recognizer import OcrRecognizer
    from screensort_analysis.vision.analyzer import VisionAnalyzer

    # OCR and the caption run side by side; each gets half of the physical cores
    # (about a quarter of the logical ones). Measured 3+3 threads on 6 cores:
    # 6.7 s -> 3.6 s per screenshot.
    threads = max(1, min(8, (os.cpu_count() or 4) // 4))
    torch.set_num_threads(threads)
    cache = Path(os.environ.get("SCREENSORT_MODEL_CACHE", "models")).resolve()
    analyzer = ImageAnalyzer(OcrRecognizer(threads), VisionAnalyzer(cache))
    with closing(Image.new("RGB", (64, 64), "white")) as image:
        with BytesIO() as output:
            image.save(output, format="PNG")
            data = output.getvalue()
    result = analyzer.analyze(data, OcrOptions())
    if any(step.status != "COMPLETED" for step in (result.ocr, result.visual)):
        raise RuntimeError("MODEL_WARMUP_FAILED")
    return analyzer


class AnalysisRuntime:
    def __init__(self, loader: Callable[[], ImageAnalyzer] = load_analyzer):
        self._loader = loader
        self._executor = ThreadPoolExecutor(
            max_workers=1, thread_name_prefix="image-analysis"
        )
        self._slot = BoundedSemaphore(1)
        self._ready = Event()
        self._analyzer: ImageAnalyzer | None = None

    async def start(self) -> None:
        self._analyzer = await asyncio.wrap_future(self._executor.submit(self._loader))
        self._ready.set()

    @property
    def is_ready(self) -> bool:
        return self._ready.is_set()

    def reserve(self) -> bool:
        return self._slot.acquire(blocking=False)

    def release(self) -> None:
        self._slot.release()

    def submit_reserved(
        self, data: bytes, options: AnalysisOptions
    ) -> Future[ImageAnalysisResult]:
        if self._analyzer is None:
            raise RuntimeError("Analyzer is not initialized.")
        future = self._executor.submit(
            self._analyzer.analyze,
            data,
            OcrOptions(languages=options.languages),
            run_ocr=options.ocr,
            run_visual=options.visual,
        )
        future.add_done_callback(self._finished)
        return future

    def _finished(self, future: Future[ImageAnalysisResult]) -> None:
        try:
            if not future.cancelled() and isinstance(future.exception(), MemoryError):
                self._ready.clear()
        finally:
            self.release()

    async def close(self) -> None:
        self._ready.clear()
        await asyncio.to_thread(self._executor.shutdown, wait=True, cancel_futures=True)
        self._analyzer = None
