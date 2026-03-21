"""Common configuration: URLs from environment variables (default values = compose standard)."""

from __future__ import annotations

import os
from typing import Any

import httpx
import pytest


def _url(name: str, default: str) -> str:
    return os.environ.get(name, default).rstrip("/")


def observability_reachable(http_client: httpx.Client, obs_urls: dict[str, str]) -> bool:
    """True if the OTEL collector Prometheus self-metrics endpoint responds (compose.obs.yml up)."""
    try:
        r = http_client.get(obs_urls["otel_metrics"], timeout=5.0)
        return r.status_code == 200
    except (httpx.HTTPError, OSError):
        return False


def integration_obs_mode() -> str:
    """
    INTEGRATION_CHECK_OBS:
      - auto (default): run observability tests only if the stack is reachable.
      - 0 / false / skip: never run observability tests.
      - 1 / true / require: observability tests must run; fail if the stack is not reachable.
    """
    v = os.environ.get("INTEGRATION_CHECK_OBS", "auto").strip().lower()
    if v in ("", "auto"):
        return "auto"
    if v in ("0", "false", "no", "skip", "off"):
        return "off"
    if v in ("1", "true", "yes", "require", "on"):
        return "require"
    return "auto"


@pytest.fixture(scope="session")
def classifier_base() -> str:
    return _url("INTEGRATION_CLASSIFIER_URL", "http://127.0.0.1:8000")


@pytest.fixture(scope="session")
def backend_base() -> str:
    return _url("INTEGRATION_BACKEND_URL", "http://127.0.0.1:9000")


@pytest.fixture(scope="session")
def http_client() -> httpx.Client:
    return httpx.Client(timeout=httpx.Timeout(120.0, connect=5.0))


@pytest.fixture(scope="session")
def obs_urls() -> dict[str, str]:
    return {
        "prometheus": _url("INTEGRATION_PROMETHEUS_URL", "http://127.0.0.1:9090"),
        "grafana": _url("INTEGRATION_GRAFANA_URL", "http://127.0.0.1:3000"),
        "jaeger": _url("INTEGRATION_JAEGER_URL", "http://127.0.0.1:16686"),
        "otel_metrics": _url("INTEGRATION_OTEL_METRICS_URL", "http://127.0.0.1:8889/metrics"),
        "otlp_http": _url("INTEGRATION_OTLP_HTTP_URL", "http://127.0.0.1:4318"),
    }


@pytest.fixture(scope="session")
def obs_context(
    http_client: httpx.Client,
    obs_urls: dict[str, str],
) -> dict[str, Any]:
    """
    Session info for observability tests: whether they should run and how failures behave.
    """
    mode = integration_obs_mode()
    reachable = observability_reachable(http_client, obs_urls)
    return {
        "mode": mode,
        "reachable": reachable,
        "urls": obs_urls,
    }


@pytest.fixture(scope="class")
def require_obs_stack(obs_context: dict[str, Any], obs_urls: dict[str, str]) -> None:
    """Use on observability test classes: applies INTEGRATION_CHECK_OBS policy."""
    mode = obs_context["mode"]
    reachable = obs_context["reachable"]
    if mode == "off":
        pytest.skip("Observability tests disabled (INTEGRATION_CHECK_OBS=0)")
    if not reachable:
        if mode == "require":
            pytest.fail(
                "Observability stack required (INTEGRATION_CHECK_OBS=1) but unreachable. "
                f"Check OTEL metrics at {obs_urls['otel_metrics']} and start compose with compose.obs.yml."
            )
        pytest.skip(
            "Observability stack not reachable (OTEL collector metrics). "
            "Start: docker compose -f docker-compose.yml -f compose.obs.yml ... up -d"
        )
