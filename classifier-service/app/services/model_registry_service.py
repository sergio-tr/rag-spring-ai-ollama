"""
Model registry service: lists models and resolves paths.
Delegates to ModelRegistry; returns domain ModelMetadata list.
"""
from app.base import BaseService
from app.models.model_metadata import ModelMetadata
from app.registry.model_registry import ModelRegistry


class ModelRegistryService(BaseService):
    """Exposes model listing as domain objects."""

    def __init__(self, registry: ModelRegistry) -> None:
        super().__init__()
        self._registry = registry

    def list_models(self) -> list[ModelMetadata]:
        """Returns all available models (default first, then trained) as ModelMetadata."""
        raw = self._registry.list_models()
        return [
            ModelMetadata(
                id=item["id"],
                name=item["name"],
                created_at=item.get("createdAt"),
                metrics=item.get("metrics"),
            )
            for item in raw
        ]
