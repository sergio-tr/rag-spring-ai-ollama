"""
Training service: runs the training pipeline and returns a TrainingResult.
Handles file validation and maps pipeline errors to service exceptions.
"""
from app.base import BaseService
from app.exceptions import TrainingError, ValidationError
from app.models.training_result import TrainingResult
from app.training.trainer import TrainingPipeline


class TrainingService(BaseService):
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
    ) -> TrainingResult:
        """
        Trains a new model from the dataset file. model_name is the label for the model.
        If class_names is provided, only those QueryTypes are used (order preserved).
        Raises ValidationError for invalid inputs; TrainingError on pipeline failure.
        """
        if not model_name or not model_name.strip():
            raise ValidationError("model_name must be non-empty")
        try:
            result = self._pipeline.train(
                dataset_path=dataset_path,
                model_name=model_name.strip(),
                class_names=class_names,
                epochs=epochs,
                batch_size=batch_size,
            )
            return TrainingResult(
                model_id=result["model_id"],
                name=result["name"],
                metrics=result["metrics"],
            )
        except ValueError as e:
            raise ValidationError(str(e)) from e
        except Exception as e:
            self.logger.exception("Training failed: %s", e)
            raise TrainingError("Training failed") from e
