"""Common configuration: URLs from environment variables (default values = compose standard)."""

from __future__ import annotations

import os
from collections.abc import Generator
from typing import Any

import httpx
import pytest

_COLLECTED_COUNT = 0
_PASSED_NODEIDS: set[str] = set()
_FAILED_NODEIDS: set[str] = set()
_SKIPPED_REASONS: dict[str, str] = {}
_STRICT_GUARD_FAILURE = ""


def _url(name: str, default: str) -> str:
    return os.environ.get(name, default).rstrip("/")


def _truthy_env(name: str) -> bool:
    return os.environ.get(name, "").strip().lower() in ("1", "true", "yes", "on")


def _integration_strict_enabled() -> bool:
    return _truthy_env("INTEGRATION_STRICT") or _truthy_env("INTEGRATION_FAIL_ON_UNREACHABLE")


def _integration_classifier_required() -> bool:
    return _truthy_env("INTEGRATION_REQUIRE_CLASSIFIER")


def _integration_classifier_model_required() -> bool:
    return _truthy_env("INTEGRATION_REQUIRE_CLASSIFIER_MODEL")


def _classifier_skip_requires_failure(nodeid: str, reason: str) -> bool:
    text = f"{nodeid}\n{reason}".lower()
    if "testobservabilitystack" in text or "observability tests" in text:
        return False
    if "classifier" not in text:
        return False
    model_not_loaded = "model not loaded" in text or "model != loaded" in text
    if model_not_loaded and not _integration_classifier_model_required():
        return False
    return True


def _auth_skip_requires_failure(reason: str) -> bool:
    text = reason.lower()
    return (
        "login failed" in text
        or "login did not return a token" in text
        or "seed user missing" in text
        or "wrong integration_login" in text
    )


def pytest_configure(config: pytest.Config) -> None:
    global _COLLECTED_COUNT, _STRICT_GUARD_FAILURE
    del config
    _COLLECTED_COUNT = 0
    _STRICT_GUARD_FAILURE = ""
    _PASSED_NODEIDS.clear()
    _FAILED_NODEIDS.clear()
    _SKIPPED_REASONS.clear()


def pytest_collection_finish(session: pytest.Session) -> None:
    global _COLLECTED_COUNT
    _COLLECTED_COUNT = len(session.items)


def pytest_runtest_logreport(report: pytest.TestReport) -> None:
    if report.outcome == "passed" and report.when == "call":
        _PASSED_NODEIDS.add(report.nodeid)
    elif report.outcome == "failed":
        _FAILED_NODEIDS.add(report.nodeid)
    elif report.outcome == "skipped":
        _SKIPPED_REASONS[report.nodeid] = str(report.longrepr)


def pytest_sessionfinish(session: pytest.Session, exitstatus: int) -> None:
    global _STRICT_GUARD_FAILURE
    del exitstatus
    if not _integration_strict_enabled():
        return

    collected = _COLLECTED_COUNT
    passed = _PASSED_NODEIDS
    failed = _FAILED_NODEIDS
    skipped = _SKIPPED_REASONS

    if collected <= 0:
        _STRICT_GUARD_FAILURE = "strict integration guard failed: pytest collected zero tests."
        session.exitstatus = pytest.ExitCode.TESTS_FAILED
        return

    if len(skipped) >= collected and not passed and not failed:
        _STRICT_GUARD_FAILURE = (
            f"strict integration guard failed: all collected tests were skipped ({collected}/{collected})."
        )
        session.exitstatus = pytest.ExitCode.TESTS_FAILED
        return

    if _integration_classifier_required():
        classifier_skips = {
            nodeid: reason
            for nodeid, reason in skipped.items()
            if _classifier_skip_requires_failure(nodeid, reason)
        }
        if classifier_skips:
            first_nodeid, first_reason = next(iter(classifier_skips.items()))
            _STRICT_GUARD_FAILURE = (
                "strict integration guard failed: classifier is required but a classifier-related test "
                f"was skipped ({first_nodeid}: {first_reason})."
            )
            session.exitstatus = pytest.ExitCode.TESTS_FAILED
            return

    auth_skips = {
        nodeid: reason
        for nodeid, reason in skipped.items()
        if _auth_skip_requires_failure(reason)
    }
    if auth_skips:
        first_nodeid, first_reason = next(iter(auth_skips.items()))
        _STRICT_GUARD_FAILURE = (
            "strict integration guard failed: authenticated product coverage was skipped "
            f"({first_nodeid}: {first_reason})."
        )
        session.exitstatus = pytest.ExitCode.TESTS_FAILED


def pytest_terminal_summary(terminalreporter: pytest.TerminalReporter) -> None:
    if _STRICT_GUARD_FAILURE:
        terminalreporter.section("strict integration guard")
        terminalreporter.write_line(_STRICT_GUARD_FAILURE)


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
def product_api_base() -> str:
    """Product API prefix (e.g. /api/v5), aligned with RAG_API_PRODUCT_BASE_PATH."""
    return os.environ.get("INTEGRATION_RAG_PRODUCT_BASE_PATH", "/api/v5").strip().rstrip("/") or "/api/v5"


@pytest.fixture(scope="session")
def integration_seed_credentials() -> tuple[str, str]:
    """Email/password for stack integration JWT flows (V16 seed or CI user)."""
    email = os.environ.get("INTEGRATION_LOGIN_EMAIL", "dev@local.test").strip()
    login_secret = os.environ.get("INTEGRATION_LOGIN_PASSWORD", "dev")
    return (email, login_secret)


@pytest.fixture(scope="session")
def integration_admin_credentials() -> tuple[str, str] | None:
    """
    When INTEGRATION_ADMIN_EMAIL is set, enables tests that require ROLE_ADMIN (e.g. e2e profile:
    admin@e2e.local / e2e from E2eAdminUserSeeder).
    """
    email = os.environ.get("INTEGRATION_ADMIN_EMAIL", "").strip()
    if not email:
        return None
    admin_secret = os.environ.get("INTEGRATION_ADMIN_PASSWORD", "e2e").strip()
    return (email, admin_secret)


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
def tc_postgres_container() -> Generator[object, None, None]:
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
