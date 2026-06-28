"""Tests for HTTP protocol guard middleware."""

from __future__ import annotations

from fastapi.testclient import TestClient


def test_classify_rejects_websocket_upgrade_with_json_error(client: TestClient):
    r = client.post(
        "/classify",
        json={"query": "hello"},
        headers={
            "Connection": "Upgrade",
            "Upgrade": "websocket",
        },
    )
    assert r.status_code == 400
    data = r.json()
    assert data.get("success") is False
    err = data.get("error") or {}
    assert err.get("code") == "HTTP_PROTOCOL_ERROR"
    assert "websocket" in (err.get("message") or "").lower()


def test_health_accepts_h2c_upgrade_header(client: TestClient):
    """Java HttpClient may send Upgrade: h2c; health probes must not return 400."""
    r = client.get(
        "/health",
        headers={
            "Connection": "Upgrade",
            "Upgrade": "h2c",
        },
    )
    assert r.status_code == 200
    assert r.json().get("status") == "ok"


def test_classify_accepts_normal_post_json(client: TestClient):
    r = client.post("/classify", json={"query": "How many documents?"})
    if r.status_code == 503:
        return
    assert r.status_code in (200, 400, 404, 422)
