"""Authenticated bytes-in / JSON-out analysis; no source paths or database."""

import asyncio
import logging
from secrets import compare_digest
from typing import Annotated

from fastapi import APIRouter, Depends, HTTPException, Query, Request
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from PIL import Image
from pydantic import ValidationError
from starlette.requests import ClientDisconnect

from screensort_analysis.api.schemas import (
    AnalysisOptions,
    ImageAnalysisResult,
    ReadyResponse,
)
from screensort_analysis.image.preparation import MAX_IMAGE_BYTES

logger = logging.getLogger(__name__)
bearer = HTTPBearer(auto_error=False)
UPLOAD_TIMEOUT_SECONDS = 15
# "Busy" is temporary, so it is 503 with a hint when to come back. 429 would mean the
# client itself sends too many requests, which is not what happens here.
BUSY_RETRY_AFTER_SECONDS = 2


async def require_token(
    request: Request,
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)],
) -> None:
    token = request.app.state.analysis_token
    if credentials is None or not compare_digest(
        credentials.credentials.encode("utf-8"), token.encode("ascii")
    ):
        raise HTTPException(401, "UNAUTHORIZED", headers={"WWW-Authenticate": "Bearer"})


router = APIRouter(dependencies=[Depends(require_token)])


@router.post("/_internal/shutdown", include_in_schema=False)
async def shutdown(request: Request):
    server = getattr(request.app.state, "server", None)
    if server is None:
        raise HTTPException(503, "NOT_DESKTOP_MANAGED")
    server.should_exit = True
    return {"status": "stopping"}


async def read_image(request: Request) -> bytes:
    media = request.headers.get("content-type", "").split(";")[0].strip().lower()
    if media not in {"image/png", "image/jpeg"}:
        raise HTTPException(415, "UNSUPPORTED_MEDIA_TYPE")
    if (
        request.headers.get("content-encoding", "identity").strip().lower()
        != "identity"
    ):
        raise HTTPException(415, "UNSUPPORTED_CONTENT_ENCODING")
    declared = request.headers.get("content-length")
    if declared is not None:
        try:
            size = int(declared)
        except ValueError:
            raise HTTPException(400, "INVALID_CONTENT_LENGTH") from None
        if size < 0:
            raise HTTPException(400, "INVALID_CONTENT_LENGTH")
        if size > MAX_IMAGE_BYTES:
            raise HTTPException(413, "IMAGE_TOO_LARGE")
    data = bytearray()
    try:
        async with asyncio.timeout(UPLOAD_TIMEOUT_SECONDS):
            async for chunk in request.stream():
                if len(chunk) > MAX_IMAGE_BYTES - len(data):
                    raise HTTPException(413, "IMAGE_TOO_LARGE")
                data.extend(chunk)
    except TimeoutError:
        raise HTTPException(408, "UPLOAD_TIMEOUT") from None
    except ClientDisconnect:
        raise HTTPException(400, "UPLOAD_INTERRUPTED") from None
    if not data:
        raise HTTPException(422, "EMPTY_IMAGE")
    return bytes(data)


def observe_completion(future: asyncio.Future) -> None:
    if not future.cancelled():
        future.exception()


@router.get("/ready", response_model=ReadyResponse)
async def ready(request: Request) -> ReadyResponse:
    if not request.app.state.analysis_runtime.is_ready:
        raise HTTPException(503, "ANALYSIS_UNAVAILABLE")
    return ReadyResponse()


@router.post(
    "/api/v1/analyses",
    response_model=ImageAnalysisResult,
    openapi_extra={
        "requestBody": {
            "required": True,
            "content": {
                media: {"schema": {"type": "string", "format": "binary"}}
                for media in ("image/png", "image/jpeg")
            },
        }
    },
)
async def analyze(
    request: Request, options: Annotated[AnalysisOptions, Query()]
) -> ImageAnalysisResult:
    if not options.ocr and not options.visual:
        raise HTTPException(422, "SELECT_ANALYSIS_STEP")
    runtime = request.app.state.analysis_runtime
    if not runtime.is_ready:
        raise HTTPException(503, "ANALYSIS_UNAVAILABLE")
    if not runtime.reserve():
        raise HTTPException(
            503,
            "ANALYSIS_BUSY",
            headers={"Retry-After": str(BUSY_RETRY_AFTER_SECONDS)},
        )
    submitted = False
    try:
        data = await read_image(request)
        future = runtime.submit_reserved(data, options)
        submitted = True
        pending = asyncio.wrap_future(future)
        pending.add_done_callback(observe_completion)
        async with asyncio.timeout(options.timeout_seconds):
            return await asyncio.shield(pending)
    except HTTPException:
        raise
    except TimeoutError:
        raise HTTPException(504, "ANALYSIS_TIMEOUT") from None
    except ValidationError:
        logger.error("INVALID_ENGINE_RESULT")
        raise HTTPException(500, "ANALYSIS_FAILED") from None
    except MemoryError:
        raise HTTPException(503, "ANALYSIS_UNAVAILABLE") from None
    except (ValueError, OSError, Image.DecompressionBombError):
        raise HTTPException(422, "INVALID_IMAGE") from None
    except Exception as exc:
        logger.error("ANALYSIS_FAILED [%s]", type(exc).__name__)
        raise HTTPException(500, "ANALYSIS_FAILED") from None
    finally:
        if not submitted:
            runtime.release()
