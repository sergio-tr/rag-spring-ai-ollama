"""Extra branch coverage for service layer."""

from __future__ import annotations

from unittest.mock import MagicMock

import pytest

from app.exceptions import EvaluationError, ValidationError
from app.services.classification_service import ClassificationService
from app.services.evaluation_service import EvaluationService
from app.services.training_service import TrainingService


def test_classification_service_empty_query_raises():
    engine = MagicMock()
    engine._loader = MagicMock()
    engine._loader.is_loaded.return_value = True
    svc = ClassificationService(engine)
    with pytest.raises(ValidationError, match="non-empty"):
        svc.classify("   ", None)


def test_evaluation_service_missing_dataset_file():
    pipeline = MagicMock()
    svc = EvaluationService(pipeline=pipeline)
    with pytest.raises(EvaluationError, match="not found"):
        svc.evaluate(model_id="default", eval_dataset_path="/nonexistent/path/to/dataset.xlsx")


def test_training_service_empty_model_name_raises():
    svc = TrainingService(MagicMock())
    with pytest.raises(ValidationError, match="model_name"):
        svc.train("/tmp/fake.xlsx", "   ")
