"""
Stack integration tests (Compose + optional observability).

Requirements:
  - postgres, classifier-service, backend reachable on configured ports.
  - For useful RAG answers, Ollama must be available; without it /api/v4/query may still return 200
    with an error message in the body.

Observability (Jaeger, Prometheus, OTEL collector, Grafana):
  - By default (INTEGRATION_CHECK_OBS=auto), tests in TestObservabilityStack run only if the OTEL
    collector metrics endpoint is reachable (compose.obs.yml).
  - INTEGRATION_CHECK_OBS=1: fail if the observability stack is not reachable (CI with obs).
  - INTEGRATION_CHECK_OBS=0: skip all observability tests.

Usage:
  cd docker && docker compose ... up -d
  pip install -r tests/integration/requirements.txt
  pytest tests/integration -v
"""

from __future__ import annotations

import os
import time
from collections.abc import Callable

import httpx
import pytest


def _skip_if_unreachable(exc: Exception) -> None:
    if isinstance(exc, (httpx.ConnectError, httpx.ConnectTimeout)):
        pytest.skip(f"Service unreachable: {exc}")


def _jaeger_service_names(http_client: httpx.Client, jaeger_base: str) -> list[str]:
    """Best-effort parse of Jaeger 1.x / 2.x services list API."""
    jaeger_base = jaeger_base.rstrip("/")
    for path in ("/api/v3/services", "/api/services"):
        try:
            r = http_client.get(f"{jaeger_base}{path}", timeout=15.0)
            if r.status_code != 200:
                continue
            data = r.json()
        except (httpx.HTTPError, ValueError):
            continue
        if isinstance(data, dict):
            if isinstance(data.get("services"), list):
                return [str(s) for s in data["services"]]
            if isinstance(data.get("data"), list):
                return [str(s) for s in data["data"]]
    return []


def _prometheus_query_result(http_client: httpx.Client, prometheus_base: str, query: str) -> object | None:
    """Return Prometheus instant query 'data.result' or None on error."""
    try:
        r = http_client.get(
            f"{prometheus_base.rstrip('/')}/api/v1/query",
            params={"query": query},
            timeout=15.0,
        )
        if r.status_code != 200:
            return None
        body = r.json()
        if body.get("status") != "success":
            return None
        data = body.get("data") or {}
        return data.get("result")
    except (httpx.HTTPError, ValueError):
        return None


