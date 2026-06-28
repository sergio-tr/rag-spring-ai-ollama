"""
Classification service: ensures model is loaded and delegates prediction to InferenceEngine.
Returns domain model ClassificationResult; raises service exceptions on failure.
Traced via TracedService.run_traced for classifier.service.classify span.
"""
from app.base import TracedService
from app.config import Config
from app.exceptions import ClassificationError, ModelNotFoundError, ValidationError
from app.inference.inference_engine import InferenceEngine
from app.models.classification_result import ClassificationResult
from app.query_type_contract import validate_query_type_label
from app.telemetry import record_classifier_call


class ClassificationService(TracedService):
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
        return self.run_traced(
            "classifier.service.classify",
            lambda: self._classify_impl(query.strip(), resolved_id),
            input_attrs={
                "queryLength": str(len((query or "").strip())),
                "modelId": resolved_id,
            },
            output_attr="query_type",
            output_value_fn=lambda r: r.query_type,
        )

    def _classify_impl(self, query: str, resolved_id: str) -> ClassificationResult:
        try:
            result = self._engine.predict_detailed(query, resolved_id)
            try:
                validate_query_type_label(result.query_type)
            except ValueError as e:
                record_classifier_call("error", resolved_id)
                raise ClassificationError(f"Invalid classifier output: {e}") from e
            record_classifier_call("success", resolved_id)
            return result
        except ClassificationError:
            raise
        except RuntimeError as e:
            record_classifier_call("error", resolved_id)
            self.logger.exception("Model not ready: %s", e)
            raise ClassificationError("Model not available") from e
        except FileNotFoundError as e:
            record_classifier_call("error", resolved_id)
            raise ModelNotFoundError(resolved_id) from e
        except Exception as e:
            record_classifier_call("error", resolved_id)
            self.logger.exception("Classification failed: %s", e)
            raise ClassificationError("Classification failed") from e
