"""
Stack integration tests (Compose + optional observability).

Requirements:
  - postgres, classifier-service, backend reachable on configured ports.
  - For useful RAG answers, Ollama must be available; product query paths may return an error envelope if LLM is down.

Observability (Jaeger, Prometheus, OTEL collector, Grafana):
  - By default (INTEGRATION_CHECK_OBS=auto), tests in TestObservabilityStack run only if the OTEL
    collector metrics endpoint is reachable (compose.obs.yml).
  - INTEGRATION_CHECK_OBS=1: fail if the observability stack is not reachable (CI with obs).
  - INTEGRATION_CHECK_OBS=0: skip all observability tests.

API path drift (aligned with Spring `rag.api.*`):
  - INTEGRATION_RAG_PRODUCT_BASE_PATH (default `/api/v5`) for JWT product tests.
  - INTEGRATION_LOGIN_EMAIL / INTEGRATION_LOGIN_PASSWORD — seed user for `POST /api/auth/login` flows
    (default matches Flyway V16: dev@local.test / dev).
  - INTEGRATION_ADMIN_EMAIL / INTEGRATION_ADMIN_PASSWORD — optional; enables admin API **200** tests when an
    ADMIN user exists (profile e2e: admin@e2e.local / e2e via E2eAdminUserSeeder).

Usage:
  cd docker && docker compose ... up -d
  pip install -r tests/integration/requirements.txt
  pytest tests/integration -v

Lab async jobs (api-map §7.4):
  - Legacy `POST {product}/lab/evaluations/llm|rag` returns **410**; canonical runs use
    `POST {product}/lab/benchmarks/{kind}/runs` (JSON body with `datasetId`), **202** with `asyncTaskId` +
    `evaluationRunId`, then **polling** `GET {product}/lab/jobs/{asyncTaskId}` or SSE `.../events`.
  - Prefer polling in integration tests (simple assert on JSON); SSE is optional and stream-based.
"""

from __future__ import annotations

import os
import time
from collections.abc import Callable

import httpx
import pytest


def _truthy_env(name: str) -> bool:
    return os.environ.get(name, "").strip().lower() in ("1", "true", "yes", "on")


def integration_strict() -> bool:
    """
    Strict mode turns connectivity skips into hard failures.

    Enable in CI lanes to avoid false-green runs when a required service is unreachable.
    """
    return _truthy_env("INTEGRATION_STRICT") or _truthy_env("INTEGRATION_FAIL_ON_UNREACHABLE")


def integration_require_classifier() -> bool:
    """
    Require classifier reachability (connectivity) in this run.

    Note: model-loaded is intentionally NOT required by default; see INTEGRATION_REQUIRE_CLASSIFIER_MODEL.
    """
    return _truthy_env("INTEGRATION_REQUIRE_CLASSIFIER")


def integration_require_classifier_model() -> bool:
    """
    Require classifier model to be loaded for classify-path assertions.
    """
    return _truthy_env("INTEGRATION_REQUIRE_CLASSIFIER_MODEL")


def _skip_if_unreachable(exc: Exception) -> None:
    if isinstance(exc, (httpx.ConnectError, httpx.ConnectTimeout)):
        if integration_strict():
            pytest.fail(f"Service unreachable (strict mode): {exc}")
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


def _assert_requires_auth(r: httpx.Response) -> None:
    """Spring Security may answer 401 or 403 for unauthenticated /api/** (JWT filter + authorize)."""
    assert r.status_code in (401, 403), r.text


def _assert_json_response_not_html(r: httpx.Response) -> dict:
    """Fail fast if an API response looks like an HTML or nginx error page instead of JSON."""
    raw = r.text
    head = raw.lstrip()[:32].lower()
    assert not head.startswith("<!doctype"), raw[:500]
    assert not head.startswith("<html"), raw[:500]
    ct = (r.headers.get("content-type") or "").lower()
    assert "application/json" in ct, (ct, raw[:200])
    return r.json()


