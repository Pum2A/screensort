"""A lightweight health probe with no database, model or filesystem access."""

from fastapi import APIRouter, Response

from screensort_analysis.api.schemas import HealthResponse

router = APIRouter(tags=["health"])


@router.get("/health", response_model=HealthResponse)
async def health(response: Response) -> HealthResponse:
    """Report that this HTTP process can answer a request, not model readiness."""
    response.headers["Cache-Control"] = "no-store"
    return HealthResponse(
        service="screensort-analysis",
        status="ok",
        analysis_available=False,
    )
