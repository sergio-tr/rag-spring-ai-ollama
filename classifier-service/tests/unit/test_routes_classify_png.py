from types import SimpleNamespace
from unittest.mock import MagicMock

import pytest
from fastapi import HTTPException

from app.exceptions import (
    ClassificationError,
    EvaluationError,
    ModelNotFoundError,
    ValidationError,
)
from app.routes import _evaluate_png_response, classify
from app.schemas import ClassifyRequest


def _patch_tracing(monkeypatch):
    span = MagicMock()
    tracer = MagicMock()
    tracer.start_span.return_value = span
    monkeypatch.setattr("app.routes.get_tracer", lambda: tracer)
    monkeypatch.setattr("app.routes.span_set_classify_start", MagicMock())
    monkeypatch.setattr("app.routes.span_set_classify_ok", MagicMock())
    monkeypatch.setattr("app.routes.span_record_error", MagicMock())
    monkeypatch.setattr("app.routes.span_end", MagicMock())
    return span


def test_classify_success_prefers_query_param_model_id_and_strips_query(monkeypatch):
    _patch_tracing(monkeypatch)
    result = SimpleNamespace(
        query_type="COUNT_DOCUMENTS",
        to_response_dict=lambda: {"queryType": "COUNT_DOCUMENTS"},
    )
    svc = MagicMock()
    svc.classify.return_value = result
    container = SimpleNamespace(classification_service=svc)

    response = classify(
        req=ClassifyRequest(query="  how many docs  ", modelId="body-model"),
        container=container,
        model_id="query-model",
    )

    assert response == {"queryType": "COUNT_DOCUMENTS"}
    svc.classify.assert_called_once_with(query="how many docs", model_id="query-model")


@pytest.mark.parametrize(
    "exc,expected_status",
    [
        (ValidationError("empty query"), 400),
        (ModelNotFoundError("missing-model"), 404),
        (ClassificationError("backend down"), 503),
    ],
)
def test_classify_maps_service_errors_to_http(monkeypatch, exc, expected_status):
    _patch_tracing(monkeypatch)
    svc = MagicMock()
    svc.classify.side_effect = exc
    container = SimpleNamespace(classification_service=svc)

    with pytest.raises(HTTPException) as err:
        classify(req=ClassifyRequest(query="x"), container=container, model_id=None)

    assert err.value.status_code == expected_status
    assert isinstance(err.value.detail, dict)
    assert "code" in err.value.detail
    assert "message" in err.value.detail


def test_classify_edge_whitespace_query_passes_empty_string_to_service(monkeypatch):
    _patch_tracing(monkeypatch)
    svc = MagicMock()
    svc.classify.side_effect = ValidationError("query cannot be empty")
    container = SimpleNamespace(classification_service=svc)

    with pytest.raises(HTTPException) as err:
        classify(req=ClassifyRequest(query="   "), container=container, model_id=None)

    assert err.value.status_code == 400
    svc.classify.assert_called_once_with(query="", model_id=None)


def test_evaluate_png_response_success_report_image_returns_png_response():
    result = SimpleNamespace(
        classification_report_image_bytes=b"\x89PNG\r\nok",
        confusion_matrix_image_bytes=b"\x89PNG\r\ncm",
    )
    svc = MagicMock()
    svc.evaluate.return_value = result
    container = SimpleNamespace(evaluation_service=svc)

    response = _evaluate_png_response(container, "default", report=True)

    assert response.status_code == 200
    assert response.media_type == "image/png"
    assert response.headers["content-disposition"] == "inline; filename=classification_report.png"
    assert response.body == b"\x89PNG\r\nok"


def test_evaluate_png_response_success_confusion_image_returns_png_response():
    result = SimpleNamespace(
        classification_report_image_bytes=b"\x89PNG\r\nok",
        confusion_matrix_image_bytes=b"\x89PNG\r\ncm",
    )
    svc = MagicMock()
    svc.evaluate.return_value = result
    container = SimpleNamespace(evaluation_service=svc)

    response = _evaluate_png_response(container, "default", report=False)

    assert response.status_code == 200
    assert response.media_type == "image/png"
    assert response.headers["content-disposition"] == "inline; filename=confusion_matrix.png"
    assert response.body == b"\x89PNG\r\ncm"


def test_evaluate_png_response_empty_or_missing_image_bytes_returns_500():
    result = SimpleNamespace(
        classification_report_image_bytes=b"",
        confusion_matrix_image_bytes=None,
    )
    svc = MagicMock()
    svc.evaluate.return_value = result
    container = SimpleNamespace(evaluation_service=svc)

    with pytest.raises(HTTPException) as err:
        _evaluate_png_response(container, "default", report=True)

    assert err.value.status_code == 500
    assert err.value.detail == "Image not generated"


def test_evaluate_png_response_malformed_image_bytes_are_returned_as_is():
    result = SimpleNamespace(
        classification_report_image_bytes=b"not-a-real-png",
        confusion_matrix_image_bytes=b"irrelevant",
    )
    svc = MagicMock()
    svc.evaluate.return_value = result
    container = SimpleNamespace(evaluation_service=svc)

    response = _evaluate_png_response(container, "default", report=True)

    assert response.status_code == 200
    assert response.body == b"not-a-real-png"
    assert response.media_type == "image/png"


@pytest.mark.parametrize(
    "exc,expected_status",
    [
        (ModelNotFoundError("missing-model"), 404),
        (EvaluationError("dataset invalid"), 400),
    ],
)
def test_evaluate_png_response_maps_domain_errors_to_http(exc, expected_status):
    svc = MagicMock()
    svc.evaluate.side_effect = exc
    container = SimpleNamespace(evaluation_service=svc)

    with pytest.raises(HTTPException) as err:
        _evaluate_png_response(container, "missing-model", report=True)

    assert err.value.status_code == expected_status
    assert isinstance(err.value.detail, dict)
    assert "code" in err.value.detail
    assert "message" in err.value.detail
