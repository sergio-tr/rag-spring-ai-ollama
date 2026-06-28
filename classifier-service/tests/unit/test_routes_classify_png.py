import asyncio
from io import BytesIO
from types import SimpleNamespace
from unittest.mock import AsyncMock, MagicMock

import pytest
from fastapi import HTTPException
from starlette.datastructures import UploadFile

from app.exceptions import (
    ClassificationError,
    EvaluationError,
    ModelNotFoundError,
    TrainingError,
    ValidationError,
)
from app.models.training_result import TrainingResult
from app.routes import _evaluate_png_response, classify, evaluate_endpoint, train_endpoint
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


def _eval_result_dict(model_id: str = "default") -> dict:
    return {
        "modelId": model_id,
        "metrics": {
            "accuracy": 1.0,
            "classificationReport": {"accuracy": 1.0},
            "confusionMatrix": [[1]],
        },
    }


def _training_result(model_id: str = "abc12345", name: str = "human name") -> TrainingResult:
    return TrainingResult(model_id=model_id, name=name, metrics={"accuracy": 0.75, "macro_avg_f1": 0.7})


def _train_upload(filename: str = "train.xlsx") -> UploadFile:
    return UploadFile(filename=filename, file=BytesIO(b"not parsed by mocked route service"))


def test_train_endpoint_passes_labels_json_owner_and_keeps_model_name_as_metadata(monkeypatch, tmp_path):
    monkeypatch.setattr("app.routes.get_tracer", lambda: None)
    uploaded_path = tmp_path / "uploaded.xlsx"
    uploaded_path.write_bytes(b"xlsx")
    monkeypatch.setattr("app.routes.write_upload_to_temp", AsyncMock(return_value=uploaded_path))
    svc = MagicMock()
    svc.train.return_value = _training_result(model_id="unique-id", name="default")
    container = SimpleNamespace(training_service=svc)

    response = asyncio.run(
        train_endpoint(
            container=container,
            file=_train_upload(),
            model_name=" default ",
            labels='["COUNT_DOCUMENTS", "SUMMARIZE_MEETING"]',
            labels_file=None,
            epochs=3,
            batch_size=4,
            owner_id="  rag-user-42  ",
        )
    )

    assert response["modelId"] == "unique-id"
    assert response["name"] == "default"
    svc.train.assert_called_once_with(
        dataset_path=str(uploaded_path),
        model_name=" default ",
        class_names=["COUNT_DOCUMENTS", "SUMMARIZE_MEETING"],
        epochs=3,
        batch_size=4,
        owner_id="rag-user-42",
    )


def test_train_endpoint_reads_labels_file_when_labels_json_absent(monkeypatch, tmp_path):
    monkeypatch.setattr("app.routes.get_tracer", lambda: None)
    uploaded_path = tmp_path / "uploaded.xlsx"
    uploaded_path.write_bytes(b"xlsx")
    monkeypatch.setattr("app.routes.write_upload_to_temp", AsyncMock(return_value=uploaded_path))
    svc = MagicMock()
    svc.train.return_value = _training_result()
    container = SimpleNamespace(training_service=svc)
    labels_file = UploadFile(
        filename="labels.txt",
        file=BytesIO(b"COUNT_DOCUMENTS\n\nSUMMARIZE_MEETING\n"),
    )

    asyncio.run(
        train_endpoint(
            container=container,
            file=_train_upload(),
            model_name="named-model",
            labels=None,
            labels_file=labels_file,
            epochs=1,
            batch_size=2,
            owner_id=None,
        )
    )

    assert svc.train.call_args.kwargs["class_names"] == ["COUNT_DOCUMENTS", "SUMMARIZE_MEETING"]


def test_train_endpoint_rejects_invalid_labels_json_before_training(monkeypatch):
    monkeypatch.setattr("app.routes.get_tracer", lambda: None)
    svc = MagicMock()
    container = SimpleNamespace(training_service=svc)

    with pytest.raises(HTTPException) as err:
        asyncio.run(
            train_endpoint(
                container=container,
                file=_train_upload(),
                model_name="named-model",
                labels='{"not": "a-list"}',
                labels_file=None,
                epochs=1,
                batch_size=2,
                owner_id=None,
            )
        )

    assert err.value.status_code == 400
    assert err.value.detail["code"] == "VALIDATION_ERROR"
    svc.train.assert_not_called()