def _login_access_token(
    http_client: httpx.Client,
    backend_base: str,
    email: str,
    password: str,
) -> str | None:
    r = http_client.post(
        f"{backend_base}/api/auth/login",
        json={"email": email, "password": password},
        headers={"Content-Type": "application/json"},
        timeout=30.0,
    )
    if r.status_code != 200:
        return None
    try:
        data = r.json()
    except ValueError:
        return None
    token = data.get("accessToken")
    return str(token) if token else None


class TestClassifierService:
    def test_models_returns_json_list(self, http_client: httpx.Client, classifier_base: str) -> None:
        try:
            r = http_client.get(f"{classifier_base}/models")
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            if integration_require_classifier():
                pytest.fail(
                    "Classifier is required (INTEGRATION_REQUIRE_CLASSIFIER=1) but unreachable: "
                    f"{classifier_base} ({e})"
                )
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
            if integration_require_classifier():
                pytest.fail(
                    "Classifier is required (INTEGRATION_REQUIRE_CLASSIFIER=1) but unreachable: "
                    f"{classifier_base} ({e})"
                )
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 400, r.text

    def test_health_ok(self, http_client: httpx.Client, classifier_base: str) -> None:
        try:
            r = http_client.get(f"{classifier_base}/health")
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            if integration_require_classifier():
                pytest.fail(
                    "Classifier is required (INTEGRATION_REQUIRE_CLASSIFIER=1) but unreachable: "
                    f"{classifier_base} ({e})"
                )
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 200, r.text
        data = r.json()
        assert data.get("status") == "ok"

    def test_classify_returns_query_type(self, http_client: httpx.Client, classifier_base: str) -> None:
        try:
            h = http_client.get(f"{classifier_base}/health")
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            if integration_require_classifier():
                pytest.fail(
                    "Classifier is required (INTEGRATION_REQUIRE_CLASSIFIER=1) but unreachable: "
                    f"{classifier_base} ({e})"
                )
            _skip_if_unreachable(e)
            raise
        assert h.status_code == 200
        if h.json().get("model") != "loaded":
            if integration_require_classifier_model():
                pytest.fail(
                    "Classifier model is required (INTEGRATION_REQUIRE_CLASSIFIER_MODEL=1) but /health reports model != loaded."
                )
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

    def test_authenticated_product_smoke_schema_and_presets_ok(
        self,
        http_client: httpx.Client,
        backend_base: str,
        product_api_base: str,
        integration_seed_credentials: tuple[str, str],
    ) -> None:
        """
        Authenticated product smoke (replacement for legacy query smoke).

        Contract:
          - Must be authenticated (JWT).
          - Must use product API only.
          - Must be non-snapshot-dependent (no knowledge snapshot creation required).
          - Must be cheap enough for CI connectivity verification.
        """
        email, password = integration_seed_credentials
        try:
            token = _login_access_token(http_client, backend_base, email, password)
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        if not token:
            pytest.skip("USER login failed; cannot run authenticated product smoke (check INTEGRATION_LOGIN_*).")
        headers = {"Authorization": f"Bearer {token}"}
        try:
            presets = http_client.get(f"{backend_base}{product_api_base}/presets", headers=headers, timeout=30.0)
            schema = http_client.get(
                f"{backend_base}{product_api_base}/config/schema", headers=headers, timeout=30.0
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert presets.status_code == 200, presets.text
        assert isinstance(presets.json(), list)
        assert schema.status_code == 200, schema.text
        assert isinstance(schema.json(), dict)


class TestBackendAuthApi:
    """POST /api/auth/* validation and negative paths (permitAll in security chain)."""

    def test_login_wrong_password_401(
        self,
        http_client: httpx.Client,
        backend_base: str,
        integration_seed_credentials: tuple[str, str],
    ) -> None:
        email, _ = integration_seed_credentials
        try:
            r = http_client.post(
                f"{backend_base}/api/auth/login",
                json={"email": email, "password": "definitely-wrong-password-for-integration"},
                headers={"Content-Type": "application/json"},
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 401, r.text

    def test_login_unknown_user_401(self, http_client: httpx.Client, backend_base: str) -> None:
        try:
            r = http_client.post(
                f"{backend_base}/api/auth/login",
                json={"email": "no-such-user-for-integration@local.invalid", "password": "irrelevant-pass-12345"},
                headers={"Content-Type": "application/json"},
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 401, r.text

    def test_login_invalid_email_400(self, http_client: httpx.Client, backend_base: str) -> None:
        try:
            r = http_client.post(
                f"{backend_base}/api/auth/login",
                json={"email": "not-an-email", "password": "somepassword"},
                headers={"Content-Type": "application/json"},
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 400, r.text

    def test_refresh_invalid_token_401(self, http_client: httpx.Client, backend_base: str) -> None:
        try:
            r = http_client.post(
                f"{backend_base}/api/auth/refresh",
                json={"refreshToken": "invalid-token-not-a-jwt"},
                headers={"Content-Type": "application/json"},
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 401, r.text

    def test_register_duplicate_seed_email_409(
        self,
        http_client: httpx.Client,
        backend_base: str,
        integration_seed_credentials: tuple[str, str],
    ) -> None:
        email, _ = integration_seed_credentials
        try:
            r = http_client.post(
                f"{backend_base}/api/auth/register",
                json={
                    "name": "Integration Duplicate",
                    "email": email,
                    "password": "longenough1",
                },
                headers={"Content-Type": "application/json"},
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 409, r.text


class TestBackendAdminApi:
    """GET /api/admin/* requires ROLE_ADMIN (see SecurityConfiguration)."""

    def test_admin_health_unauthenticated_requires_auth(
        self,
        http_client: httpx.Client,
        backend_base: str,
    ) -> None:
        try:
            r = http_client.get(f"{backend_base}/api/admin/health")
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        _assert_requires_auth(r)

    def test_admin_health_forbidden_for_user_jwt(
        self,
        http_client: httpx.Client,
        backend_base: str,
        integration_seed_credentials: tuple[str, str],
    ) -> None:
        email, password = integration_seed_credentials
        try:
            token = _login_access_token(http_client, backend_base, email, password)
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        if not token:
            pytest.skip("USER login failed; cannot assert 403 on /api/admin (check INTEGRATION_LOGIN_*).")
        try:
            r = http_client.get(
                f"{backend_base}/api/admin/health",
                headers={"Authorization": f"Bearer {token}"},
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 403, r.text

    def test_admin_allowlist_forbidden_for_user_jwt(
        self,
        http_client: httpx.Client,
        backend_base: str,
        integration_seed_credentials: tuple[str, str],
    ) -> None:
        email, password = integration_seed_credentials
        try:
            token = _login_access_token(http_client, backend_base, email, password)
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        if not token:
            pytest.skip("USER login failed; cannot assert 403 on /api/admin (check INTEGRATION_LOGIN_*).")
        try:
            r = http_client.get(
                f"{backend_base}/api/admin/allowlist",
                headers={"Authorization": f"Bearer {token}"},
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 403, r.text

    def test_admin_health_and_allowlist_ok_for_admin_jwt(
        self,
        http_client: httpx.Client,
        backend_base: str,
        integration_admin_credentials: tuple[str, str] | None,
    ) -> None:
        if integration_admin_credentials is None:
            pytest.skip(
                "Set INTEGRATION_ADMIN_EMAIL (+ INTEGRATION_ADMIN_PASSWORD, default e2e) when the backend "
                "has an ADMIN user (e.g. SPRING_PROFILES_ACTIVE includes e2e → admin@e2e.local)."
            )
        a_email, a_password = integration_admin_credentials
        try:
            token = _login_access_token(http_client, backend_base, a_email, a_password)
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        if not token:
            pytest.fail(
                "ADMIN login failed with INTEGRATION_ADMIN_EMAIL; check password or that E2eAdminUserSeeder ran."
            )
        headers = {"Authorization": f"Bearer {token}"}
        try:
            h = http_client.get(f"{backend_base}/api/admin/health", headers=headers, timeout=30.0)
            lst = http_client.get(f"{backend_base}/api/admin/allowlist", headers=headers, timeout=30.0)
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert h.status_code == 200, h.text
        body = h.json()
        assert body.get("status") == "UP" and body.get("scope") == "admin"
        assert lst.status_code == 200, lst.text
        assert isinstance(lst.json(), list)


class TestBackendProductApi:
    """JWT-protected product prefix (same paths as RAG_API_PRODUCT_BASE_PATH)."""

    def test_presets_without_token_requires_auth(
        self,
        http_client: httpx.Client,
        backend_base: str,
        product_api_base: str,
    ) -> None:
        try:
            r = http_client.get(f"{backend_base}{product_api_base}/presets")
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        _assert_requires_auth(r)

    def test_config_schema_without_token_requires_auth(
        self,
        http_client: httpx.Client,
        backend_base: str,
        product_api_base: str,
    ) -> None:
        try:
            r = http_client.get(f"{backend_base}{product_api_base}/config/schema")
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        _assert_requires_auth(r)

    def test_login_then_presets_and_schema_ok(
        self,
        http_client: httpx.Client,
        backend_base: str,
        product_api_base: str,
        integration_seed_credentials: tuple[str, str],
    ) -> None:
        email, password = integration_seed_credentials
        try:
            token = _login_access_token(http_client, backend_base, email, password)
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        if not token:
            pytest.skip(
                "Login did not return a token (seed user missing or wrong INTEGRATION_LOGIN_*). "
                "Ensure Flyway V16 seed or set INTEGRATION_LOGIN_EMAIL / INTEGRATION_LOGIN_PASSWORD."
            )
        headers = {"Authorization": f"Bearer {token}"}
        try:
            presets = http_client.get(f"{backend_base}{product_api_base}/presets", headers=headers, timeout=30.0)
            schema = http_client.get(
                f"{backend_base}{product_api_base}/config/schema",
                headers=headers,
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert presets.status_code == 200, presets.text
        assert schema.status_code == 200, schema.text
        plist = presets.json()
        assert isinstance(plist, list)
        sbody = schema.json()
        assert isinstance(sbody, dict)
        fv = sbody.get("fields")
        assert isinstance(fv, list) and len(fv) >= 1

    def test_unknown_product_route_returns_json_404_not_html(
        self,
        http_client: httpx.Client,
        backend_base: str,
        product_api_base: str,
        integration_seed_credentials: tuple[str, str],
    ) -> None:
        email, password = integration_seed_credentials
        try:
            token = _login_access_token(http_client, backend_base, email, password)
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        if not token:
            pytest.skip("Login did not return a token (seed user missing or wrong INTEGRATION_LOGIN_*).")
        headers = {
            "Authorization": f"Bearer {token}",
            "Accept": "application/json",
        }
        try:
            r = http_client.get(
                f"{backend_base}{product_api_base}/no-such-endpoint-phase8e-contract",
                headers=headers,
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 404, r.text
        body = _assert_json_response_not_html(r)
        assert body.get("success") is False
        err = body.get("error") or {}
        assert err.get("code"), body


class TestBackendAccountExportApi:
    """Account export async contract (Phase 7 UX — backend poll/download paths)."""

    def test_export_post_returns_202_json_not_html(
        self,
        http_client: httpx.Client,
        backend_base: str,
        product_api_base: str,
        integration_seed_credentials: tuple[str, str],
    ) -> None:
        email, password = integration_seed_credentials
        try:
            token = _login_access_token(http_client, backend_base, email, password)
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        if not token:
            pytest.skip("Login did not return a token (seed user missing or wrong INTEGRATION_LOGIN_*).")
        headers = {
            "Authorization": f"Bearer {token}",
            "Accept": "application/json",
        }
        try:
            r = http_client.post(
                f"{backend_base}{product_api_base}/me/account/export",
                headers=headers,
                timeout=60.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 202, r.text
        body = _assert_json_response_not_html(r)
        job_id = body.get("jobId")
        assert job_id is not None and len(str(job_id)) > 0
        poll_path = body.get("pollPath")
        assert isinstance(poll_path, str) and "/me/account/jobs/" in poll_path


class TestBackendOpenApi:
    """springdoc OpenAPI JSON at /v3/api-docs (permitAll — no JWT)."""

    def test_v3_api_docs_lists_product_auth_and_admin_paths(
        self,
        http_client: httpx.Client,
        backend_base: str,
        product_api_base: str,
    ) -> None:
        try:
            r = http_client.get(f"{backend_base}/v3/api-docs", timeout=30.0)
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        if r.status_code == 404:
            pytest.skip("OpenAPI not exposed (springdoc disabled for this profile)")
        assert r.status_code == 200, r.text
        doc = r.json()
        assert doc.get("openapi") is not None or doc.get("swagger") is not None
        paths = doc.get("paths") or {}
        assert isinstance(paths, dict)
        # Spot-check: product, auth, admin prefixes appear in paths
        path_keys = " ".join(paths.keys())
        assert product_api_base in path_keys or "/api/v5" in path_keys
        assert "/api/auth/login" in path_keys
        assert "/api/admin/health" in path_keys


class TestBackendLabJobs:
    """
    Lab async flow: 202 + job body, then GET job status (polling).
    See module docstring and docs/adr/0003-evaluation-async-project-scope-and-dataset-dedup.md (Lab async jobs).
    """

    def test_lab_status_requires_auth(
        self,
        http_client: httpx.Client,
        backend_base: str,
        product_api_base: str,
    ) -> None:
        try:
            r = http_client.get(f"{backend_base}{product_api_base}/lab/status", timeout=30.0)
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        _assert_requires_auth(r)

    def test_lab_status_authenticated_json_contract_not_html(
        self,
        http_client: httpx.Client,
        backend_base: str,
        product_api_base: str,
        integration_seed_credentials: tuple[str, str],
    ) -> None:
        """datasets.enabled reflects reference workbook readiness (see LabController#status)."""
        email, password = integration_seed_credentials
        try:
            token = _login_access_token(http_client, backend_base, email, password)
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        if not token:
            pytest.skip("Login did not return a token (seed user missing or wrong INTEGRATION_LOGIN_*).")
        headers = {"Authorization": f"Bearer {token}", "Accept": "application/json"}
        try:
            r = http_client.get(f"{backend_base}{product_api_base}/lab/status", headers=headers, timeout=30.0)
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 200, r.text
        body = _assert_json_response_not_html(r)
        datasets = body.get("datasets") or {}
        assert isinstance(datasets.get("enabled"), bool)
        assert isinstance(datasets.get("datasetKindsReady"), bool)
        # Legacy single questionCount removed; optional tombstone for API readers.
        assert "legacyQuestionCountDeprecated" in datasets
        assert isinstance(body.get("countsByDatasetKind"), dict)
        assert body.get("evaluations") is not None
        assert body.get("classifier") is not None
        assert isinstance(body.get("message"), str)

    def test_lab_evaluations_rag_legacy_returns_410(
        self,
        http_client: httpx.Client,
        backend_base: str,
        product_api_base: str,
        integration_seed_credentials: tuple[str, str],
    ) -> None:
        """POST /lab/evaluations/rag is gone; response points at canonical benchmark path."""
        email, password = integration_seed_credentials
        try:
            token = _login_access_token(http_client, backend_base, email, password)
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        if not token:
            pytest.skip("Login did not return a token (seed user missing or wrong INTEGRATION_LOGIN_*).")
        headers = {"Authorization": f"Bearer {token}", "Accept": "application/json"}
        try:
            post = http_client.post(
                f"{backend_base}{product_api_base}/lab/evaluations/rag",
                headers=headers,
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert post.status_code == 410, post.text
        err = _assert_json_response_not_html(post)
        assert err.get("error") == "LAB_EVALUATIONS_LEGACY_REMOVED"
        assert "benchmarks" in (err.get("message") or "").lower()

    def test_lab_benchmark_rag_preset_async_returns_202_and_pollable_job(
        self,
        http_client: httpx.Client,
        backend_base: str,
        product_api_base: str,
        integration_admin_credentials: tuple[str, str] | None,
    ) -> None:
        """
        Canonical RAG lab run: typed evaluation_dataset (Flyway V42 reference UUID), ADMIN-only SYSTEM_DATASET.

        CI sets INTEGRATION_ADMIN_* with profile e2e; local runs skip without admin env.
        """
        if integration_admin_credentials is None:
            pytest.skip(
                "Needs ROLE_ADMIN + seeded reference dataset (set INTEGRATION_ADMIN_EMAIL / "
                "INTEGRATION_ADMIN_PASSWORD; e2e profile: admin@e2e.local / e2e)."
            )
        a_email, a_password = integration_admin_credentials
        try:
            token = _login_access_token(http_client, backend_base, a_email, a_password)
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        if not token:
            pytest.skip("Admin login did not return a token (check INTEGRATION_ADMIN_* / e2e seeder).")
        headers = {
            "Authorization": f"Bearer {token}",
            "Content-Type": "application/json",
            "Accept": "application/json",
        }
        # V42__seed_reference_evaluation_dataset.sql — REFERENCE_BUNDLE, SYSTEM_DATASET (ADMIN scope).
        reference_dataset_id = "00000000-0000-7000-8000-000000000001"
        try:
            post = http_client.post(
                f"{backend_base}{product_api_base}/lab/benchmarks/RAG_PRESET_END_TO_END/runs",
                headers=headers,
                json={"datasetId": reference_dataset_id},
                timeout=120.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert post.status_code == 202, post.text
        body = post.json()
        task_id = body.get("asyncTaskId")
        assert task_id is not None and len(str(task_id)) > 0
        assert body.get("evaluationRunId") is not None
        assert body.get("status") == "ACCEPTED"
        try:
            st = http_client.get(
                f"{backend_base}{product_api_base}/lab/jobs/{task_id}",
                headers={"Authorization": f"Bearer {token}", "Accept": "application/json"},
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert st.status_code == 200, st.text
        job = st.json()
        assert str(job.get("id")) == str(task_id)
        assert job.get("status") in (
            "QUEUED",
            "RUNNING",
            "SUCCEEDED",
            "FAILED",
        ), job


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

    # Intentionally no cross-service "backend query triggers classifier" assertion here:
    # product query flows can be snapshot-dependent and are out of scope for the non-snapshot smoke contract.


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
        product_api_base: str,
        integration_seed_credentials: tuple[str, str],
    ) -> None:
        """Backend Micrometer metrics should appear in Prometheus (RAG timers or HTTP server metrics)."""
        prom = obs_urls["prometheus"]
        email, password = integration_seed_credentials
        try:
            token = _login_access_token(http_client, backend_base, email, password)
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        if token:
            headers = {"Authorization": f"Bearer {token}"}
        else:
            headers = None
        try:
            # Warm up a stable product endpoint to generate HTTP metrics without snapshot dependency.
            http_client.get(f"{backend_base}{product_api_base}/config/schema", headers=headers, timeout=30.0)
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
        product_api_base: str,
        integration_seed_credentials: tuple[str, str],
    ) -> None:
        """
        Regression: Spring OTLP HTTP exporter must use /v1/traces; profiles docker+infra must point to otel-collector.
        """
        expected = os.environ.get("INTEGRATION_EXPECT_RAG_SERVICE_NAME", "rag-backend").strip() or "rag-backend"
        email, password = integration_seed_credentials
        try:
            token = _login_access_token(http_client, backend_base, email, password)
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        if not token:
            pytest.skip("USER login failed; cannot generate authenticated traffic for Jaeger (check INTEGRATION_LOGIN_*).")
        headers = {"Authorization": f"Bearer {token}"}
        try:
            r = http_client.get(f"{backend_base}{product_api_base}/config/schema", headers=headers, timeout=30.0)
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
            f"Last services seen: {last_services!r}. Check rag-service OTLP (SPRING_PROFILES_ACTIVE=docker,infra), "
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
