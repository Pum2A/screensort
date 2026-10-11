"""Public HTTP data shapes, independent of Java persistence entities."""

from typing import Literal

from pydantic import BaseModel


class HealthResponse(BaseModel):
    """Liveness is not a claim that OCR or visual models are ready."""

    service: Literal["screensort-analysis"]
    status: Literal["ok"]
    analysis_available: Literal[False]
