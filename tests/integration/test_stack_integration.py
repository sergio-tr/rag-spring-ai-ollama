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

Classifier (CI split):
  - PR job ``integration``: Spring only — classifier tests **skip** when :8000 is down (INTEGRATION_STRICT still applies to backend/auth).
  - Job ``integration_classifier_required``: sets INTEGRATION_REQUIRE_CLASSIFIER=1 and starts uvicorn — classifier connectivity failures **fail** the run.

API path drift (aligned with Spring `rag.api.*`):
  - INTEGRATION_RAG_PRODUCT_BASE_PATH (default `/api/v5`) for JWT product tests.
  - INTEGRATION_LOGIN_EMAIL / INTEGRATION_LOGIN_PASSWORD — seed user for `POST {product}/auth/login` flows
    (default matches Flyway V16: dev@local.test / dev).
  - INTEGRATION_ADMIN_EMAIL / INTEGRATION_ADMIN_PASSWORD — optional; enables admin API **200** tests when an
    ADMIN user exists (profile e2e: admin@e2e.local / e2e via E2eAdminUserSeeder).

Usage:
  cd docker && docker compose ... up -d
  pip install -r tests/integration/requirements.txt
  pytest tests/integration -v

Lab async jobs (api-map §7.4):
  - Canonical runs use `POST {product}/lab/benchmarks/{kind}/runs` (JSON body with `datasetId`), **202** with `asyncTaskId` +
    `evaluationRunId`, then **polling** `GET {product}/lab/jobs/{asyncTaskId}` or SSE `.../events`.
  - Prefer polling in integration tests (simple assert on JSON); SSE is optional and stream-based.
"""

from __future__ import annotations

import os
import time
from collections.abc import Callable
from pathlib import Path

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


def _skip_if_unreachable(exc: Exception, *, service: str = "backend") -> None:
    """
    Map connectivity errors to skip (optional stack) or fail (strict CI).

    Classifier tests skip when the service is down unless INTEGRATION_REQUIRE_CLASSIFIER=1
    (integration_classifier_required job). Backend/product tests still honor INTEGRATION_STRICT.
    """
    if not isinstance(exc, (httpx.ConnectError, httpx.ConnectTimeout)):
        return
    if service == "classifier":
        if integration_require_classifier():
            pytest.fail(
                "Classifier is required (INTEGRATION_REQUIRE_CLASSIFIER=1) but unreachable: "
                f"{exc}"
            )
        pytest.skip(f"classifier-service unreachable: {exc}")
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
    product_api_base = os.environ.get("INTEGRATION_RAG_PRODUCT_BASE_PATH", "/api/v5").rstrip("/") or "/api/v5"
    r = http_client.post(
        f"{backend_base}{product_api_base}/auth/login",
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


# Plain-text acta bytes (must be extractable; empty PDFs fail ingestion). Shipped under rag-service test resources.
_LAB_CORPUS_FIXTURE_PATH = (
    Path(__file__).resolve().parents[2]
    / "rag-service"
    / "src"
    / "test"
    / "resources"
    / "docs"
    / "bootstrap-acta.txt"
)
_LAB_CORPUS_FIXTURE_BYTES = """\
ACTA — 24 de febrero de 2025

Fecha: 24 de febrero de 2025.
Presidente: Juan Pérez García.
Secretaria: Laura Martínez.

Orden del día:
1) Aprobación del acta anterior.
2) Presupuesto.
3) Mantenimiento del ascensor.