@pytest.mark.parametrize(
    "exc,expected_code",
    [
        (ValidationError("Dataset must have columns 'Question' and 'QueryType'"), "VALIDATION_ERROR"),
        (TrainingError("Training failed"), "TRAINING_ERROR"),
    ],
)
def test_train_endpoint_maps_domain_errors_to_http(monkeypatch, tmp_path, exc, expected_code):
    monkeypatch.setattr("app.routes.get_tracer", lambda: None)
    uploaded_path = tmp_path / "uploaded.xlsx"
    uploaded_path.write_bytes(b"xlsx")
    monkeypatch.setattr("app.routes.write_upload_to_temp", AsyncMock(return_value=uploaded_path))
    svc = MagicMock()
    svc.train.side_effect = exc
    container = SimpleNamespace(training_service=svc)

    with pytest.raises(HTTPException) as err:
        asyncio.run(
            train_endpoint(
                container=container,
                file=_train_upload(),
                model_name="bad-model",
                labels=None,
                labels_file=None,
                epochs=1,
                batch_size=2,
                owner_id=None,
            )
        )

    assert err.value.status_code == (400 if isinstance(exc, ValidationError) else 500)
    assert err.value.detail["code"] == expected_code


def test_train_endpoint_maps_unexpected_errors_to_generic_training_error(monkeypatch, tmp_path):
    monkeypatch.setattr("app.routes.get_tracer", lambda: None)
    uploaded_path = tmp_path / "uploaded.xlsx"
    uploaded_path.write_bytes(b"xlsx")
    monkeypatch.setattr("app.routes.write_upload_to_temp", AsyncMock(return_value=uploaded_path))
    svc = MagicMock()
    svc.train.side_effect = RuntimeError("disk full")
    container = SimpleNamespace(training_service=svc)

    with pytest.raises(HTTPException) as err:
        asyncio.run(
            train_endpoint(
                container=container,
                file=_train_upload(),
                model_name="bad-model",
                labels=None,
                labels_file=None,
                epochs=1,
                batch_size=2,
                owner_id=None,
            )
        )

    assert err.value.status_code == 500
    assert err.value.detail["code"] == "TRAINING_ERROR"
    assert err.value.detail["details"]["error"] == "disk full"


def test_evaluate_endpoint_uses_uploaded_dataset_path(monkeypatch):
    monkeypatch.setattr("app.routes.get_tracer", lambda: None)
    svc = MagicMock()
    svc.evaluate.return_value = SimpleNamespace(to_response_dict=lambda include_images_base64: _eval_result_dict("m1"))
    container = SimpleNamespace(evaluation_service=svc)
    upload = UploadFile(
        filename="eval.xlsx",
        file=BytesIO(b"not parsed by mocked service"),
    )

    response = asyncio.run(
        evaluate_endpoint(container=container, model_id="m1", include_images=False, file=upload)
    )

    assert response["modelId"] == "m1"
    _, kwargs = svc.evaluate.call_args
    assert kwargs["model_id"] == "m1"
    assert kwargs["include_images"] is False
    assert kwargs["eval_dataset_path"]
    assert kwargs["eval_dataset_path"].endswith(".xlsx")


def test_evaluate_endpoint_uses_default_dataset_when_upload_absent(monkeypatch):
    monkeypatch.setattr("app.routes.get_tracer", lambda: None)
    svc = MagicMock()
    svc.evaluate.return_value = SimpleNamespace(
        to_response_dict=lambda include_images_base64: _eval_result_dict("default")
    )
    container = SimpleNamespace(evaluation_service=svc)

    response = asyncio.run(
        evaluate_endpoint(container=container, model_id=None, include_images=False, file=None)
    )

    assert response["modelId"] == "default"
    svc.evaluate.assert_called_once_with(
        model_id=None,
        eval_dataset_path=None,
        include_images=False,
    )


def test_evaluate_endpoint_missing_default_dataset_returns_clear_400(monkeypatch):
    monkeypatch.setattr("app.routes.get_tracer", lambda: None)
    svc = MagicMock()
    svc.evaluate.side_effect = EvaluationError(
        "Evaluation dataset not found: data/evaluation_dataset.xlsx. Upload a file or place a dataset under DATA_DIR."
    )
    container = SimpleNamespace(evaluation_service=svc)

    with pytest.raises(HTTPException) as err:
        asyncio.run(evaluate_endpoint(container=container, model_id=None, include_images=False, file=None))

    assert err.value.status_code == 400
    assert isinstance(err.value.detail, dict)
    assert err.value.detail["code"] == "EVALUATION_ERROR"
    assert "Evaluation dataset not found" in err.value.detail["message"]
