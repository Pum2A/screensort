"""Public HTTP data shapes, independent of Java persistence entities."""

from typing import Literal

from pydantic import BaseModel, Field


class HealthResponse(BaseModel):
    """Liveness is not a claim that OCR or visual models are ready."""

    service: Literal["screensort-analysis"]
    status: Literal["ok"]
    analysis_available: Literal[False]


class OcrOptions(BaseModel):
    """Options passed to the text recognition function."""

    languages: list[Literal["pl", "en"]] = Field(
        default_factory=lambda: ["pl", "en"],
        min_length=1,
    )


class OcrResult(BaseModel):
    """Data returned after successful text recognition."""

    text: str
    engine: str
    engine_version: str
    duration_ms: float = Field(ge=0)


class VisualResult(BaseModel):
    """Data returned after successful visual analysis."""

    description: str = Field(min_length=1)
    model: str
    model_revision: str
    task: Literal["caption", "detailed_caption"]
    device: Literal["cpu", "cuda"]
    duration_ms: float = Field(ge=0, allow_inf_nan=False)


class ImageAnalysisResult(BaseModel):
    """Analysis result linked to the exact input content."""

    source_sha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    ocr: OcrResult
    visual: VisualResult | None = None
