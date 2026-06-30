#!/usr/bin/env python3
"""
Evaluate classifier candidates C0–C6 on held-out evaluation_dataset.xlsx.

C0: current default (unretrained)
C1: Keras pipeline train-only
C2: TF-IDF word + LinearSVC
C3: TF-IDF char_wb 3-5 + LinearSVC
C4: TF-IDF char_wb + LogisticRegression balanced
C5: Keras pipeline + class_weight
C6: deterministic rules + ML fallback (C1 base)
"""
from __future__ import annotations

import argparse
import json
import sys
import tempfile
from pathlib import Path
from typing import Any, Callable

import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, confusion_matrix
from sklearn.pipeline import Pipeline
from sklearn.svm import LinearSVC

_ROOT = Path(__file__).resolve().parents[1]
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

from app.config import Config  # noqa: E402
from app.dataset_hygiene import load_classification_dataset  # noqa: E402
from app.deterministic_resolver import predict_with_ml_fallback  # noqa: E402
from app.inference.model_loader import ModelLoader  # noqa: E402
from app.query_type_contract import JAVA_QUERY_TYPE_ORDER, canonical_class_order  # noqa: E402
from app.training.trainer import TrainingPipeline  # noqa: E402


def _load_eval(eval_path: Path) -> tuple[np.ndarray, np.ndarray, list[str]]:
    df = load_classification_dataset(str(eval_path))
    class_names = list(JAVA_QUERY_TYPE_ORDER)
    label_to_idx = {c: i for i, c in enumerate(class_names)}
    valid = df["QueryType"].astype(str).isin(label_to_idx)
    df = df[valid].copy()
    X = df["Question"].astype(str).values
    y = np.array([label_to_idx[c] for c in df["QueryType"].astype(str)])
    return X, y, class_names


def _metrics(y_true: np.ndarray, y_pred: np.ndarray, class_names: list[str]) -> dict[str, Any]:
    report = classification_report(
        y_true, y_pred, target_names=class_names, output_dict=True, zero_division=0
    )
    cm = confusion_matrix(y_true, y_pred, labels=list(range(len(class_names))))
    return {
        "accuracy": float(report.get("accuracy", 0)),
        "macro_f1": float(report.get("macro avg", {}).get("f1-score", 0)),
        "classification_report": report,
        "confusion_matrix": cm.tolist(),
        "class_names": class_names,
    }


def _predict_keras_model(model, class_names: list[str], texts: np.ndarray) -> np.ndarray:
    probs = model.predict(tf.constant([str(x) for x in texts]), verbose=0)
    idx = np.argmax(probs, axis=1)
    canon_idx = {name: i for i, name in enumerate(JAVA_QUERY_TYPE_ORDER)}
    return np.array([canon_idx[class_names[int(i)]] for i in idx])


def eval_c0_default(loader: ModelLoader, X: np.ndarray, y: np.ndarray, class_names: list[str]) -> dict:
    loader.load_by_id("default")
    model = loader.get_model("default")
    model_class_names = loader.get_class_names("default")
    y_pred = _predict_keras_model(model, model_class_names, X)
    return _metrics(y, y_pred, class_names)


def _train_keras(
    train_path: Path,
    *,
    class_weight: bool = False,
    output_dir: Path | None = None,
) -> tuple[Any, list[str]]:
    config = Config()
    pipeline = TrainingPipeline(config=config)
    result = pipeline.train(
        dataset_path=str(train_path),
        model_name="candidate",
        output_dir=str(output_dir) if output_dir else None,
        class_names=list(JAVA_QUERY_TYPE_ORDER),
        epochs=40,
        batch_size=8,
        class_weight=class_weight,
    )
    model = tf.keras.models.load_model(result["model_path"])
    labels = list(JAVA_QUERY_TYPE_ORDER)
    return model, labels


def eval_keras_candidate(
    train_path: Path,
    X: np.ndarray,
    y: np.ndarray,
    class_names: list[str],
    *,
    class_weight: bool = False,
    postprocess: Callable[[str, str], str] | None = None,
) -> dict:
    with tempfile.TemporaryDirectory() as tmp:
        model, model_labels = _train_keras(train_path, class_weight=class_weight, output_dir=Path(tmp))
        y_pred = _predict_keras_model(model, model_labels, X)
        if postprocess:
            texts = X.tolist()
            adjusted = []
            for text, pred_idx in zip(texts, y_pred):
                ml_label = class_names[pred_idx]
                final = postprocess(text, ml_label)
                adjusted.append(class_names.index(final))
            y_pred = np.array(adjusted)
    return _metrics(y, y_pred, class_names)


def eval_sklearn_candidate(
    train_path: Path,
    X: np.ndarray,
    y: np.ndarray,
    class_names: list[str],
    *,
    vectorizer: TfidfVectorizer,
    estimator,
) -> dict:
    train_df = load_classification_dataset(str(train_path))
    label_to_idx = {c: i for i, c in enumerate(class_names)}
    train_df = train_df[train_df["QueryType"].astype(str).isin(label_to_idx)]
    X_train = train_df["Question"].astype(str).values
    y_train = np.array([label_to_idx[c] for c in train_df["QueryType"].astype(str)])
    pipe = Pipeline([("tfidf", vectorizer), ("clf", estimator)])
    pipe.fit(X_train, y_train)
    y_pred = pipe.predict(X)
    return _metrics(y, y_pred, class_names)


