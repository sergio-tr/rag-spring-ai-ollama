"""
Classification service: ensures model is loaded and delegates prediction to InferenceEngine.
Returns domain model ClassificationResult; raises service exceptions on failure.
"""
from app.base import BaseService
from app.config import Config
from app.exceptions import ClassificationError, ModelNotFoundError, ValidationError
from app.inference.inference_engine import InferenceEngine
from app.models.classification_result import ClassificationResult


class ClassificationService(BaseService):
    """Orchestrates classification: validation, model resolution, inference."""

    def __init__(self, inference_engine: InferenceEngine, config: Config | None = None) -> None:
        super().__init__()
        self._engine = inference_engine
        self._config = config or Config()

    def classify(self, query: str, model_id: str | None = None) -> ClassificationResult:
        """
        Classifies the query. model_id optional (default from config).
        Raises ValidationError if query is empty; ModelNotFoundError if model missing; ClassificationError on inference failure.
        """
        if not query or not query.strip():
            raise ValidationError("query must be non-empty")
        resolved_id = (model_id or "").strip() or self._config.get_default_model_id()
        try:
            if not self._engine._loader.is_loaded(resolved_id):
                self._engine._loader.load_by_id(resolved_id)
        except FileNotFoundError as e:
            raise ModelNotFoundError(resolved_id) from e
        try:
            query_type = self._engine.predict(query.strip(), resolved_id)
            return ClassificationResult(query_type=query_type)
        except RuntimeError as e:
            self.logger.exception("Model not ready: %s", e)
            raise ClassificationError("Model not available") from e
        except FileNotFoundError as e:
            raise ModelNotFoundError(resolved_id) from e
        except Exception as e:
            self.logger.exception("Classification failed: %s", e)
            raise ClassificationError("Classification failed") from e
