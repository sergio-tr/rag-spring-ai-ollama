"""
Unit tests for ClassificationService (with mocked InferenceEngine).
Requires project dependencies (sklearn, seaborn) to be installed.
"""
from unittest import mock

import pytest

pytest.importorskip("sklearn")
pytest.importorskip("seaborn")

from app.exceptions import ClassificationError, ModelNotFoundError, ValidationError
from app.models.classification_result import ClassificationResult
from app.services.classification_service import ClassificationService


class TestClassificationService:
    def test_classify_empty_query_raises_validation_error(self):
        engine = _mock_engine(loaded=True, predict_result="COUNT_DOCUMENTS")
        svc = ClassificationService(engine)
        with pytest.raises(ValidationError) as exc_info:
            svc.classify("")
        assert "non-empty" in str(exc_info.value).lower() or "query" in str(exc_info.value).lower()

    def test_classify_whitespace_only_raises_validation_error(self):
        engine = _mock_engine(loaded=True, predict_result="COUNT_DOCUMENTS")
        svc = ClassificationService(engine)
        with pytest.raises(ValidationError):
            svc.classify("   ")

    def test_classify_returns_classification_result_when_loaded(self):
        engine = _mock_engine(loaded=True, predict_result="SUMMARIZE_MEETING")
        svc = ClassificationService(engine)
        result = svc.classify("Summarize the meeting")
        assert isinstance(result, ClassificationResult)
        assert result.query_type == "SUMMARIZE_MEETING"

    def test_classify_uses_default_model_id_when_not_provided(self):
        engine = _mock_engine(loaded=True, predict_result="COUNT_DOCUMENTS")
        svc = ClassificationService(engine)
        svc.classify("q")
        engine.predict.assert_called_once()
        args = engine.predict.call_args
        assert args[0][0] == "q"
        assert args[0][1] == "default"

    def test_classify_uses_provided_model_id(self):
        engine = _mock_engine(loaded=True, predict_result="COUNT_DOCUMENTS")
        svc = ClassificationService(engine)
        svc.classify("q", model_id="custom-model")
        engine.predict.assert_called_once()
        assert engine.predict.call_args[0][1] == "custom-model"

    def test_classify_model_not_loaded_triggers_load(self):
        loader = _mock_loader(is_loaded=False)
        engine = _mock_engine(loaded=False, predict_result="COUNT_DOCUMENTS", loader=loader)
        svc = ClassificationService(engine)
        svc.classify("q")
        loader.load_by_id.assert_called_once_with("default")

    def test_classify_file_not_found_raises_model_not_found(self):
        loader = _mock_loader(is_loaded=False, load_raises=FileNotFoundError())
        engine = _mock_engine(loaded=False, predict_result="COUNT_DOCUMENTS", loader=loader)
        svc = ClassificationService(engine)
        with pytest.raises(ModelNotFoundError):
            svc.classify("q")

    def test_classify_predict_runtime_error(self):
        engine = _mock_engine(loaded=True, predict_result="COUNT_DOCUMENTS")
        engine.predict.side_effect = RuntimeError("not ready")
        svc = ClassificationService(engine)
        with pytest.raises(ClassificationError, match="not available"):
            svc.classify("q")

    def test_classify_predict_other_exception(self):
        engine = _mock_engine(loaded=True, predict_result="COUNT_DOCUMENTS")
        engine.predict.side_effect = ValueError("unexpected")
        svc = ClassificationService(engine)
        with pytest.raises(ClassificationError, match="failed"):
            svc.classify("q")


def _mock_engine(*, loaded: bool, predict_result: str, loader=None):
    engine = mock.MagicMock()
    engine.predict.return_value = predict_result
    if loader is None:
        loader = _mock_loader(is_loaded=loaded)
    engine._loader = loader
    return engine


def _mock_loader(*, is_loaded: bool, load_raises=None):
    loader = mock.MagicMock()
    loader.is_loaded.return_value = is_loaded
    if load_raises:
        loader.load_by_id.side_effect = load_raises
    return loader