def run_matrix(train_path: Path, eval_path: Path) -> dict[str, Any]:
    X, y, class_names = _load_eval(eval_path)
    loader = ModelLoader(Config())
    results: dict[str, Any] = {
        "train_path": str(train_path),
        "eval_path": str(eval_path),
        "eval_rows": int(len(y)),
        "train_rows": int(len(load_classification_dataset(str(train_path)))),
        "candidates": {},
    }

    print("C0: default unretrained...")
    results["candidates"]["C0"] = {
        "description": "current default unretrained",
        **eval_c0_default(loader, X, y, class_names),
    }

    print("C1: Keras train-only...")
    results["candidates"]["C1"] = {
        "description": "Keras pipeline retrained train-only",
        **eval_keras_candidate(train_path, X, y, class_names),
    }

    print("C2: TF-IDF word + LinearSVC...")
    results["candidates"]["C2"] = {
        "description": "TF-IDF word + LinearSVC",
        **eval_sklearn_candidate(
            train_path,
            X,
            y,
            class_names,
            vectorizer=TfidfVectorizer(ngram_range=(1, 2), min_df=1, sublinear_tf=True),
            estimator=LinearSVC(),
        ),
    }

    print("C3: TF-IDF char_wb 3-5 + LinearSVC...")
    results["candidates"]["C3"] = {
        "description": "TF-IDF char_wb 3-5 + LinearSVC",
        **eval_sklearn_candidate(
            train_path,
            X,
            y,
            class_names,
            vectorizer=TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), min_df=1),
            estimator=LinearSVC(),
        ),
    }

    print("C4: TF-IDF char_wb + LogisticRegression balanced...")
    results["candidates"]["C4"] = {
        "description": "TF-IDF char_wb + LogisticRegression balanced",
        **eval_sklearn_candidate(
            train_path,
            X,
            y,
            class_names,
            vectorizer=TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), min_df=1),
            estimator=LogisticRegression(max_iter=2000, class_weight="balanced"),
        ),
    }

    print("C5: Keras + class_weight...")
    results["candidates"]["C5"] = {
        "description": "Keras pipeline + balanced class_weight",
        **eval_keras_candidate(train_path, X, y, class_names, class_weight=True),
    }

    print("C6: deterministic rules + ML fallback...")
    results["candidates"]["C6"] = {
        "description": "deterministic rules + Keras ML fallback",
        **eval_keras_candidate(
            train_path,
            X,
            y,
            class_names,
            postprocess=predict_with_ml_fallback,
        ),
    }

    best_id = max(
        results["candidates"],
        key=lambda k: results["candidates"][k]["macro_f1"],
    )
    results["best_candidate"] = best_id
    results["best_macro_f1"] = results["candidates"][best_id]["macro_f1"]
    return results


def error_analysis(
    train_path: Path,
    eval_path: Path,
    candidate_key: str,
    results: dict[str, Any],
) -> list[dict[str, Any]]:
    """Per-row errors for a candidate (re-runs prediction for C0/C1 only for evidence)."""
    X, y, class_names = _load_eval(eval_path)
    if candidate_key == "C0":
        loader = ModelLoader(Config())
        loader.load_by_id("default")
        model = loader.get_model("default")
        model_labels = loader.get_class_names("default")
        y_pred = _predict_keras_model(model, model_labels, X)
    elif candidate_key in ("C1", "C5", "C6"):
        cw = candidate_key == "C5"
        post = predict_with_ml_fallback if candidate_key == "C6" else None
        with tempfile.TemporaryDirectory() as tmp:
            model, model_labels = _train_keras(train_path, class_weight=cw, output_dir=Path(tmp))
            y_pred = _predict_keras_model(model, model_labels, X)
            if post:
                y_pred = np.array(
                    [
                        class_names.index(post(t, class_names[p]))
                        for t, p in zip(X.tolist(), y_pred)
                    ]
                )
    else:
        return []

    errors = []
    for i, (text, true_i, pred_i) in enumerate(zip(X, y, y_pred)):
        if true_i != pred_i:
            errors.append(
                {
                    "index": i,
                    "question": str(text),
                    "true": class_names[true_i],
                    "predicted": class_names[pred_i],
                }
            )
    return errors


def main() -> int:
    p = argparse.ArgumentParser()
    config = Config()
    data_dir = Path(config.get_data_dir())
    p.add_argument(
        "--train-xlsx",
        type=Path,
        default=data_dir / "basic_dataset_qa_clasificacion_clean.xlsx",
    )
    p.add_argument("--eval-xlsx", type=Path, default=data_dir / "evaluation_dataset.xlsx")
    p.add_argument(
        "--output-json",
        type=Path,
        default=_ROOT.parent / ".cursor/evidence/classifier-retrain-failure-analysis-20250629/candidate_matrix_results.json",
    )
    args = p.parse_args()

    results = run_matrix(args.train_xlsx.resolve(), args.eval_xlsx.resolve())
    results["error_analysis"] = {
        "C0": error_analysis(args.train_xlsx, args.eval_xlsx, "C0", results),
        "best": error_analysis(
            args.train_xlsx,
            args.eval_xlsx,
            results["best_candidate"],
            results,
        ),
    }
    args.output_json.parent.mkdir(parents=True, exist_ok=True)
    args.output_json.write_text(json.dumps(results, ensure_ascii=False, indent=2), encoding="utf-8")
    print(json.dumps(
        {
            "best": results["best_candidate"],
            "macro_f1": results["best_macro_f1"],
            "output": str(args.output_json),
        },
        indent=2,
    ))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
