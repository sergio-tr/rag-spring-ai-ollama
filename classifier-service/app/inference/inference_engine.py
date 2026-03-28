"""
Inference engine: runs prediction for a query using a loaded model.
No HTTP; depends on ModelLoader and Config for default model id.
"""
import numpy as np
import tensorflow as tf

from app.base import Loggable
from app.config import Config
from app.inference.model_loader import ModelLoader


class InferenceEngine(Loggable):
    """Predicts query type from text using a loaded model and its labels."""

    def __init__(self, loader: ModelLoader, config: Config | None = None) -> None:
        self._loader = loader
        self._config = config or Config()

    def predict(self, query: str, model_id: str | None = None) -> str:
        """
        Predicts the query type for the given text.
        model_id: model to use (None => default). Returns the class label (e.g. COUNT_DOCUMENTS).
        Raises RuntimeError if model is not loaded; FileNotFoundError if model not found.
        """
        mid = model_id or self._config.get_default_model_id()
        if not self._loader.is_loaded(mid):
            self._loader.load_by_id(mid)
        model = self._loader.get_model(mid)
        class_names = self._loader.get_class_names(mid)
        pred = model.predict(tf.constant([query]))[0]
        idx = int(np.argmax(pred))
        return class_names[idx]
