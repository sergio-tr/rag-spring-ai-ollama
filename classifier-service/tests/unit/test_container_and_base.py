"""Smoke tests for ServiceContainer and base service helpers."""

from __future__ import annotations

from unittest.mock import MagicMock

from app.base import BaseService, TracedService
from app.container import ServiceContainer


def test_service_container_exposes_services():
    c = ServiceContainer()
    assert c.config is not None
    assert c.classification_service is not None
    assert c.model_registry_service is not None
    assert c.training_service is not None
    assert c.evaluation_service is not None
    assert c.loader is not None


def test_base_service_logger():
    class S(BaseService):
        pass

    s = S()
    assert s.logger.name.endswith("S")


def test_traced_service_run_traced_delegates():
    class TS(TracedService):
        def go(self) -> int:
            return self.run_traced("x", lambda: 7)

    assert TS().go() == 7
