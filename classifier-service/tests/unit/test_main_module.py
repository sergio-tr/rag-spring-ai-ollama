"""Coverage for root ``main`` module wiring (``create_app`` exposure)."""

from __future__ import annotations


def test_main_module_defines_fastapi_app():
    import main

    assert main.app is not None
    assert main.create_app is not None
