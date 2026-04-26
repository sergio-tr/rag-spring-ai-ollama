"""Smoke tests for the FastAPI factory and error handlers."""

from __future__ import annotations

from fastapi import HTTPException, FastAPI
from fastapi.testclient import TestClient

from app.main import create_app


def test_create_app_registers_title_and_router():
    app = create_app()
    assert "Classifier" in app.title
    paths = [getattr(r, "path", "") for r in app.routes]
    assert any("/health" in str(p) for p in paths)


def test_http_exception_handler_normalizes_error_shapes():
    app = create_app()

    @app.get("/_boom0")
    def _boom0():
        raise HTTPException(status_code=400, detail={"code": "X", "message": "direct"})

    @app.get("/_boom1")
    def _boom1():
        raise HTTPException(status_code=400, detail={"error": {"code": "X", "message": "m"}})

    @app.get("/_boom2")
    def _boom2():
        raise HTTPException(status_code=400, detail={"detail": {"code": "X", "message": "m2"}})

    @app.get("/_boom3")
    def _boom3():
        raise HTTPException(status_code=400, detail="plain")

    c = TestClient(app)
    r0 = c.get("/_boom0")
    assert r0.status_code == 400
    assert r0.json()["error"]["message"] == "direct"

    r1 = c.get("/_boom1")
    assert r1.status_code == 400
    assert r1.json()["success"] is False
    assert r1.json()["error"]["message"] == "m"

    r2 = c.get("/_boom2")
    assert r2.status_code == 400
    assert r2.json()["error"]["message"] == "m2"

    r3 = c.get("/_boom3")
    assert r3.status_code == 400
    assert r3.json()["error"]["message"] == "plain"


def test_validation_exception_handler_returns_envelope():
    app = create_app()

    @app.get("/_need-int")
    def _need_int(x: int):
        return {"x": x}

    c = TestClient(app)
    r = c.get("/_need-int?x=nope")
    assert r.status_code == 422
    body = r.json()
    assert body["success"] is False
    assert body["error"]["code"] == "VALIDATION_ERROR"
