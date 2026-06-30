"""Unit tests for EvaluationPipeline (mocked TensorFlow / Excel)."""

from __future__ import annotations

from unittest.mock import MagicMock

import numpy as np
import pandas as pd
import pytest

from app.config import Config
from app.evaluation.evaluator import EvaluationPipeline
from app.inference.loaded_model import LoadedModel


def _reset_config():
    Config._instance = None


@pytest.fixture
def pipeline_mocks():
    _reset_config()
    loader = MagicMock()
    loader.is_loaded.return_value = True
    loader.get_class_names.return_value = ["A", "B"]
    model = MagicMock()
    model.predict.return_value = np.array([[0.2, 0.8], [0.9, 0.1]])
    loader.get_model.return_value = model
    loader.get_loaded_model.return_value = LoadedModel(
        model_type="keras",
        artifact=model,
        class_names=["A", "B"],
    )
    return loader, model


def test_canonical_class_order_used_in_confusion_matrix(tmp_path, pipeline_mocks):
    loader, model = pipeline_mocks
    loader.get_class_names.return_value = ["BOOLEAN_QUERY", "COUNT_DOCUMENTS"]
    loader.get_loaded_model.return_value = LoadedModel(
        model_type="keras",
        artifact=model,
        class_names=["BOOLEAN_QUERY", "COUNT_DOCUMENTS"],
    )
    model.predict.return_value = np.array([[0.2, 0.8], [0.9, 0.1]])
    excel = tmp_path / "eval-order.xlsx"
    df = pd.DataFrame(
        {"Question": ["q1", "q2"], "QueryType": ["BOOLEAN_QUERY", "COUNT_DOCUMENTS"]}
    )
    df.to_excel(excel, index=False)

    ep = EvaluationPipeline(config=Config(), loader=loader, registry=MagicMock())
    result = ep.evaluate("m1", str(excel), include_images=False)

    assert result.class_names == ["COUNT_DOCUMENTS", "BOOLEAN_QUERY"]


def test_evaluate_happy_path_includes_images(tmp_path, pipeline_mocks):
    loader, model = pipeline_mocks
    excel = tmp_path / "eval.xlsx"
    df = pd.DataFrame({"Question": ["q1", "q2"], "QueryType": ["B", "A"]})
    df.to_excel(excel, index=False)

    ep = EvaluationPipeline(config=Config(), loader=loader, registry=MagicMock())
    result = ep.evaluate("m1", str(excel), include_images=True)

    assert result.model_id == "m1"
    assert result.class_names == ["A", "B"]
    assert result.classification_report_image_bytes is not None
    assert result.confusion_matrix_image_bytes is not None
    assert len(result.confusion_matrix) == 2
    model.predict.assert_called_once()


def test_evaluate_accepts_spanish_pregunta_column(tmp_path, pipeline_mocks):
    loader, model = pipeline_mocks
    model.predict.return_value = np.array([[0.2, 0.8], [0.9, 0.1]])
    excel = tmp_path / "eval-es.xlsx"
    df = pd.DataFrame({"Pregunta": ["q1", "q2"], "QueryType": ["B", "A"]})
    df.to_excel(excel, index=False)

    ep = EvaluationPipeline(config=Config(), loader=loader, registry=MagicMock())
    result = ep.evaluate("m1", str(excel), include_images=False)

    assert result.model_id == "m1"
    assert result.class_names == ["A", "B"]
    model.predict.assert_called_once()


def test_evaluate_loads_when_not_cached(pipeline_mocks, tmp_path):
    loader, model = pipeline_mocks
    loader.is_loaded.return_value = False
    # Two samples covering both labels so classification_report matches class_names size
    model.predict.return_value = np.array([[1.0, 0.0], [0.0, 1.0]])
    excel = tmp_path / "e.xlsx"
    pd.DataFrame(
        {"Question": ["x", "y"], "QueryType": ["A", "B"]}
    ).to_excel(excel, index=False)

    ep = EvaluationPipeline(config=Config(), loader=loader, registry=MagicMock())
    ep.evaluate("m2", str(excel), include_images=False)

    loader.load_by_id.assert_called_once_with("m2")


def test_evaluate_include_images_false_skips_png(tmp_path, pipeline_mocks):
    loader, model = pipeline_mocks
    model.predict.return_value = np.array([[1.0, 0.0], [0.0, 1.0]])
    excel = tmp_path / "e2.xlsx"
    pd.DataFrame(
        {"Question": ["x", "y"], "QueryType": ["A", "B"]}
    ).to_excel(excel, index=False)

    ep = EvaluationPipeline(config=Config(), loader=loader, registry=MagicMock())
    result = ep.evaluate("m1", str(excel), include_images=False)
    assert result.classification_report_image_bytes is None
    assert result.confusion_matrix_image_bytes is None


def test_evaluate_raises_without_question_column(tmp_path, pipeline_mocks):
    loader, _ = pipeline_mocks
    excel = tmp_path / "bad.xlsx"
    pd.DataFrame({"QueryType": ["A"]}).to_excel(excel, index=False)

    ep = EvaluationPipeline(config=Config(), loader=loader, registry=MagicMock())
    with pytest.raises(ValueError, match="Question"):
        ep.evaluate("m1", str(excel))


def test_evaluate_raises_when_no_valid_rows(tmp_path, pipeline_mocks):
    loader, _ = pipeline_mocks
    loader.get_class_names.return_value = ["A", "B"]
    excel = tmp_path / "empty.xlsx"
    pd.DataFrame({"Question": ["x"], "QueryType": ["UNKNOWN"]}).to_excel(excel, index=False)

    ep = EvaluationPipeline(config=Config(), loader=loader, registry=MagicMock())
    with pytest.raises(ValueError, match="No valid rows"):
        ep.evaluate("m1", str(excel))


def test_build_classification_report_image_fallback_rows():
    _reset_config()
    ep = EvaluationPipeline(config=Config(), loader=MagicMock(), registry=MagicMock())
    report = {
        "A": {"precision": 0.5, "recall": 0.5, "f1-score": 0.5},
        "accuracy": 0.5,
        "macro avg": {"precision": 0.5, "recall": 0.5, "f1-score": 0.5},
    }
    png = ep._build_classification_report_image(report, ["Z"])
    assert isinstance(png, bytes) and len(png) > 100


def test_build_confusion_matrix_image():
    _reset_config()
    ep = EvaluationPipeline(config=Config(), loader=MagicMock(), registry=MagicMock())
    cm = np.array([[1, 2], [3, 4]])
    png = ep._build_confusion_matrix_image(cm, ["A", "B"])
    assert isinstance(png, bytes) and len(png) > 100


def test_evaluate_warns_and_filters_unknown_labels(tmp_path, pipeline_mocks, caplog):
    import logging

    loader, model = pipeline_mocks
    loader.get_class_names.return_value = ["A", "B"]
    excel = tmp_path / "mix.xlsx"
    pd.DataFrame(
        {"Question": ["a", "b", "c"], "QueryType": ["A", "BAD", "B"]}
    ).to_excel(excel, index=False)
    model.predict.return_value = np.array([[1.0, 0.0], [0.0, 1.0]])

    ep = EvaluationPipeline(config=Config(), loader=loader, registry=MagicMock())
    with caplog.at_level(logging.WARNING):
        result = ep.evaluate("m1", str(excel), include_images=False)
    assert "Dropping" in caplog.text
    assert result.model_id == "m1"
