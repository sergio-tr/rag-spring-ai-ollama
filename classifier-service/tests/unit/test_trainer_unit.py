"""Unit tests for TrainingPipeline with mocked TensorFlow."""

from __future__ import annotations

from unittest.mock import MagicMock

import numpy as np
import pandas as pd
import pytest

from app.config import Config
from app.training import trainer as trainer_mod
from app.training.trainer import TrainingPipeline


def _reset_config():
    Config._instance = None


@pytest.fixture
def models_dir(tmp_path, monkeypatch):
    _reset_config()
    root = tmp_path / "models"
    monkeypatch.setenv("MODELS_DIR", str(root))
    return root


def _make_excel(path, n: int = 10):
    df = pd.DataFrame({
        "Question": [f"q{i}" for i in range(n)],
        "QueryType": (["COUNT_DOCUMENTS"] * (n // 2)) + (["EXTRACT_ENTITIES"] * (n - n // 2)),
    })
    df.to_excel(path, index=False)


def test_train_happy_path_mocked_tf(models_dir, tmp_path, monkeypatch):
    excel = tmp_path / "train.xlsx"
    _make_excel(excel)

    X = np.array([f"q{i}" for i in range(10)], dtype=object)
    y = np.zeros((10, 2))
    y[:5, 0] = 1
    y[5:, 1] = 1

    def fake_split(X_arr, y_arr, **kwargs):
        return X_arr[:8], X_arr[8:], y_arr[:8], y_arr[8:]

    mock_vec = MagicMock()
    mock_tv = MagicMock(return_value=mock_vec)
    mock_model = MagicMock()
    mock_model.fit = MagicMock()
    mock_model.save = MagicMock()
    mock_model.predict = MagicMock(
        return_value=np.array([[0.6, 0.4], [0.3, 0.7]])
    )

    monkeypatch.setattr(trainer_mod, "train_test_split", fake_split)
    monkeypatch.setattr(trainer_mod.tf.keras.layers, "TextVectorization", mock_tv)
    monkeypatch.setattr(trainer_mod.tf.keras, "Sequential", MagicMock(return_value=mock_model))

    registry = MagicMock()
    registry.create_new_model_id.return_value = "trainid01"
    registry.register_model = MagicMock()

    pipeline = TrainingPipeline(config=Config(), registry=registry)
    out = pipeline.train(str(excel), "trained-name", epochs=1, batch_size=2)

    assert out["model_id"] == "trainid01"
    assert out["name"] == "trained-name"
    assert "metrics" in out
    registry.register_model.assert_called_once()
    mock_model.fit.assert_called_once()
    mock_model.save.assert_called_once()
    meta = registry.register_model.call_args.kwargs["metadata"]
    assert "ownerId" not in meta


def test_train_metadata_contains_owner_id_when_provided(models_dir, tmp_path, monkeypatch):
    excel = tmp_path / "train.xlsx"
    _make_excel(excel)

    def fake_split(X_arr, y_arr, **kwargs):
        return X_arr[:8], X_arr[8:], y_arr[:8], y_arr[8:]

    mock_vec = MagicMock()
    mock_tv = MagicMock(return_value=mock_vec)
    mock_model = MagicMock()
    mock_model.fit = MagicMock()
    mock_model.save = MagicMock()
    mock_model.predict = MagicMock(
        return_value=np.array([[0.6, 0.4], [0.3, 0.7]])
    )

    monkeypatch.setattr(trainer_mod, "train_test_split", fake_split)
    monkeypatch.setattr(trainer_mod.tf.keras.layers, "TextVectorization", mock_tv)
    monkeypatch.setattr(trainer_mod.tf.keras, "Sequential", MagicMock(return_value=mock_model))

    registry = MagicMock()
    registry.create_new_model_id.return_value = "own01"
    registry.register_model = MagicMock()

    pipeline = TrainingPipeline(config=Config(), registry=registry)
    pipeline.train(str(excel), "m", epochs=1, batch_size=2, owner_id="rag-user-9")
    meta = registry.register_model.call_args.kwargs["metadata"]
    assert meta["ownerId"] == "rag-user-9"


def test_train_stratify_falls_back_when_value_error(models_dir, tmp_path, monkeypatch):
    excel = tmp_path / "tiny.xlsx"
    _make_excel(excel, n=4)

    calls = {"n": 0}

    def fake_split(X_arr, y_arr, **kwargs):
        calls["n"] += 1
        if kwargs.get("stratify") is not None:
            raise ValueError("stratify failed")
        return X_arr[:2], X_arr[2:], y_arr[:2], y_arr[2:]

    mock_vec = MagicMock()
    monkeypatch.setattr(trainer_mod, "train_test_split", fake_split)
    monkeypatch.setattr(trainer_mod.tf.keras.layers, "TextVectorization", MagicMock(return_value=mock_vec))
    mock_model = MagicMock()
    mock_model.fit = MagicMock()
    mock_model.save = MagicMock()
    # Validation split has 2 rows; predict must return one row per sample
    mock_model.predict = MagicMock(
        return_value=np.array([[0.5, 0.5], [0.5, 0.5]])
    )
    monkeypatch.setattr(trainer_mod.tf.keras, "Sequential", MagicMock(return_value=mock_model))

    registry = MagicMock()
    registry.create_new_model_id.return_value = "idstrat"
    registry.register_model = MagicMock()

    pipeline = TrainingPipeline(config=Config(), registry=registry)
    out = pipeline.train(str(excel), "n2", epochs=1, batch_size=2)
    assert out["model_id"] == "idstrat"
    assert calls["n"] == 2


def test_train_raises_when_missing_columns(tmp_path, monkeypatch):
    _reset_config()
    excel = tmp_path / "bad.xlsx"
    pd.DataFrame({"X": [1]}).to_excel(excel, index=False)

    pipeline = TrainingPipeline(config=Config(), registry=MagicMock())
    with pytest.raises(ValueError, match="Question"):
        pipeline.train(str(excel), "n")


def test_train_with_class_names_filters_empty_raises(tmp_path, monkeypatch):
    _reset_config()
    excel = tmp_path / "cf.xlsx"
    pd.DataFrame({"Question": ["a"], "QueryType": ["COUNT_DOCUMENTS"]}).to_excel(excel, index=False)

    pipeline = TrainingPipeline(config=Config(), registry=MagicMock())
    with pytest.raises(ValueError, match="No rows left"):
        pipeline.train(str(excel), "n", class_names=["EXTRACT_ENTITIES"])
