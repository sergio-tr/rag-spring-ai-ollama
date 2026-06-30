"""
Inference engine: runs prediction for a query using a loaded Keras or sklearn model.
No HTTP; depends on ModelLoader and Config for default model id.
"""
import numpy as np

from app.base import Loggable
from app.config import Config
from app.inference.model_loader import ModelLoader
from app.inference.sklearn_predict import predict_proba
from app.models.classification_result import ClassificationResult, TopPrediction
from app.query_type_contract import label_set_hash
from app.tensorflow_support import require_tensorflow


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
        Predicts query type with confidence and optional top-k predictions.
        """
        mid = model_id or self._config.get_default_model_id()
        loaded = self._loader.get_loaded_model(mid)
        class_names = loaded.class_names
        if loaded.model_type == "sklearn":
            probs = predict_proba(loaded.artifact, query)
            clf_classes = list(loaded.artifact.named_steps["clf"].classes_)
            canon_idx = {name: i for i, name in enumerate(class_names)}
            aligned = np.zeros(len(class_names), dtype=float)
            for i, label in enumerate(clf_classes):
                idx = canon_idx.get(str(label))
                if idx is not None:
                    aligned[idx] = float(probs[i])
            probs = aligned
        else:
            tf = require_tensorflow()
            probs = loaded.artifact.predict(tf.constant([query]), verbose=0)[0]
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
