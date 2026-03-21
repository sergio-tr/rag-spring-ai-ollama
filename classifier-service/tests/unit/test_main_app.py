"""Smoke tests for the FastAPI factory."""

from __future__ import annotations

from app.main import create_app


def test_create_app_registers_title_and_router():
    app = create_app()
    assert "Classifier" in app.title
    paths = [getattr(r, "path", "") for r in app.routes]
    assert any("/health" in str(p) for p in paths)