def _poll_until(
    fn: Callable[[], bool],
    deadline_s: float,
    interval_s: float = 2.0,
) -> bool:
    deadline = time.time() + deadline_s
    while time.time() < deadline:
        if fn():
            return True
        time.sleep(interval_s)
    return False


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

    def test_actuator_prometheus_exposes_metrics(self, http_client: httpx.Client, backend_base: str) -> None:
        """Backend must expose Micrometer/Prometheus scrape target (used by Prometheus job 'backend')."""
        try:
            r = http_client.get(f"{backend_base}/actuator/prometheus")
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 200, r.text
        text = r.text.lower()
        assert "http_server_requests_seconds" in text or "process_cpu_usage" in text or "jvm" in text, (
            "Expected typical Micrometer/JVM metrics on /actuator/prometheus"
        )

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

    def test_query_response_is_json(self, http_client: httpx.Client, backend_base: str) -> None:
        """v4 query returns JSON (success/data or error envelope)."""
        try:
            r = http_client.get(f"{backend_base}/api/v4/query", params={"question": "How many documents are there?"})
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 200, r.text
        ct = (r.headers.get("content-type") or "").lower()
        assert "application/json" in ct, f"expected JSON content-type, got {ct!r}"


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

    def test_backend_query_triggers_internal_classifier_path(self, http_client: httpx.Client, backend_base: str) -> None:
        """
        RAG query should exercise backend → classifier-service HTTP (classifier reachable from backend network).
        Does not assert answer quality; only HTTP 200 from the API.
        """
        try:
            r = http_client.get(
                f"{backend_base}/api/v4/query",
                params={"question": "integration cross-service ping"},
                timeout=120.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 200, r.text


@pytest.mark.usefixtures("require_obs_stack")
class TestObservabilityStack:
    """
    Validates OTEL collector, Jaeger, Prometheus, Grafana, and trace export from Java + Python services.

    Runs when the OTEL collector metrics endpoint is reachable (INTEGRATION_CHECK_OBS=auto),
    unless INTEGRATION_CHECK_OBS=0. Use INTEGRATION_CHECK_OBS=1 in CI to require this stack.
    """

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

    def test_otlp_http_receiver_reachable(self, http_client: httpx.Client, obs_urls: dict[str, str]) -> None:
        """
        OTLP/HTTP listener on 4318: expect non-refusal (404/405 on GET is normal; connection refused is not).
        """
        base = obs_urls["otlp_http"].rstrip("/")
        try:
            r = http_client.get(f"{base}/", timeout=5.0)
        except httpx.ConnectError as e:
            pytest.fail(f"OTLP HTTP port not accepting connections at {base}: {e}")
        assert r.status_code < 600

    def test_prometheus_has_http_metrics(self, http_client: httpx.Client, obs_urls: dict[str, str]) -> None:
        r = http_client.get(f"{obs_urls['prometheus']}/api/v1/query", params={"query": "up"})
        assert r.status_code == 200
        body = r.json()
        assert body.get("status") == "success"

    def test_prometheus_scrapes_backend_job(
        self,
        http_client: httpx.Client,
        obs_urls: dict[str, str],
        backend_base: str,
    ) -> None:
        """
        After the backend is up, Prometheus job 'backend' should eventually scrape /actuator/prometheus.
        """
        prom = obs_urls["prometheus"]

        def backend_job_up() -> bool:
            res = _prometheus_query_result(http_client, prom, 'up{job="backend"}')
            if not res:
                return False
            for item in res:
                val = item.get("value")
                if isinstance(val, list) and len(val) >= 2 and str(val[1]) == "1":
                    return True
            return False

        # Warm the backend metrics endpoint once
        try:
            http_client.get(f"{backend_base}/actuator/prometheus", timeout=10.0)
        except httpx.HTTPError:
            pass

        ok = _poll_until(backend_job_up, deadline_s=90.0, interval_s=3.0)
        if not ok:
            res = _prometheus_query_result(http_client, prom, 'up{job="backend"}')
            pytest.fail(
                'Prometheus did not report up{job="backend"}==1 within timeout. '
                f"Last result: {res!r}. Check prometheus.yml targets and backend network."
            )

    def test_prometheus_has_rag_or_http_series(
        self,
        http_client: httpx.Client,
        obs_urls: dict[str, str],
        backend_base: str,
    ) -> None:
        """Backend Micrometer metrics should appear in Prometheus (RAG timers or HTTP server metrics)."""
        prom = obs_urls["prometheus"]
        try:
            http_client.get(
                f"{backend_base}/api/v4/query",
                params={"question": "prometheus metrics warm-up"},
                timeout=90.0,
            )
        except httpx.HTTPError:
            pass

        def has_series() -> bool:
            for q in (
                "rag_query_generate_seconds_count",
                'http_server_requests_seconds_count{job="backend"}',
                'http_server_requests_seconds_count',
            ):
                res = _prometheus_query_result(http_client, prom, q)
                if res:
                    return True
            return False

        ok = _poll_until(has_series, deadline_s=60.0, interval_s=3.0)
        assert ok, (
            "Expected rag_query_generate_seconds_count or http_server_requests_seconds_count in Prometheus "
            "after traffic. Trigger a query and check Grafana/Prometheus config."
        )

    def test_jaeger_receives_rag_backend_traces_after_query(
        self,
        http_client: httpx.Client,
        obs_urls: dict[str, str],
        backend_base: str,
    ) -> None:
        """
        Regression: Spring OTLP HTTP exporter must use /v1/traces; profiles docker+obs must point to otel-collector.
        """
        expected = os.environ.get("INTEGRATION_EXPECT_RAG_SERVICE_NAME", "rag-backend").strip() or "rag-backend"
        try:
            r = http_client.get(f"{backend_base}/api/v4/query", params={"question": "otel trace smoke"}, timeout=120.0)
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 200, r.text

        jaeger = obs_urls["jaeger"]
        deadline = time.time() + 90.0
        last_services: list[str] = []
        while time.time() < deadline:
            last_services = _jaeger_service_names(http_client, jaeger)
            if expected in last_services:
                return
            time.sleep(2.0)

        pytest.fail(
            f"Jaeger never listed service {expected!r} after backend query. "
            f"Last services seen: {last_services!r}. Check rag-service OTLP (SPRING_PROFILES_ACTIVE=docker,obs), "
            f"OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318, and paths /v1/traces."
        )

    def test_jaeger_lists_classifier_service_after_traffic(
        self,
        http_client: httpx.Client,
        obs_urls: dict[str, str],
        classifier_base: str,
    ) -> None:
        """Python classifier exports OTLP with OTEL_SERVICE_NAME=classifier-service."""
        expected = (
            os.environ.get("INTEGRATION_EXPECT_CLASSIFIER_SERVICE_NAME", "classifier-service").strip()
            or "classifier-service"
        )
        try:
            h = http_client.get(f"{classifier_base}/health")
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        if h.status_code != 200 or h.json().get("model") != "loaded":
            pytest.skip("Classifier model not loaded; cannot generate classifier traces")

        http_client.post(
            f"{classifier_base}/classify",
            json={"query": "integration jaeger classifier"},
            headers={"Content-Type": "application/json"},
            timeout=30.0,
        )

        jaeger = obs_urls["jaeger"]
        deadline = time.time() + 60.0
        last_services: list[str] = []
        while time.time() < deadline:
            last_services = _jaeger_service_names(http_client, jaeger)
            if expected in last_services:
                return
            time.sleep(2.0)

        pytest.fail(
            f"Jaeger never listed service {expected!r} after classify. Last: {last_services!r}. "
            "Check classifier-service OTEL env in compose.obs.yml."
        )
