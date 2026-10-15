"""Check the service boundary without models, source images or PostgreSQL."""

import pytest
from fastapi.testclient import TestClient

from screensort_analysis.main import create_app

TOKEN = "test-only-token-" + "a" * 32


class NoModelsRuntime:
    is_ready = False

    async def start(self):
        pass

    async def close(self):
        pass


@pytest.fixture
def client():
    with TestClient(
        create_app(runtime_factory=NoModelsRuntime, token=TOKEN)
    ) as test_client:
        yield test_client


def test_health_reports_process_liveness_without_claiming_analysis(client):
    response = client.get("/health")

    assert response.status_code == 200
    assert response.json() == {
        "service": "screensort-analysis",
        "status": "ok",
        "analysis_available": False,
    }
    assert response.headers["cache-control"] == "no-store"


def test_health_does_not_accept_write_requests(client):
    assert client.post("/health").status_code == 405


def test_openapi_exposes_the_health_response_contract(client):
    specification = client.get("/openapi.json").json()

    assert set(specification["paths"]) == {"/health", "/ready", "/api/v1/analyses"}
    response_schema = specification["paths"]["/health"]["get"]["responses"]["200"]
    assert response_schema["content"]["application/json"]["schema"] == {
        "$ref": "#/components/schemas/HealthResponse"
    }
    required_fields = specification["components"]["schemas"]["HealthResponse"][
        "required"
    ]
    assert set(required_fields) == {
        "service",
        "status",
        "analysis_available",
    }


def test_analysis_is_not_an_unauthenticated_success_endpoint(client):
    assert client.post("/api/v1/analyses").status_code == 401


def test_application_factory_creates_separate_instances():
    assert create_app(token=TOKEN) is not create_app(token=TOKEN)
