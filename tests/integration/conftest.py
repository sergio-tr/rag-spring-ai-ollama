"""Common configuration: URLs from environment variables (default values = compose standard)."""

from __future__ import annotations

import os
from typing import Any

import httpx
import pytest


def _url(name: str, default: str) -> str:
    return os.environ.get(name, default).rstrip("/")


def _truthy_env(name: str) -> bool:
    return os.environ.get(name, "").strip().lower() in ("1", "true", "yes", "on")


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
def legacy_api_base() -> str:
    """Legacy HTTP prefix only (e.g. /api/v4), aligned with RAG_API_LEGACY_BASE_PATH."""
    # Keep integration tests aligned with the current frontend/api prefix to avoid drift (e.g. v4 → v5).
    default_prefix = os.environ.get("NEXT_PUBLIC_RAG_API_PREFIX", "/api/v5").strip().rstrip("/") or "/api/v5"
    return os.environ.get("INTEGRATION_RAG_LEGACY_BASE_PATH", default_prefix).strip().rstrip("/") or default_prefix


@pytest.fixture(scope="session")
def product_api_base() -> str:
    """Product API prefix (e.g. /api/v5), aligned with RAG_API_PRODUCT_BASE_PATH."""
    return os.environ.get("INTEGRATION_RAG_PRODUCT_BASE_PATH", "/api/v5").strip().rstrip("/") or "/api/v5"


@pytest.fixture(scope="session")
def integration_seed_credentials() -> tuple[str, str]:
    """Email/password for stack integration JWT flows (V16 seed or CI user)."""
    email = os.environ.get("INTEGRATION_LOGIN_EMAIL", "dev@local.test").strip()
    password = os.environ.get("INTEGRATION_LOGIN_PASSWORD", "dev")
    return (email, password)


@pytest.fixture(scope="session")
def integration_admin_credentials() -> tuple[str, str] | None:
    """
    When INTEGRATION_ADMIN_EMAIL is set, enables tests that require ROLE_ADMIN (e.g. e2e profile:
    admin@e2e.local / e2e from E2eAdminUserSeeder).
    """
    email = os.environ.get("INTEGRATION_ADMIN_EMAIL", "").strip()
    if not email:
        return None
    password = os.environ.get("INTEGRATION_ADMIN_PASSWORD", "e2e").strip()
    return (email, password)


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


@pytest.fixture(scope="session")
def tc_postgres_container() -> object:
    """
    Optional local Postgres (Testcontainers) with init SQL aligned with Java Testcontainers.

    Enable with INTEGRATION_USE_TESTCONTAINERS=1 (Docker required). CI keeps this unset and uses
    the GitHub Actions Postgres service for Spring; pytest there stays HTTP-only.

    Set INTEGRATION_TC_PRINT_JDBC=1 to print SPRING_DATASOURCE_URL for pointing a local Spring
    process at the same database (Path B advanced).
    """
    if not _truthy_env("INTEGRATION_USE_TESTCONTAINERS"):
        pytest.skip("INTEGRATION_USE_TESTCONTAINERS is not set (Testcontainers Postgres disabled)")
    import tc_postgres

    container = tc_postgres.start_pgvector_container()
    if _truthy_env("INTEGRATION_TC_PRINT_JDBC"):
        jdbc = tc_postgres.jdbc_url_from_container(container)
        print(f"\nINTEGRATION_TC_PRINT_JDBC: export SPRING_DATASOURCE_URL={jdbc!r}\n")
    try:
        yield container
    finally:
        container.stop()
