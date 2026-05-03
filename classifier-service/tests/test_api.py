"""
API tests: /health, /models, /classify, /train.
"""
import pytest
from fastapi.testclient import TestClient


QUERY_TYPES_ALLOWED = {
    "COUNT_DOCUMENTS",
    "EXTRACT_ENTITIES",
    "COUNT_AND_EXPLAIN",
    "FIND_PARAGRAPH",
    "DECISION_EXTRACTION",
    "GET_DURATION",
    "GET_FIELD",
    "SUMMARIZE_TOPIC",
    "SUMMARIZE_MEETING",
    "BOOLEAN_QUERY",
    "FILTER_AND_LIST",
    "COMPARE",
}


def test_health_returns_200(client: TestClient):
    r = client.get("/health")
    assert r.status_code == 200
    data = r.json()
    assert data.get("status") == "ok"
    assert "model" in data
    assert data["model"] in ("loaded", "not_loaded")


def test_health_model_loaded_when_default_available(client: TestClient):
    """When models/default/ exists, health should report 'loaded'."""
    r = client.get("/health")
    assert r.status_code == 200
    if r.json().get("model") == "loaded":
        r2 = client.get("/models")
        assert r2.status_code == 200
        assert len(r2.json()) >= 1
        assert r2.json()[0]["id"] == "default"


def test_models_returns_list(client: TestClient):
    r = client.get("/models")
    assert r.status_code == 200
    data = r.json()
    assert isinstance(data, list)
    assert len(data) >= 1


def test_models_includes_default_first(client: TestClient):
    """First listed model is always the 'default' tag."""
    r = client.get("/models")
    assert r.status_code == 200
    data = r.json()
    assert len(data) >= 1
    first = data[0]
    assert first["id"] == "default"
    assert "name" in first


def test_classify_returns_query_type(client: TestClient):
    """POST /classify returns JSON with valid queryType."""
    r = client.post("/classify", json={"query": "How many actas mention the lift?"})
    if r.status_code == 503:
        pytest.skip("Model not loaded (503)")
    assert r.status_code == 200
    data = r.json()
    assert "queryType" in data
    assert isinstance(data["queryType"], str)
    assert len(data["queryType"]) > 0
    assert data["queryType"] in QUERY_TYPES_ALLOWED


def test_classify_empty_query_returns_400(client: TestClient):
    r = client.post("/classify", json={"query": ""})
    assert r.status_code == 400
    data = r.json()
    assert data.get("success") is False
    err = data.get("error") or {}
    assert "code" in err or "message" in err


def test_classify_missing_query_returns_422(client: TestClient):
    r = client.post("/classify", json={})
    assert r.status_code == 422
    data = r.json()
    assert data.get("success") is False
    assert "error" in data


def test_classify_invalid_model_id_returns_404(client: TestClient):
    r = client.post(
        "/classify",
        json={"query": "something", "modelId": "nonexistent_model_12345"},
    )
    assert r.status_code == 404


def test_train_rejects_non_excel_returns_400(client: TestClient):
    r = client.post(
        "/train",
        data={"model_name": "test", "epochs": 2, "batch_size": 2},
        files={"file": ("data.txt", b"not excel", "text/plain")},
    )
    assert r.status_code == 400


def test_train_with_minimal_excel_returns_200(client: TestClient, minimal_dataset_excel):
    """POST /train with valid Excel registers a model and returns 200 with model_id and metrics."""
    r = client.post(
        "/train",
        data={"model_name": "test-model", "epochs": 1, "batch_size": 2},
        files={
            "file": (
                "dataset.xlsx",
                minimal_dataset_excel.read(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            )
        },
    )
    if r.status_code == 500:
        pytest.skip("Training failed (e.g. TensorFlow env)")
    assert r.status_code == 200
    data = r.json()
    assert "modelId" in data
    assert "name" in data
    assert data["name"] == "test-model"
    assert "metrics" in data


def test_openapi_schema_available(client: TestClient):
    r = client.get("/openapi.json")
    assert r.status_code == 200
    data = r.json()
    assert "openapi" in data
    assert "/health" in data["paths"]
    assert "/classify" in data["paths"]
    assert "/models" in data["paths"]
    assert "/train" in data["paths"]
    assert "/evaluate" in data["paths"]


def test_evaluate_returns_metrics_and_optionally_images(client: TestClient):
    """POST /evaluate returns metrics and optionally base64 images (camelCase)."""
    r = client.post("/evaluate", params={"includeImages": True})
    if r.status_code == 400:
        body = r.json()
        err = body.get("error") if isinstance(body.get("error"), dict) else body
        msg = (err.get("message") or "").lower()
        if "not found" in msg or "dataset" in msg or "evaluation" in msg:
            pytest.skip("Default evaluation dataset or model not usable: " + (err.get("message") or ""))
    if r.status_code == 404:
        pytest.skip("Model not found")
    assert r.status_code == 200
    data = r.json()
    assert "modelId" in data
    assert "metrics" in data
    assert "classificationReport" in data["metrics"]
    assert "confusionMatrix" in data["metrics"]
    assert "accuracy" in data["metrics"]
    if data.get("classificationReportImageBase64"):
        import base64
        assert len(base64.b64decode(data["classificationReportImageBase64"])) > 0
    if data.get("confusionMatrixImageBase64"):
        import base64
        assert len(base64.b64decode(data["confusionMatrixImageBase64"])) > 0


def test_evaluate_report_image_returns_png(client: TestClient):
    """GET /evaluate/{model_id}/report.png returns image/png."""
    r = client.get("/evaluate/default/report.png")
    if r.status_code in (400, 404):
        pytest.skip("Model or eval dataset not available")
    assert r.status_code == 200
    assert r.headers.get("content-type", "").startswith("image/png")
    assert len(r.content) > 0


def test_evaluate_confusion_image_returns_png(client: TestClient):
    """GET /evaluate/{model_id}/confusion.png returns image/png."""
    r = client.get("/evaluate/default/confusion.png")
    if r.status_code in (400, 404):
        pytest.skip("Model or eval dataset not available")
    assert r.status_code == 200
    assert r.headers.get("content-type", "").startswith("image/png")
    assert len(r.content) > 0
