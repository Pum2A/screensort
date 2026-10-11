"""Assemble the HTTP application without loading models at import time."""

from fastapi import FastAPI

from screensort_analysis.api.health import router as health_router


def create_app() -> FastAPI:
    """Create a service instance; analysis engines are not implemented yet."""
    app = FastAPI(
        title="ScreenSort Analysis Service",
        version="0.1.0",
        description="Local service scaffold. Only the health endpoint is implemented.",
        redoc_url=None,
    )
    app.include_router(health_router)
    return app
