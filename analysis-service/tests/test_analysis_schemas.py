"""The result contract must reject contradictory status, result and error data."""

import pytest
from pydantic import ValidationError

from screensort_analysis.api.schemas import (
    AnalysisError,
    CompletedAnalysis,
    FailedAnalysis,
    ImageAnalysisResult,
    OcrResult,
    SkippedAnalysis,
    VisualResult,
)


@pytest.fixture
def step_results():
    return {
        "ocr": OcrResult(
            text="Test text", engine="test-only", engine_version="0", duration_ms=1
        ),
        "visual": VisualResult(
            description="Test description; no real model was run.",
            model="test-only",
            model_revision="test-only",
            task="caption",
            device="cpu",
            duration_ms=2,
        ),
    }


@pytest.mark.parametrize("ocr_status", ["COMPLETED", "FAILED", "SKIPPED"])
@pytest.mark.parametrize("visual_status", ["COMPLETED", "FAILED", "SKIPPED"])
def test_independent_outcomes_survive_json_roundtrip(
    step_results, ocr_status, visual_status
):
    parts = {}
    for name, status in (("ocr", ocr_status), ("visual", visual_status)):
        if status == "COMPLETED":
            parts[name] = {"status": status, "result": step_results[name].model_dump()}
        elif status == "FAILED":
            parts[name] = {
                "status": status,
                "error": {
                    "code": "OCR_FAILED" if name == "ocr" else "VISUAL_FAILED",
                    "message": "Analysis failed.",
                },
            }
        else:
            parts[name] = {"status": status}

    result = ImageAnalysisResult(source_sha256="a" * 64, **parts)

    assert result.ocr.status == ocr_status
    assert result.visual.status == visual_status
    for name, status in (("ocr", ocr_status), ("visual", visual_status)):
        part = getattr(result, name)
        assert (part.result is not None) == (status == "COMPLETED")
        assert (part.error is not None) == (status == "FAILED")
        if status == "COMPLETED":
            assert type(part.result) is type(step_results[name])
    assert ImageAnalysisResult.model_validate_json(result.model_dump_json()) == result


@pytest.mark.parametrize("part", ["ocr", "visual"])
@pytest.mark.parametrize(
    "invalid_case",
    [
        "completed_without_result",
        "completed_with_error",
        "failed_without_error",
        "failed_with_result",
        "skipped_with_result",
        "skipped_with_error",
        "unknown_status",
        "unknown_field",
        "unknown_error_code",
    ],
)
def test_invalid_outcomes_are_rejected(step_results, part, invalid_case):
    success = step_results[part].model_dump()
    error = {"code": "OCR_FAILED", "message": "Analysis failed."}
    payloads = {
        "completed_without_result": {"status": "COMPLETED", "result": None},
        "completed_with_error": {
            "status": "COMPLETED",
            "result": success,
            "error": error,
        },
        "failed_without_error": {"status": "FAILED", "error": None},
        "failed_with_result": {"status": "FAILED", "result": success, "error": error},
        "skipped_with_result": {"status": "SKIPPED", "result": success},
        "skipped_with_error": {"status": "SKIPPED", "error": error},
        "unknown_status": {"status": "RUNNING"},
        "unknown_field": {"status": "SKIPPED", "reslut": None},
        "unknown_error_code": {
            "status": "FAILED",
            "error": {"code": "OTHER", "message": "Failure"},
        },
    }
    parts = {"ocr": SkippedAnalysis(), "visual": SkippedAnalysis()}
    parts[part] = payloads[invalid_case]

    with pytest.raises(ValidationError):
        ImageAnalysisResult(source_sha256="a" * 64, **parts)


@pytest.mark.parametrize("part", ["ocr", "visual"])
def test_one_engine_cannot_return_the_other_engines_result(step_results, part):
    other = "visual" if part == "ocr" else "ocr"
    parts = {"ocr": SkippedAnalysis(), "visual": SkippedAnalysis()}
    parts[part] = {
        "status": "COMPLETED",
        "result": step_results[other].model_dump(),
    }

    with pytest.raises(ValidationError):
        ImageAnalysisResult(source_sha256="a" * 64, **parts)


def test_outcome_wrappers_cannot_be_reassigned(step_results):
    error = AnalysisError(code="OCR_FAILED", message="Text recognition failed.")
    models_and_fields = [
        (CompletedAnalysis[OcrResult](result=step_results["ocr"]), "status", "FAILED"),
        (FailedAnalysis(error=error), "result", step_results["ocr"]),
        (SkippedAnalysis(), "error", error),
        (error, "code", "VISUAL_FAILED"),
    ]

    for model, field, value in models_and_fields:
        with pytest.raises(ValidationError, match="frozen"):
            setattr(model, field, value)


def test_json_schema_discriminates_each_step_by_status():
    properties = ImageAnalysisResult.model_json_schema()["properties"]

    for name in ("ocr", "visual"):
        discriminator = properties[name]["discriminator"]
        assert discriminator["propertyName"] == "status"
        assert set(discriminator["mapping"]) == {"COMPLETED", "FAILED", "SKIPPED"}
