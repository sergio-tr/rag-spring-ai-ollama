"""Coverage for root uvicorn entry wiring (``create_app`` exposure)."""

from __future__ import annotations


def test_uvicorn_entry_defines_fastapi_app():
    import uvicorn_entry

    assert uvicorn_entry.app is not None
    assert uvicorn_entry.create_app is not None
