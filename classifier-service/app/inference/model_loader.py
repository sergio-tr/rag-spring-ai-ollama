"""
Model loader: loads Keras model and labels by model_id, with in-memory cache.
Resolves paths via config (default) or registry (trained models).
"""
import tensorflow as tf

from app.base import Loggable
from app.config import Config
from app.registry.model_registry import ModelRegistry


class ModelLoader(Loggable):
    """Loads and caches models by id. Delegates path resolution to config (default) or registry."""

    def __init__(self, config: Config | None = None, registry: ModelRegistry | None = None) -> None:
        self._config = config or Config()
        self._registry = registry or ModelRegistry(self._config)
        self._cache: dict[str, tuple] = {}

    def is_loaded(self, model_id: str) -> bool:
        """Returns True if the model for this id is in cache."""
        return model_id in self._cache

    def load_by_id(self, model_id: str) -> tuple:
        """
        Loads the model for the given id (default from config, others from registry).
        Caches and returns (model, class_names). Raises FileNotFoundError if not found.
        """
        if model_id == self._config.DEFAULT_MODEL_TAG:
            return self._load_default()
        if model_id in self._cache:
            return self._cache[model_id]
        paths = self._registry.get_model_paths(model_id)
        if not paths:
            raise FileNotFoundError(f"Model '{model_id}' not found in registry")
        model_path, labels_path = paths
        model = tf.keras.models.load_model(model_path)
        with open(labels_path, "r", encoding="utf-8") as f:
            class_names = [line.strip() for line in f.readlines() if line.strip()]
        self._cache[model_id] = (model, class_names)
        return model, class_names

    def _load_default(self) -> tuple:
        """Loads the default model from config paths. Idempotent."""
        if self._config.DEFAULT_MODEL_TAG in self._cache:
            return self._cache[self._config.DEFAULT_MODEL_TAG]
        model_path = self._config.get_default_model_path()
        labels_path = self._config.get_default_labels_path()
        model = tf.keras.models.load_model(model_path)
        with open(labels_path, "r", encoding="utf-8") as f:
            class_names = [line.strip() for line in f.readlines() if line.strip()]
        self._cache[self._config.DEFAULT_MODEL_TAG] = (model, class_names)
        return model, class_names

    def get_model(self, model_id: str):
        """Returns the Keras model for model_id; loads it if not in cache."""
        if model_id not in self._cache:
            self.load_by_id(model_id)
        return self._cache[model_id][0]

    def get_class_names(self, model_id: str) -> list[str]:
        """Returns the list of class labels for model_id; loads if not in cache."""
        if model_id not in self._cache:
            self.load_by_id(model_id)
        return self._cache[model_id][1]
