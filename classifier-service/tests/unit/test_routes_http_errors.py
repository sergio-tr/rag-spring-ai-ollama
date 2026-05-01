"""HTTP error mapping for routes (replaces classification service on container)."""

from __future__ import annotations

from unittest.mock import MagicMock

import pytest
from fastapi.testclient import TestClient

from app.exceptions import ClassificationError, ModelNotFoundError, ValidationError


@pytest.fixture
def app_client():
    from uvicorn_entry import app

    return TestClient(app)


def test_classify_validation_error_returns_400(app_client: TestClient):
    from uvicorn_entry import app

    c = app.state.container
    orig = c._classification_service
    try:
        mock_svc = MagicMock()
        mock_svc.classify.side_effect = ValidationError("empty")
        c._classification_service = mock_svc
        r = app_client.post("/classify", json={"query": "x"})
        assert r.status_code == 400
    finally:
        c._classification_service = orig


def test_classify_model_not_found_returns_404(app_client: TestClient):
    from uvicorn_entry import app

    c = app.state.container
    orig = c._classification_service
    try:
        mock_svc = MagicMock()
        mock_svc.classify.side_effect = ModelNotFoundError("missing-model")
        c._classification_service = mock_svc
        r = app_client.post("/classify", json={"query": "hello"})
        assert r.status_code == 404
    finally:
        c._classification_service = orig


def test_classify_classification_error_returns_503(app_client: TestClient):
    from uvicorn_entry import app

    c = app.state.container
    orig = c._classification_service
    try:
        mock_svc = MagicMock()
        mock_svc.classify.side_effect = ClassificationError("down")
        c._classification_service = mock_svc
        r = app_client.post("/classify", json={"query": "hello"})
        assert r.status_code == 503
    finally:
        c._classification_service = orig
