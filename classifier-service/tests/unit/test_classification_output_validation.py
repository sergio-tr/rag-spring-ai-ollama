"""ClassificationService rejects model outputs outside the Java QueryType contract."""

from __future__ import annotations

from unittest.mock import MagicMock

import pytest

from app.config import Config
from app.exceptions import ClassificationError
from app.inference.inference_engine import InferenceEngine
from app.services.classification_service import ClassificationService


def test_classify_raises_classification_error_on_invalid_model_output():
    loader = MagicMock()
    loader.is_loaded.return_value = True
    loader.get_model.return_value = MagicMock()
    loader.get_class_names.return_value = ["COUNT_DOCUMENTS"]

    engine = InferenceEngine(loader, config=Config())
    engine.predict = MagicMock(return_value="LEGACY_UNKNOWN_LABEL")  # type: ignore[method-assign]

    svc = ClassificationService(engine, config=Config())
    with pytest.raises(ClassificationError, match="Invalid classifier output"):
        svc.classify("how many documents?", model_id="default")