Acuerdos:
- Se aprueba el presupuesto anual.
""".encode("utf-8")


def _lab_corpus_fixture_bytes() -> bytes:
    if _LAB_CORPUS_FIXTURE_PATH.is_file():
        return _LAB_CORPUS_FIXTURE_PATH.read_bytes()
    return _LAB_CORPUS_FIXTURE_BYTES


def _lab_auth_headers(token: str, *, json_body: bool = True) -> dict[str, str]:
    headers = {"Authorization": f"Bearer {token}", "Accept": "application/json"}
    if json_body:
        headers["Content-Type"] = "application/json"
    return headers


def _lab_create_evaluation_corpus_with_document(
    http_client: httpx.Client,
    backend_base: str,
    product_api_base: str,
    token: str,
    *,
    corpus_name: str,
) -> str:
    """
    Create a Lab evaluation corpus and attach one document (required for RAG_PRESET_END_TO_END runs).
    """
    headers = _lab_auth_headers(token)
    create = http_client.post(
        f"{backend_base}{product_api_base}/lab/evaluation-corpora",
        headers=headers,
        json={"name": corpus_name},
        timeout=60.0,
    )
    assert create.status_code in (200, 201), create.text
    corpus_body = _assert_json_response_not_html(create)
    corpus_id = corpus_body.get("id")
    assert corpus_id, corpus_body

    upload_headers = _lab_auth_headers(token, json_body=False)
    upload = http_client.post(
        f"{backend_base}{product_api_base}/lab/evaluation-corpora/{corpus_id}/documents",
        headers=upload_headers,
        files={"file": ("bootstrap-acta.txt", _lab_corpus_fixture_bytes(), "text/plain")},
        timeout=120.0,
    )
    assert upload.status_code == 200, upload.text
    upload_body = _assert_json_response_not_html(upload)
    corpus_body = upload_body.get("corpus") if isinstance(upload_body.get("corpus"), dict) else upload_body
    assert (corpus_body.get("documentCount") or 0) >= 1, upload_body
    _lab_wait_evaluation_corpus_ready(
        http_client, backend_base, product_api_base, token, str(corpus_id)
    )
    return str(corpus_id)


def _lab_wait_evaluation_corpus_ready(
    http_client: httpx.Client,
    backend_base: str,
    product_api_base: str,
    token: str,
    corpus_id: str,
    *,
    deadline_s: float = 90.0,
    interval_s: float = 2.0,
) -> None:
    """Poll GET evaluation corpus until at least one document is READY (RAG_PRESET_END_TO_END gate)."""
    headers = _lab_auth_headers(token)
    url = f"{backend_base}{product_api_base}/lab/evaluation-corpora/{corpus_id}"
    last_body: dict | None = None

    def ready() -> bool:
        nonlocal last_body
        try:
            r = http_client.get(url, headers=headers, timeout=30.0)
        except (httpx.ConnectError, httpx.ConnectTimeout):
            return False
        if r.status_code != 200:
            return False
        last_body = _assert_json_response_not_html(r)
        ready_count = last_body.get("readyCount") or 0
        if ready_count >= 1:
            docs = last_body.get("documents") or []
            if any(
                isinstance(d, dict)
                and d.get("status") == "READY"
                and d.get("storagePresent") is True
                for d in docs
            ):
                return True
            # readyCount can disagree with per-document status when persistence context is stale
        if (last_body.get("failedCount") or 0) >= 1:
            pytest.fail(f"evaluation corpus document processing failed: {last_body}")
        return False

    if not _poll_until(ready, deadline_s, interval_s):
        pytest.fail(
            f"evaluation corpus {corpus_id} has no READY documents after {deadline_s}s: {last_body}"
        )


class TestClassifierService:
    def test_models_returns_json_list(self, http_client: httpx.Client, classifier_base: str) -> None:
        try:
            r = http_client.get(f"{classifier_base}/models")
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e, service="classifier")
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
            _skip_if_unreachable(e, service="classifier")
            raise
        assert r.status_code == 400, r.text

    def test_health_ok(self, http_client: httpx.Client, classifier_base: str) -> None:
        try:
            r = http_client.get(f"{classifier_base}/health")
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e, service="classifier")
            raise
        assert r.status_code == 200, r.text
        data = r.json()
        assert data.get("status") == "ok"

    def test_classify_returns_query_type(self, http_client: httpx.Client, classifier_base: str) -> None:
        try:
            h = http_client.get(f"{classifier_base}/health")
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e, service="classifier")
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
            _skip_if_unreachable(e, service="classifier")
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
        Authenticated product smoke (replacement for removed query smoke).

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
    """POST product auth validation and negative paths (permitAll in security chain)."""

    def test_login_wrong_password_401(
        self,
        http_client: httpx.Client,
        backend_base: str,
        integration_seed_credentials: tuple[str, str],
        product_api_base: str,
    ) -> None:
        email, _ = integration_seed_credentials
        try:
            r = http_client.post(
                f"{backend_base}{product_api_base}/auth/login",
                json={"email": email, "password": "definitely-wrong-password-for-integration"},
                headers={"Content-Type": "application/json"},
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 401, r.text

    def test_login_unknown_user_401(
        self, http_client: httpx.Client, backend_base: str, product_api_base: str
    ) -> None:
        try:
            r = http_client.post(
                f"{backend_base}{product_api_base}/auth/login",
                json={"email": "no-such-user-for-integration@local.invalid", "password": "irrelevant-pass-12345"},
                headers={"Content-Type": "application/json"},
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 401, r.text

    def test_login_invalid_email_400(
        self, http_client: httpx.Client, backend_base: str, product_api_base: str
    ) -> None:
        try:
            r = http_client.post(
                f"{backend_base}{product_api_base}/auth/login",
                json={"email": "not-an-email", "password": "somepassword"},
                headers={"Content-Type": "application/json"},
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 400, r.text

    def test_refresh_invalid_token_401(
        self, http_client: httpx.Client, backend_base: str, product_api_base: str
    ) -> None:
        try:
            r = http_client.post(
                f"{backend_base}{product_api_base}/auth/refresh",
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
        product_api_base: str,
    ) -> None:
        email, _ = integration_seed_credentials
        try:
            r = http_client.post(
                f"{backend_base}{product_api_base}/auth/register",
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


def _unique_integration_email() -> str:
    stamp = int(time.time() * 1000)
    return f"m2-auth-{stamp}@integration.local"


def _assert_email_confirmation_enabled(register_response: httpx.Response) -> None:
    if register_response.status_code == 200:
        try:
            body = register_response.json()
        except ValueError:
            body = {}
        if body.get("status") == "REGISTERED":
            pytest.fail(
                "Email confirmation must be enabled on the integration backend "
                "(RAG_AUTH_EMAIL_CONFIRMATION_ENABLED=true, e2e profile). "
                f"Got: {register_response.status_code} {register_response.text[:300]}"
            )


def _admin_access_token(http_client: httpx.Client, backend_base: str, product_api_base: str) -> str:
    admin_email = os.environ.get("INTEGRATION_ADMIN_EMAIL", "admin@e2e.local").strip()
    admin_password = os.environ.get("INTEGRATION_ADMIN_PASSWORD", "e2e").strip()
    token = _login_access_token(http_client, backend_base, admin_email, admin_password)
    if not token:
        pytest.fail(
            f"Admin login failed for mail-outbox inspection ({admin_email}). "
            "Ensure e2e profile and E2eAdminUserSeeder are active."
        )
    return token


def _extract_confirm_token_from_outbox(
    http_client: httpx.Client,
    backend_base: str,
    product_api_base: str,
    admin_token: str,
    recipient_email: str,
) -> str:
    import re

    r = http_client.get(
        f"{backend_base}{product_api_base}/admin/mail-outbox",
        params={"limit": 20},
        headers={"Authorization": f"Bearer {admin_token}", "Accept": "application/json"},
        timeout=30.0,
    )
    assert r.status_code == 200, r.text
    entries = r.json()
    assert isinstance(entries, list), entries
    normalized = recipient_email.strip().lower()
    for entry in entries:
        if not isinstance(entry, dict):
            continue
        if entry.get("purpose") != "EMAIL_CONFIRMATION":
            continue
        if str(entry.get("recipient", "")).strip().lower() != normalized:
            continue
        body_text = str(entry.get("bodyText", ""))
        match = re.search(r"confirm-email\?token=([^&\s\"']+)", body_text)
        if match:
            from urllib.parse import unquote

            return unquote(match.group(1))
    pytest.fail(f"No EMAIL_CONFIRMATION outbox row with token link for {recipient_email}")


class TestBackendAuthEmailConfirmation:
    """M2: register → pending → login blocked → confirm → login OK (requires e2e auth flags)."""

    def test_register_returns_pending_status(
        self, http_client: httpx.Client, backend_base: str, product_api_base: str
    ) -> None:
        email = _unique_integration_email()
        try:
            r = http_client.post(
                f"{backend_base}{product_api_base}/auth/register",
                json={
                    "name": "M2 Pending",
                    "email": email,
                    "password": "Password123!",
                    "locale": "en",
                    "acceptedPrivacyPolicy": True,
                    "acceptedTerms": True,
                },
                headers={"Content-Type": "application/json"},
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        _assert_email_confirmation_enabled(r)
        assert r.status_code == 202, r.text
        body = r.json()
        assert body.get("status") == "PENDING_EMAIL_VERIFICATION"
        assert body.get("login") is None

    def test_login_unverified_returns_403_email_not_verified(
        self, http_client: httpx.Client, backend_base: str, product_api_base: str
    ) -> None:
        email = _unique_integration_email()
        password = "Password123!"
        try:
            reg = http_client.post(
                f"{backend_base}{product_api_base}/auth/register",
                json={
                    "name": "M2 Blocked Login",
                    "email": email,
                    "password": password,
                    "locale": "en",
                    "acceptedPrivacyPolicy": True,
                    "acceptedTerms": True,
                },
                headers={"Content-Type": "application/json"},
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        _assert_email_confirmation_enabled(reg)
        login = http_client.post(
            f"{backend_base}{product_api_base}/auth/login",
            json={"email": email, "password": password},
            headers={"Content-Type": "application/json"},
            timeout=30.0,
        )
        assert login.status_code == 403, login.text
        try:
            payload = login.json()
        except ValueError:
            payload = {}
        assert payload.get("code") == "EMAIL_NOT_VERIFIED"

    def test_confirm_email_via_outbox_then_login_ok(
        self, http_client: httpx.Client, backend_base: str, product_api_base: str
    ) -> None:
        email = _unique_integration_email()
        password = "Password123!"
        try:
            reg = http_client.post(
                f"{backend_base}{product_api_base}/auth/register",
                json={
                    "name": "M2 Confirm Cycle",
                    "email": email,
                    "password": password,
                    "locale": "en",
                    "acceptedPrivacyPolicy": True,
                    "acceptedTerms": True,
                },
                headers={"Content-Type": "application/json"},
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        _assert_email_confirmation_enabled(reg)
        admin_token = _admin_access_token(http_client, backend_base, product_api_base)
        raw_token = _extract_confirm_token_from_outbox(
            http_client, backend_base, product_api_base, admin_token, email
        )
        confirm = http_client.post(
            f"{backend_base}{product_api_base}/auth/confirm-email",
            json={"token": raw_token},
            headers={"Content-Type": "application/json"},
            timeout=30.0,
        )
        assert confirm.status_code == 200, confirm.text
        token = _login_access_token(http_client, backend_base, email, password)
        assert token, "login must return accessToken after email confirmation"


class TestBackendAdminApi:
    """GET product admin routes requires ROLE_ADMIN (see SecurityConfiguration)."""

    def test_admin_health_unauthenticated_requires_auth(
        self,
        http_client: httpx.Client,
        backend_base: str,
        product_api_base: str,
    ) -> None:
        try:
            r = http_client.get(f"{backend_base}{product_api_base}/admin/health")
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        _assert_requires_auth(r)

    def test_admin_health_forbidden_for_user_jwt(
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
            pytest.skip("USER login failed; cannot assert 403 on product admin routes (check INTEGRATION_LOGIN_*).")
        try:
            r = http_client.get(
                f"{backend_base}{product_api_base}/admin/health",
                headers={"Authorization": f"Bearer {token}"},
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 403, r.text

    def test_admin_models_forbidden_for_user_jwt(
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
            pytest.skip("USER login failed; cannot assert 403 on product admin routes (check INTEGRATION_LOGIN_*).")
        try:
            r = http_client.get(
                f"{backend_base}{product_api_base}/admin/models",
                headers={"Authorization": f"Bearer {token}"},
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 403, r.text

    def test_admin_health_and_models_ok_for_admin_jwt(
        self,
        http_client: httpx.Client,
        backend_base: str,
        product_api_base: str,
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
            h = http_client.get(f"{backend_base}{product_api_base}/admin/health", headers=headers, timeout=30.0)
            lst = http_client.get(f"{backend_base}{product_api_base}/admin/models", headers=headers, timeout=30.0)
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
        # Spot-check: product auth and admin prefixes appear in paths.
        path_keys = " ".join(paths.keys())
        assert product_api_base in path_keys or "/api/v5" in path_keys
        assert f"{product_api_base}/auth/login" in path_keys
        assert f"{product_api_base}/admin/health" in path_keys


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
        assert isinstance(body.get("countsByDatasetKind"), dict)
        assert body.get("evaluations") is not None
        assert body.get("classifier") is not None
        assert isinstance(body.get("message"), str)

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
        headers = _lab_auth_headers(token)
        # V42__seed_reference_evaluation_dataset.sql — REFERENCE_BUNDLE, SYSTEM_DATASET (ADMIN scope).
        reference_dataset_id = "00000000-0000-7000-8000-000000000001"
        try:
            corpus_id = _lab_create_evaluation_corpus_with_document(
                http_client,
                backend_base,
                product_api_base,
                token,
                corpus_name="pytest-rag-preset-corpus",
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        try:
            post = http_client.post(
                f"{backend_base}{product_api_base}/lab/benchmarks/RAG_PRESET_END_TO_END/runs",
                headers=headers,
                json={
                    "datasetId": reference_dataset_id,
                    "corpusId": corpus_id,
                    "experimentalPresetCodes": ["P0"],
                },
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

    def test_lab_evaluation_corpus_create_and_rag_without_project_id(
        self,
        http_client: httpx.Client,
        backend_base: str,
        product_api_base: str,
        integration_admin_credentials: tuple[str, str] | None,
    ) -> None:
        """LAB closure: evaluation corpus is independent of projectId on benchmark start."""
        if integration_admin_credentials is None:
            pytest.skip("Needs INTEGRATION_ADMIN_* (e2e profile).")
        a_email, a_password = integration_admin_credentials
        token = _login_access_token(http_client, backend_base, a_email, a_password)
        if not token:
            pytest.skip("Admin login did not return a token.")
        headers = _lab_auth_headers(token)
        try:
            corpus_id = _lab_create_evaluation_corpus_with_document(
                http_client,
                backend_base,
                product_api_base,
                token,
                corpus_name="pytest-closure-corpus",
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise

        reference_dataset_id = "00000000-0000-7000-8000-000000000001"
        try:
            post = http_client.post(
                f"{backend_base}{product_api_base}/lab/benchmarks/RAG_PRESET_END_TO_END/runs",
                headers=headers,
                json={
                    "datasetId": reference_dataset_id,
                    "corpusId": corpus_id,
                    "experimentalPresetCodes": ["P0"],
                },
                timeout=120.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        if post.status_code == 422:
            pytest.skip(f"Dataset/corpus gate rejected run (expected in minimal corpus): {post.text[:300]}")
        assert post.status_code == 202, post.text
        body = post.json()
        assert body.get("asyncTaskId")
        assert body.get("evaluationRunId")

    def test_lab_jobs_active_list_authenticated(
        self,
        http_client: httpx.Client,
        backend_base: str,
        product_api_base: str,
        integration_seed_credentials: tuple[str, str],
    ) -> None:
        email, password = integration_seed_credentials
        token = _login_access_token(http_client, backend_base, email, password)
        if not token:
            pytest.skip("User login did not return a token.")
        headers = {"Authorization": f"Bearer {token}", "Accept": "application/json"}
        try:
            r = http_client.get(
                f"{backend_base}{product_api_base}/lab/jobs/active",
                headers=headers,
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 200, r.text
        body = r.json()
        assert isinstance(body, list)

    def test_lab_evaluation_corpus_readiness_no_documents(
        self,
        http_client: httpx.Client,
        backend_base: str,
        product_api_base: str,
        integration_seed_credentials: tuple[str, str],
    ) -> None:
        """M3: empty corpus exposes primaryBlocker NO_DOCUMENTS and runnable=false."""
        email, password = integration_seed_credentials
        try:
            token = _login_access_token(http_client, backend_base, email, password)
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        if not token:
            pytest.skip("Login did not return a token.")
        headers = _lab_auth_headers(token)
        try:
            create = http_client.post(
                f"{backend_base}{product_api_base}/lab/evaluation-corpora",
                headers=headers,
                json={"name": "pytest-readiness-empty"},
                timeout=60.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert create.status_code in (200, 201), create.text
        corpus_id = _assert_json_response_not_html(create).get("id")
        assert corpus_id

        try:
            readiness = http_client.get(
                f"{backend_base}{product_api_base}/lab/evaluation-corpora/{corpus_id}/readiness",
                headers=headers,
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert readiness.status_code == 200, readiness.text
        body = _assert_json_response_not_html(readiness)
        assert body.get("primaryBlocker") == "NO_DOCUMENTS"
        assert body.get("runnable") is False

    def test_lab_evaluation_corpus_readiness_runnable_after_upload(
        self,
        http_client: httpx.Client,
        backend_base: str,
        product_api_base: str,
        integration_seed_credentials: tuple[str, str],
    ) -> None:
        """M3: corpus with READY documents is runnable (snapshot may still need reindex)."""
        email, password = integration_seed_credentials
        try:
            token = _login_access_token(http_client, backend_base, email, password)
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        if not token:
            pytest.skip("Login did not return a token.")
        try:
            corpus_id = _lab_create_evaluation_corpus_with_document(
                http_client,
                backend_base,
                product_api_base,
                token,
                corpus_name="pytest-readiness-ready",
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        headers = _lab_auth_headers(token)
        try:
            readiness = http_client.get(
                f"{backend_base}{product_api_base}/lab/evaluation-corpora/{corpus_id}/readiness",
                headers=headers,
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert readiness.status_code == 200, readiness.text
        body = _assert_json_response_not_html(readiness)
        assert body.get("runnable") is True
        assert body.get("primaryBlocker") in (None, "")
        # Document-centric flow: missing index is informational, not a run blocker.
        snapshot_blocker = body.get("snapshotBlocker")
        if body.get("activeSnapshotId") in (None, ""):
            assert snapshot_blocker in (
                "INDEX_PREPARATION_REQUIRED",
                "REINDEX_REQUIRED",
                "NO_ACTIVE_SNAPSHOT",
                None,
            ), body

    def test_lab_evaluation_corpus_missing_returns_kb_not_found_code(
        self,
        http_client: httpx.Client,
        backend_base: str,
        product_api_base: str,
        integration_seed_credentials: tuple[str, str],
    ) -> None:
        """M3: stale corpus id returns machine code KB_NOT_FOUND, not generic NOT_FOUND."""
        email, password = integration_seed_credentials
        try:
            token = _login_access_token(http_client, backend_base, email, password)
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        if not token:
            pytest.skip("Login did not return a token.")
        missing_id = "00000000-0000-7000-8000-000000009999"
        headers = _lab_auth_headers(token)
        try:
            r = http_client.get(
                f"{backend_base}{product_api_base}/lab/evaluation-corpora/{missing_id}",
                headers=headers,
                timeout=30.0,
            )
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e)
            raise
        assert r.status_code == 404, r.text
        body = _assert_json_response_not_html(r)
        assert body.get("code") == "KB_NOT_FOUND"


class TestCrossService:
    def test_classifier_then_backend_query(self, http_client: httpx.Client, classifier_base: str, backend_base: str) -> None:
        """Smoke: both services reachable in sequence (no strict queryType equality)."""
        try:
            h = http_client.get(f"{classifier_base}/health")
        except (httpx.ConnectError, httpx.ConnectTimeout) as e:
            _skip_if_unreachable(e, service="classifier")
            raise
        try:
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
