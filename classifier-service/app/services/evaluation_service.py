"""
Evaluation service: runs evaluation for a model (by tag) and returns metrics + images.
Uses default evaluation dataset when no file is provided.
Traced via TracedService.run_traced for classifier.service.evaluate span.
"""
from pathlib import Path

from app.base import TracedService
from app.config import Config
from app.evaluation.evaluator import EvaluationPipeline
from app.evaluation.result import EvaluationResult
from app.exceptions import EvaluationError, ModelNotFoundError
from app.telemetry import record_evaluate_complete


class EvaluationService(TracedService):
    """Orchestrates evaluation: resolves model id and eval dataset path, runs pipeline."""

    def __init__(self, config: Config | None = None, pipeline: EvaluationPipeline | None = None) -> None:
        super().__init__()
        self._config = config or Config()
        self._pipeline = pipeline or EvaluationPipeline(
            config=self._config,
            loader=None,
            registry=None,
        )

    def evaluate(
        self,
        model_id: str | None = None,
        eval_dataset_path: str | None = None,
        include_images: bool = True,
    ) -> EvaluationResult:
        """
        Evaluates the model (by tag). Uses default model and default eval dataset if not provided.
        Raises ModelNotFoundError if model not found; EvaluationError on invalid dataset or runtime error.
        """
        resolved_model_id = (model_id or "").strip() or self._config.get_default_model_id()
        path = (eval_dataset_path or "").strip() or self._config.get_default_eval_dataset_path()
        if not Path(path).exists():
            raise EvaluationError(
                f"Evaluation dataset not found: {path}. Upload a file or place a dataset under DATA_DIR."
            )
        return self.run_traced(
            "classifier.service.evaluate",
            lambda: self._evaluate_impl(
                model_id=resolved_model_id,
                path=path,
                include_images=include_images,
            ),
            input_attrs={
                "model_id": resolved_model_id,
                "include_images": str(include_images),
            },
        )

    def _evaluate_impl(
        self,
        *,
        model_id: str,
        path: str,
        include_images: bool,
    ) -> EvaluationResult:
        try:
            result = self._pipeline.evaluate(
                model_id=model_id,
                eval_dataset_path=path,
                include_images=include_images,
            )
            record_evaluate_complete(model_id)
            return result
        except FileNotFoundError as e:
            raise ModelNotFoundError(model_id) from e
        except ValueError as e:
            raise EvaluationError(str(e)) from e
        except Exception as e:
            self.logger.exception("Evaluation failed: %s", e)
            # Include the original message so callers/tests can decide when to skip
            # (e.g. missing dataset/model, incompatible labels, etc.).
            raise EvaluationError(f"Evaluation failed: {e}") from e
