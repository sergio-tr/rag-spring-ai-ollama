"""Probability estimation for sklearn pipelines used in classification serving."""
from __future__ import annotations

import numpy as np
from sklearn.pipeline import Pipeline


def predict_proba(pipeline: Pipeline, text: str) -> np.ndarray:
    """
    Returns a probability vector aligned with pipeline.classes_.
    Uses predict_proba when available; otherwise softmax over decision_function scores.
    """
    clf = pipeline.named_steps["clf"]
    vec = pipeline.named_steps["tfidf"]
    features = vec.transform([text])
    if hasattr(clf, "predict_proba"):
        return np.asarray(clf.predict_proba(features)[0], dtype=float)
    if hasattr(clf, "decision_function"):
        scores = np.asarray(clf.decision_function(features)[0], dtype=float)
        if scores.ndim == 0:
            scores = np.array([scores, -scores])
        scores = scores - np.max(scores)
        exp = np.exp(scores)
        total = exp.sum()
        if total <= 0:
            return np.ones(len(exp)) / len(exp)
        return exp / total
    pred = clf.predict(features)[0]
    classes = getattr(clf, "classes_", None)
    if classes is None:
        raise ValueError("Classifier has no classes_ attribute")
    probs = np.zeros(len(classes), dtype=float)
    idx = int(np.where(classes == pred)[0][0])
    probs[idx] = 1.0
    return probs
