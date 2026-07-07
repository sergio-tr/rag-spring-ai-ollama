"""HTTP /train sklearn path and default immutability guards."""
from __future__ import annotations

import hashlib
from pathlib import Path

import pytest
from fastapi.testclient import TestClient


def _default_model_checksum() -> str:
    path = Path("models/default/model.joblib")
    if not path.is_file():
        pytest.skip("Shipped default model.joblib not present")
    return hashlib.sha256(path.read_bytes()).hexdigest()


def test_train_with_minimal_excel_produces_sklearn_artifact(minimal_dataset_excel, tmp_path, monkeypatch):
    from app.config import Config
    from fastapi.testclient import TestClient
    from uvicorn_entry import app

    models_dir = tmp_path / "models"
    models_dir.mkdir()
    Config._instance = None
    monkeypatch.setenv("MODELS_DIR", str(models_dir))
    client = TestClient(app)

    before = _default_model_checksum()

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
    assert r.status_code == 200, r.text
    data = r.json()
    assert "modelId" in data
    assert data["modelId"] != "default"
    assert data["name"] == "test-model"
    assert "metrics" in data

    model_dir = models_dir / data["modelId"]
    assert (model_dir / "model.joblib").is_file()
    assert (model_dir / "labels.txt").is_file()
    assert (model_dir / "metadata.json").is_file()

    meta = (model_dir / "metadata.json").read_text(encoding="utf-8")
    assert "sklearn" in meta

    after = _default_model_checksum()
    assert before == after


def test_train_rejects_reserved_default_name(client: TestClient, minimal_dataset_excel):
    r = client.post(
        "/train",
        data={"model_name": "default", "epochs": 1, "batch_size": 2},
        files={
            "file": (
                "dataset.xlsx",
                minimal_dataset_excel.read(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            )
        },
    )
    assert r.status_code == 400
    body = r.json()
    err = body.get("error") or body.get("detail") or {}
    message = str(err.get("message") if isinstance(err, dict) else err).lower()
    assert "default" in message or "reserved" in message


def test_train_rejects_missing_columns(client: TestClient, tmp_path):
    import io

    import pandas as pd

    buf = io.BytesIO()
    pd.DataFrame({"X": [1]}).to_excel(buf, index=False, engine="openpyxl")
    buf.seek(0)

    r = client.post(
        "/train",
        data={"model_name": "bad-cols", "epochs": 1, "batch_size": 2},
        files={
            "file": (
                "bad.xlsx",
                buf.read(),
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            )
        },
    )
    assert r.status_code == 400
    body = r.json()
    err = body.get("error") or body.get("detail") or {}
    message = str(err.get("message") if isinstance(err, dict) else err)
    assert "Question" in message and "QueryType" in message
