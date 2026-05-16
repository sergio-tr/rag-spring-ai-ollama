"""Branch coverage for service error handling and happy paths."""

from __future__ import annotations

from pathlib import Path
from unittest.mock import MagicMock, patch

import pytest

from app.evaluation.result import EvaluationResult
from app.exceptions import (
    ClassificationError,
    EvaluationError,
    ModelNotFoundError,
    TrainingError,
    ValidationError,
)
from app.models.training_result import TrainingResult
from app.services.classification_service import ClassificationService
from app.services.evaluation_service import EvaluationService
from app.services.training_service import TrainingService


def test_classification_runtime_error_maps_to_classification_error():
    engine = MagicMock()
    engine._loader = MagicMock()
    engine._loader.is_loaded.return_value = True
    engine.predict.side_effect = RuntimeError("tf failed")
    svc = ClassificationService(engine)
    with pytest.raises(ClassificationError, match="not available"):
        svc.classify("hello")


def test_classification_generic_exception_maps_to_classification_error():
    engine = MagicMock()
    engine._loader = MagicMock()
    engine._loader.is_loaded.return_value = True
    engine.predict.side_effect = OSError("boom")
    svc = ClassificationService(engine)
    with pytest.raises(ClassificationError, match="failed"):
        svc.classify("hello")


def test_classification_file_not_found_in_impl():
    engine = MagicMock()
    engine._loader = MagicMock()
    engine._loader.is_loaded.return_value = True
    engine.predict.side_effect = FileNotFoundError()
    svc = ClassificationService(engine)
    with pytest.raises(ModelNotFoundError):
        svc.classify("hello")


def test_evaluation_service_success():
    pipeline = MagicMock()
    pipeline.evaluate.return_value = EvaluationResult(
        model_id="m1",
        classification_report={"accuracy": 1.0},
        confusion_matrix=[[1]],
        class_names=["A"],
    )
    svc = EvaluationService(pipeline=pipeline)
    fake_path = Path("/tmp/eval.xlsx")
    with patch.object(Path, "exists", return_value=True):
        r = svc.evaluate(model_id="m1", eval_dataset_path=str(fake_path), include_images=False)
    assert r.model_id == "m1"
    pipeline.evaluate.assert_called_once()


def test_evaluation_service_uses_default_dataset_when_upload_absent(tmp_path):
    default_dataset = tmp_path / "evaluation_dataset.xlsx"
    default_dataset.write_bytes(b"placeholder")
    config = MagicMock()
    config.get_default_model_id.return_value = "default"
    config.get_default_eval_dataset_path.return_value = str(default_dataset)
    pipeline = MagicMock()
    pipeline.evaluate.return_value = EvaluationResult(
        model_id="default",
        classification_report={"accuracy": 1.0},
        confusion_matrix=[[1]],
        class_names=["A"],
    )

    svc = EvaluationService(config=config, pipeline=pipeline)
    result = svc.evaluate(include_images=False)

    assert result.model_id == "default"
    pipeline.evaluate.assert_called_once_with(
        model_id="default",
        eval_dataset_path=str(default_dataset),
        include_images=False,
    )


def test_evaluation_service_missing_default_dataset_error_is_clear(tmp_path):
    missing_dataset = tmp_path / "missing" / "evaluation_dataset.xlsx"
    config = MagicMock()
    config.get_default_model_id.return_value = "default"
    config.get_default_eval_dataset_path.return_value = str(missing_dataset)
    svc = EvaluationService(config=config, pipeline=MagicMock())

    with pytest.raises(EvaluationError) as err:
        svc.evaluate()

    assert "Evaluation dataset not found" in str(err.value)
    assert "Upload a file" in str(err.value)


def test_evaluation_model_not_found():
    pipeline = MagicMock()
    pipeline.evaluate.side_effect = FileNotFoundError()
    svc = EvaluationService(pipeline=pipeline)
    with patch.object(Path, "exists", return_value=True):
        with pytest.raises(ModelNotFoundError):
            svc.evaluate(model_id="missing", eval_dataset_path="/x/eval.xlsx")


def test_evaluation_value_error():
    pipeline = MagicMock()
    pipeline.evaluate.side_effect = ValueError("bad dataset")
    svc = EvaluationService(pipeline=pipeline)
    with patch.object(Path, "exists", return_value=True):
        with pytest.raises(EvaluationError, match="bad dataset"):
            svc.evaluate(eval_dataset_path="/x/eval.xlsx")


def test_evaluation_generic_exception():
    pipeline = MagicMock()
    pipeline.evaluate.side_effect = OSError("disk")
    svc = EvaluationService(pipeline=pipeline)
    with patch.object(Path, "exists", return_value=True):
        with pytest.raises(EvaluationError, match="Evaluation failed"):
            svc.evaluate(eval_dataset_path="/x/eval.xlsx")


def test_training_service_success():
    pipeline = MagicMock()
    pipeline.train.return_value = {
        "model_id": "mid",
        "name": "n",
        "metrics": {"accuracy": 0.9},
    }
    svc = TrainingService(pipeline)
    r = svc.train("/path/d.xlsx", "my-model", epochs=1, batch_size=4)
    assert isinstance(r, TrainingResult)
    assert r.model_id == "mid"
    assert r.metrics["accuracy"] == 0.9


def test_training_value_error_from_pipeline():
    pipeline = MagicMock()
    pipeline.train.side_effect = ValueError("bad")
    svc = TrainingService(pipeline)
    with pytest.raises(ValidationError, match="bad"):
        svc.train("/p.xlsx", "name")


def test_training_generic_error():
    pipeline = MagicMock()
    pipeline.train.side_effect = OSError("oops")
    svc = TrainingService(pipeline)
    with pytest.raises(TrainingError, match="Training failed"):
        svc.train("/p.xlsx", "name")
