"""Common configuration: URLs from environment variables (default values = compose standard)."""

import os

import httpx
import pytest


def _url(name: str, default: str) -> str:
    return os.environ.get(name, default).rstrip("/")


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
    }
