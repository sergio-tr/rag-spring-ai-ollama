"""
Training service: runs the training pipeline and returns a TrainingResult.
Handles file validation and maps pipeline errors to service exceptions.
Traced via TracedService.run_traced for classifier.service.train span.
"""
from app.base import TracedService
from app.exceptions import TrainingError, ValidationError
from app.models.training_result import TrainingResult
from app.telemetry import record_train_complete
from app.training.trainer import TrainingPipeline
import traceback


class TrainingService(TracedService):
    """Orchestrates training: validates input, runs pipeline, returns domain result."""

    def __init__(self, pipeline: TrainingPipeline) -> None:
        super().__init__()
        self._pipeline = pipeline

    def train(
        self,
        dataset_path: str,
        model_name: str,
        class_names: list[str] | None = None,
        epochs: int = 50,
        batch_size: int = 8,
        owner_id: str | None = None,
    ) -> TrainingResult:
        """
        Trains a new model from the dataset file. model_name is the label for the model.
        If class_names is provided, only those QueryTypes are used (order preserved).
        Raises ValidationError for invalid inputs; TrainingError on pipeline failure.
        """
        if not model_name or not model_name.strip():
            raise ValidationError("model_name must be non-empty")
        return self.run_traced(
            "classifier.service.train",
            lambda: self._train_impl(
                dataset_path=dataset_path,
                model_name=model_name.strip(),
                class_names=class_names,
                epochs=epochs,
                batch_size=batch_size,
                owner_id=owner_id,
            ),
            input_attrs={
                "model_name": model_name.strip()[:128],
                "epochs": str(epochs),
                "batch_size": str(batch_size),
            },
            output_attr="model_id",
            output_value_fn=lambda r: r.model_id,
        )

    def _train_impl(
        self,
        *,
        dataset_path: str,
        model_name: str,
        class_names: list[str] | None,
        epochs: int,
        batch_size: int,
        owner_id: str | None,
    ) -> TrainingResult:
        try:
            result = self._pipeline.train(
                dataset_path=dataset_path,
                model_name=model_name,
                class_names=class_names,
                epochs=epochs,
                batch_size=batch_size,
                owner_id=owner_id,
            )
            out = TrainingResult(
                model_id=result["model_id"],
                name=result["name"],
                metrics=result["metrics"],
            )
            record_train_complete(out.model_id)
            return out
        except ValueError as e:
            msg = str(e)
            # Default: ValueError from the pipeline is treated as a validation error (400).
            # Some environments may throw ValueError for runtime/library issues; map those to 500
            # so API tests can skip when training isn't supported.
            runtime_markers = (
                "tensorflow",
                "keras",
                "cuda",
                "cudnn",
                "graph execution",
                "resourceexhausted",
                "out of memory",
                "numpy",
                "ufunc",
            )
            tb = "".join(traceback.format_exception(type(e), e, e.__traceback__)).lower()
            if any(m in msg.lower() for m in runtime_markers) or any(m in tb for m in runtime_markers):
                raise TrainingError("Training failed") from e
            raise ValidationError(msg) from e
        except Exception as e:
            self.logger.exception("Training failed: %s", e)
            raise TrainingError("Training failed") from e
