"""Smoke tests for the FastAPI factory and error handlers."""

from __future__ import annotations

from fastapi import HTTPException, FastAPI
from fastapi.testclient import TestClient

from app.main import create_app


def _registered_route_paths(app: FastAPI) -> list[str]:
    """Collect route paths across FastAPI router layouts (incl. 0.137+ _IncludedRouter)."""
    paths: list[str] = []
    for route in app.routes:
        path = getattr(route, "path", None)
        if path:
            paths.append(str(path))
        contexts = getattr(route, "effective_route_contexts", None)
        if callable(contexts):
            for ctx in contexts():
                ctx_path = getattr(ctx, "path", None)
                if ctx_path:
                    paths.append(str(ctx_path))
        for child in getattr(route, "routes", None) or ():
            child_path = getattr(child, "path", None)
            if child_path:
                paths.append(str(child_path))
    return paths


def test_create_app_registers_title_and_router():
    app = create_app()
    assert "Classifier" in app.title
    paths = _registered_route_paths(app)
    assert any(p == "/health" or p.endswith("/health") for p in paths)


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


def test_http_exception_handler_maps_unknown_dict_detail():
    app = create_app()

    @app.get("/_boom4")
    def _boom4():
        raise HTTPException(status_code=400, detail={"unexpected": True})

    c = TestClient(app)
    r = c.get("/_boom4")
    assert r.status_code == 400
    body = r.json()
    assert body["success"] is False
    assert "unexpected" in body["error"]["message"]


def test_lifespan_runs_with_test_client_context_manager():
    """Startup hook loads default model (or logs warning); exercise lifespan branches."""
    with TestClient(create_app()) as client:
        r = client.get("/health")
        assert r.status_code == 200
