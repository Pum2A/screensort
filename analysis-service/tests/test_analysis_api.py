"""Exercise actual HTTP routes with generated images and explicit fake models."""

import asyncio
from contextlib import closing
from io import BytesIO
from threading import Event
from types import SimpleNamespace
from unittest.mock import Mock

import pytest
from fastapi.testclient import TestClient
from PIL import Image

from screensort_analysis.api import analysis as api
from screensort_analysis.api.schemas import (
    AnalysisOptions,
    CompletedAnalysis,
    ImageAnalysisResult,
    OcrResult,
    SkippedAnalysis,
    VisualResult,
)
from screensort_analysis.image.analyzer import ImageAnalyzer
from screensort_analysis.image.runtime import AnalysisRuntime
from screensort_analysis.main import create_app

TOKEN = "test-only-token-" + "a" * 32
HEADERS = {"Authorization": f"Bearer {TOKEN}", "Content-Type": "image/png"}


@pytest.fixture
def engines():
    return (
        SimpleNamespace(
            recognize_prepared=Mock(
                return_value=OcrResult(
                    text="Test", engine="test-only", engine_version="0", duration_ms=1
                )
            )
        ),
        SimpleNamespace(
            analyze=Mock(
                return_value=VisualResult(
                    description="Test image",
                    model="test-only",
                    model_revision="0",
                    task="caption",
                    device="cpu",
                    duration_ms=1,
                )
            )
        ),
    )


@pytest.fixture
def client(engines):
    analyzer = ImageAnalyzer(*engines)
    with TestClient(
        create_app(
            runtime_factory=lambda: AnalysisRuntime(lambda: analyzer), token=TOKEN
        )
    ) as client:
        yield client


@pytest.fixture
def image_bytes():
    with closing(Image.new("RGB", (4, 3), "white")) as image:
        with BytesIO() as output:
            image.save(output, format="PNG")
            return output.getvalue()


def test_returns_actual_pipeline_shape_and_no_store(client, image_bytes):
    response = client.post("/api/v1/analyses", headers=HEADERS, content=image_bytes)
    assert response.status_code == 200
    result = ImageAnalysisResult.model_validate(response.json())
    assert result.ocr.status == result.visual.status == "COMPLETED"
    assert result.ocr.result.text == "Test"
    assert response.headers["cache-control"] == "no-store"


def test_readiness_is_protected_and_reflects_initialized_runtime(client):
    assert client.get("/ready").status_code == 401
    assert client.get("/ready", headers=HEADERS).json() == {
        "status": "ready",
        "analysis_available": True,
    }
    assert client.get("/health").json()["analysis_available"] is True


@pytest.mark.parametrize("query", ["", "?ocr=invalid", "?source_path=secret"])
def test_authentication_stops_before_body_read(client, monkeypatch, query):
    read = Mock(side_effect=AssertionError("Body must not be read"))
    monkeypatch.setattr(api, "read_image", read)
    response = client.post("/api/v1/analyses" + query, content=b"private")
    assert response.status_code == 401
    read.assert_not_called()


@pytest.mark.parametrize("query", ["ocr=false&visual=false", "source_path=secret"])
def test_rejects_invalid_options_before_read(client, monkeypatch, query):
    read = Mock(side_effect=AssertionError("Body must not be read"))
    monkeypatch.setattr(api, "read_image", read)
    response = client.post("/api/v1/analyses?" + query, headers=HEADERS)
    assert response.status_code == 422
    assert "secret" not in response.text
    read.assert_not_called()


def test_skips_unrequested_step(client, engines, image_bytes):
    response = client.post(
        "/api/v1/analyses?visual=false", headers=HEADERS, content=image_bytes
    )
    assert response.json()["visual"]["status"] == "SKIPPED"
    engines[1].analyze.assert_not_called()


def test_preserves_ocr_on_visual_failure(client, engines, image_bytes):
    engines[1].analyze.side_effect = RuntimeError("Private details")
    response = client.post("/api/v1/analyses", headers=HEADERS, content=image_bytes)
    assert response.status_code == 200
    assert response.json()["ocr"]["status"] == "COMPLETED"
    assert response.json()["visual"]["status"] == "FAILED"
    assert "Private details" not in response.text


@pytest.mark.parametrize("data", [b"", b"not an image"])
def test_rejects_invalid_image_without_model_calls(client, engines, data):
    assert (
        client.post("/api/v1/analyses", headers=HEADERS, content=data).status_code
        == 422
    )
    engines[0].recognize_prepared.assert_not_called()
    engines[1].analyze.assert_not_called()


def test_rejects_unsupported_content_type(client):
    assert (
        client.post(
            "/api/v1/analyses",
            headers={**HEADERS, "Content-Type": "application/json"},
            content=b"{}",
        ).status_code
        == 415
    )


def test_enforces_actual_bytes_without_content_length(client, monkeypatch, engines):
    monkeypatch.setattr(api, "MAX_IMAGE_BYTES", 4)
    response = client.post(
        "/api/v1/analyses", headers=HEADERS, content=iter([b"12", b"345"])
    )
    assert response.status_code == 413
    engines[0].recognize_prepared.assert_not_called()


def test_cancelled_wait_keeps_worker_reserved_until_completion():
    async def run():
        started, finish = Event(), Event()
        result = ImageAnalysisResult(
            source_sha256="a" * 64,
            ocr=CompletedAnalysis[OcrResult](
                result=OcrResult(
                    text="", engine="test-only", engine_version="0", duration_ms=1
                )
            ),
            visual=SkippedAnalysis(),
        )

        def analyze(*args, **kwargs):
            started.set()
            assert finish.wait(timeout=5)
            return result

        runtime = AnalysisRuntime(lambda: SimpleNamespace(analyze=analyze))
        await runtime.start()
        try:
            assert runtime.reserve()
            future = runtime.submit_reserved(b"x", AnalysisOptions())
            pending = asyncio.wrap_future(future)
            pending.add_done_callback(api.observe_completion)

            async def wait():
                return await asyncio.shield(pending)

            waiter = asyncio.create_task(wait())
            assert await asyncio.to_thread(started.wait, 2)
            waiter.cancel()
            with pytest.raises(asyncio.CancelledError):
                await waiter
            assert not runtime.reserve()
            finish.set()
            assert await pending == result
            assert runtime.reserve()
            runtime.release()
        finally:
            finish.set()
            await runtime.close()

    asyncio.run(run())


def test_production_does_not_accept_missing_or_weak_token(monkeypatch):
    monkeypatch.delenv("SCREENSORT_ANALYSIS_TOKEN", raising=False)
    with pytest.raises(RuntimeError, match="random"):
        create_app()
    with pytest.raises(RuntimeError, match="random"):
        create_app(token="password")


def test_shutdown_is_protected_and_requires_the_owned_launcher(client):
    assert client.post("/_internal/shutdown").status_code == 401
    assert client.post("/_internal/shutdown", headers=HEADERS).status_code == 503
