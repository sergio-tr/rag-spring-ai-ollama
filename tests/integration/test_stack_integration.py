"""
Stack integration tests (Compose + optional observability).

Requirements:
  - postgres, classifier-service, backend reachable on configured ports.
  - For useful RAG answers, Ollama must be available; without it /api/v4/query may still return 200
    with an error message in the body.

Usage:
  cd docker && docker compose ... up -d
  pip install -r tests/integration/requirements.txt
  pytest tests/integration -v
"""

from __future__ import annotations

import os

import httpx
import pytest


def _skip_if_unreachable(exc: Exception) -> None:
    if isinstance(exc, (httpx.ConnectError, httpx.ConnectTimeout)):
        pytest.skip(f"Service unreachable: {exc}")


class TestClassifierService:
    def test_models_returns_json_list(self, http_client: httpx.Client, classifier_base: str) -> None:
        try:
            r = http_client.get(f"{classifier_base}/models")
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 200, r.text
        data = r.json()
        assert isinstance(data, list)
        assert len(data) >= 1
        assert "id" in data[0]

    def test_classify_empty_query_returns_400(self, http_client: httpx.Client, classifier_base: str) -> None:
        try:
            r = http_client.post(
                f"{classifier_base}/classify",
                json={"query": ""},
                headers={"Content-Type": "application/json"},
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 400, r.text

    def test_health_ok(self, http_client: httpx.Client, classifier_base: str) -> None:
        try:
            r = http_client.get(f"{classifier_base}/health")
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 200, r.text
        data = r.json()
        assert data.get("status") == "ok"

    def test_classify_returns_query_type(self, http_client: httpx.Client, classifier_base: str) -> None:
        try:
            h = http_client.get(f"{classifier_base}/health")
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert h.status_code == 200
        if h.json().get("model") != "loaded":
            pytest.skip(
                "Keras model not loaded in classifier-service (/health → model != loaded). "
                "Check MODEL_PATH and files under models/."
            )
        try:
            r = http_client.post(
                f"{classifier_base}/classify",
                json={"query": "¿Cuántas actas hay?"},
                headers={"Content-Type": "application/json"},
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 200, r.text
        data = r.json()
        assert "queryType" in data, data


class TestBackend:
    def test_actuator_info_available(self, http_client: httpx.Client, backend_base: str) -> None:
        try:
            r = http_client.get(f"{backend_base}/actuator/info")
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        if r.status_code == 404:
            pytest.skip("actuator/info not exposed (management.endpoints)")
        assert r.status_code == 200, r.text
        assert r.headers.get("content-type", "").startswith("application/json")

    def test_actuator_health_up(self, http_client: httpx.Client, backend_base: str) -> None:
        try:
            r = http_client.get(f"{backend_base}/actuator/health")
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 200, r.text
        data = r.json()
        assert data.get("status") == "UP", data

    def test_query_returns_200(self, http_client: httpx.Client, backend_base: str) -> None:
        """Query endpoint: 200 even if body reflects missing LLM/data."""
        try:
            r = http_client.get(f"{backend_base}/api/v4/query", params={"question": "test"})
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 200, r.text
        body = (r.text or "").strip()
        assert len(body) > 0

    def test_query_response_is_plain_text(self, http_client: httpx.Client, backend_base: str) -> None:
        try:
            r = http_client.get(f"{backend_base}/api/v4/query", params={"question": "How many documents are there?"})
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 200, r.text
        assert "application/json" not in (r.headers.get("content-type") or "").lower()


class TestCrossService:
    def test_classifier_then_backend_query(self, http_client: httpx.Client, classifier_base: str, backend_base: str) -> None:
        """Smoke: both services reachable in sequence (no strict queryType equality)."""
        try:
            h = http_client.get(f"{classifier_base}/health")
            b = http_client.get(f"{backend_base}/actuator/health")
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert h.status_code == 200 and b.status_code == 200


class TestObservabilityStack:
    """Only when compose.obs.yml is up and INTEGRATION_CHECK_OBS=1."""

    @pytest.fixture(autouse=True)
    def _require_flag(self) -> None:
        if os.environ.get("INTEGRATION_CHECK_OBS", "").lower() not in ("1", "true", "yes"):
            pytest.skip("Set INTEGRATION_CHECK_OBS=1 to validate Jaeger/Prometheus/Grafana/OTEL")

    def test_prometheus_health(self, http_client: httpx.Client, obs_urls: dict[str, str]) -> None:
        r = http_client.get(f"{obs_urls['prometheus']}/-/healthy")
        assert r.status_code == 200

    def test_grafana_health(self, http_client: httpx.Client, obs_urls: dict[str, str]) -> None:
        r = http_client.get(f"{obs_urls['grafana']}/api/health")
        assert r.status_code == 200

    def test_jaeger_ui_reachable(self, http_client: httpx.Client, obs_urls: dict[str, str]) -> None:
        r = http_client.get(f"{obs_urls['jaeger']}/")
        assert r.status_code == 200

    def test_otel_collector_metrics(self, http_client: httpx.Client, obs_urls: dict[str, str]) -> None:
        r = http_client.get(obs_urls["otel_metrics"])
        assert r.status_code == 200
        assert "otelcol" in r.text.lower() or "#" in r.text

    def test_prometheus_has_http_metrics(self, http_client: httpx.Client, obs_urls: dict[str, str]) -> None:
        r = http_client.get(f"{obs_urls['prometheus']}/api/v1/query", params={"query": "up"})
        assert r.status_code == 200
        body = r.json()
        assert body.get("status") == "success"
