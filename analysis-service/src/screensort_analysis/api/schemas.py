"""Public HTTP data shapes, independent of Java persistence entities."""

from typing import Annotated, Literal

from pydantic import BaseModel, ConfigDict, Field


class HealthResponse(BaseModel):
    """Liveness is not a claim that OCR or visual models are ready."""

    service: Literal["screensort-analysis"]
    status: Literal["ok"]
    analysis_available: bool


class AnalysisOptions(BaseModel):
    """Only known, bounded options are accepted over HTTP."""

    model_config = ConfigDict(extra="forbid")
    ocr: bool = True
    visual: bool = True
    languages: list[Literal["pl", "en"]] = Field(
        default_factory=lambda: ["pl", "en"], min_length=1, max_length=2
    )
    # The backend asks for more time once, for an image that already timed out.
    timeout_seconds: int = Field(default=120, ge=30, le=240, alias="timeoutSeconds")


class ReadyResponse(BaseModel):
    status: Literal["ready"] = "ready"
    analysis_available: Literal[True] = True


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


class AnalysisError(BaseModel):
    """Safe public failure details, without raw engine exceptions."""

    model_config = ConfigDict(frozen=True, extra="forbid")

    code: Literal["OCR_FAILED", "VISUAL_FAILED"]
    message: str


class CompletedAnalysis[T](BaseModel):
    """A successful step must contain its correctly typed result."""

    model_config = ConfigDict(frozen=True, extra="forbid")

    status: Literal["COMPLETED"] = "COMPLETED"
    result: T
    error: None = None


class FailedAnalysis(BaseModel):
    """An attempted step failed; it cannot also contain a successful result."""

    model_config = ConfigDict(frozen=True, extra="forbid")

    status: Literal["FAILED"] = "FAILED"
    result: None = None
    error: AnalysisError


class SkippedAnalysis(BaseModel):
    """An unrequested step is neither a success nor a failure."""

    model_config = ConfigDict(frozen=True, extra="forbid")

    status: Literal["SKIPPED"] = "SKIPPED"
    result: None = None
    error: None = None


OcrAnalysis = Annotated[
    CompletedAnalysis[OcrResult] | FailedAnalysis | SkippedAnalysis,
    Field(discriminator="status"),
]

VisualAnalysis = Annotated[
    CompletedAnalysis[VisualResult] | FailedAnalysis | SkippedAnalysis,
    Field(discriminator="status"),
]


class ImageAnalysisResult(BaseModel):
    """Independent outcomes linked to the exact input content."""

    source_sha256: str = Field(pattern=r"^[0-9a-f]{64}$")
    ocr: OcrAnalysis
    visual: VisualAnalysis
