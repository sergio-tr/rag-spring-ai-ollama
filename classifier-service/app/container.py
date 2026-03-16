"""
Dependency container: builds and holds Config, registry, inference, training, evaluation and services.
Single place to wire dependencies; routes obtain services from the container.
"""
from app.config import Config
from app.evaluation.evaluator import EvaluationPipeline
from app.inference.inference_engine import InferenceEngine
from app.inference.model_loader import ModelLoader
from app.registry.model_registry import ModelRegistry
from app.services.classification_service import ClassificationService
from app.services.evaluation_service import EvaluationService
from app.services.model_registry_service import ModelRegistryService
from app.services.training_service import TrainingService
from app.training.trainer import TrainingPipeline


class ServiceContainer:
    """
    Holds all app dependencies and services. Instantiate once (e.g. in create_app)
    and attach to app.state so routes can obtain services via Depends(get_container).
    """

    def __init__(self, config: Config | None = None) -> None:
        self._config = config or Config()
        self._registry = ModelRegistry(self._config)
        self._loader = ModelLoader(self._config, self._registry)
        self._engine = InferenceEngine(self._loader, self._config)
        self._training_pipeline = TrainingPipeline(self._config, self._registry)
        self._evaluation_pipeline = EvaluationPipeline(
            self._config, self._loader, self._registry
        )
        self._classification_service = ClassificationService(self._engine, self._config)
        self._registry_service = ModelRegistryService(self._registry)
        self._training_service = TrainingService(self._training_pipeline)
        self._evaluation_service = EvaluationService(self._config, self._evaluation_pipeline)

    @property
    def config(self) -> Config:
        return self._config

    @property
    def classification_service(self) -> ClassificationService:
        return self._classification_service

    @property
    def model_registry_service(self) -> ModelRegistryService:
        return self._registry_service

    @property
    def training_service(self) -> TrainingService:
        return self._training_service

    @property
    def evaluation_service(self) -> EvaluationService:
        return self._evaluation_service

    @property
    def loader(self) -> ModelLoader:
        return self._loader
