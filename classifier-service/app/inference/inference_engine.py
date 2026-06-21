"""
Inference engine: runs prediction for a query using a loaded model.
No HTTP; depends on ModelLoader and Config for default model id.
"""
import numpy as np
import tensorflow as tf

from app.base import Loggable
from app.config import Config
from app.inference.model_loader import ModelLoader
from app.models.classification_result import ClassificationResult, TopPrediction
from app.query_type_contract import label_set_hash


class InferenceEngine(Loggable):
    """Predicts query type from text using a loaded model and its labels."""

    _TOP_K = 3

    def __init__(self, loader: ModelLoader, config: Config | None = None) -> None:
        self._loader = loader
        self._config = config or Config()

    def predict(self, query: str, model_id: str | None = None) -> str:
        """Returns the argmax class label for the given text."""
        return self.predict_detailed(query, model_id).query_type

    def predict_detailed(self, query: str, model_id: str | None = None) -> ClassificationResult:
        """
        Predicts query type with softmax confidence and optional top-k predictions.
        """
        mid = model_id or self._config.get_default_model_id()
        if not self._loader.is_loaded(mid):
            self._loader.load_by_id(mid)
        model = self._loader.get_model(mid)
        class_names = self._loader.get_class_names(mid)
        probs = model.predict(tf.constant([query]), verbose=0)[0]
        idx = int(np.argmax(probs))
        confidence = float(probs[idx])
        top_predictions = self._top_predictions(probs, class_names)
        return ClassificationResult(
            query_type=class_names[idx],
            confidence=confidence,
            top_predictions=top_predictions,
            label_set_hash=label_set_hash(class_names),
        )

    def _top_predictions(self, probs: np.ndarray, class_names: list[str]) -> tuple[TopPrediction, ...]:
        if len(class_names) == 0:
            return ()
        k = min(self._TOP_K, len(class_names))
        indices = np.argsort(probs)[-k:][::-1]
        return tuple(
            TopPrediction(query_type=class_names[int(i)], confidence=float(probs[int(i)])) for i in indices
        )
