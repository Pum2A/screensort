"""Assemble the HTTP application without loading models at import time."""

import os
import re
from collections.abc import Callable
from contextlib import asynccontextmanager

from fastapi import FastAPI, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from screensort_analysis.api.analysis import router as analysis_router
from screensort_analysis.api.health import router as health_router
from screensort_analysis.image.runtime import AnalysisRuntime


def create_app(
    runtime_factory: Callable[[], AnalysisRuntime] | None = None,
    token: str | None = None,
) -> FastAPI:
    """Production loads local models; tests inject an explicit fake runtime."""
    runtime_factory = runtime_factory or AnalysisRuntime
    token = (
        token if token is not None else os.environ.get("SCREENSORT_ANALYSIS_TOKEN", "")
    )
    if re.fullmatch(r"[A-Za-z0-9_-]{43,128}", token) is None:
        raise RuntimeError("Configure a random SCREENSORT_ANALYSIS_TOKEN.")

    @asynccontextmanager
    async def lifespan(app: FastAPI):
        runtime = runtime_factory()
        app.state.analysis_token = token
        app.state.analysis_runtime = runtime
        try:
            await runtime.start()
            yield
        finally:
            await runtime.close()

    app = FastAPI(
        title="ScreenSort Analysis Service",
        version="0.1.0",
        description="Local authenticated OCR and visual analysis.",
        lifespan=lifespan,
        redoc_url=None,
    )

    @app.middleware("http")
    async def prevent_caching(request: Request, call_next):
        response = await call_next(request)
        response.headers["Cache-Control"] = "no-store"
        return response

    @app.exception_handler(RequestValidationError)
    async def invalid_options(request: Request, exc: RequestValidationError):
        return JSONResponse(
            status_code=422,
            content={"detail": "INVALID_OPTIONS"},
            headers={"Cache-Control": "no-store"},
        )

    app.include_router(health_router)
    app.include_router(analysis_router)
    return app
