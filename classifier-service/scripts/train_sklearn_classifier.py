#!/usr/bin/env python3
"""Train and register sklearn classifier variants C3/C4 for serving."""
from __future__ import annotations

import argparse
import json
import shutil
import sys
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import joblib
import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, confusion_matrix
from sklearn.pipeline import Pipeline
from sklearn.svm import LinearSVC

_ROOT = Path(__file__).resolve().parents[1]
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

from app.config import Config  # noqa: E402
from app.dataset_hygiene import (  # noqa: E402
    assert_training_dataset_hygiene,
    load_classification_dataset,
    load_gold_subset_questions,
)
from app.evaluation.evaluator import EvaluationPipeline  # noqa: E402
from app.inference.sklearn_predict import predict_proba  # noqa: E402
from app.query_type_contract import JAVA_QUERY_TYPE_ORDER, canonical_class_order  # noqa: E402
from app.registry.model_registry import ModelRegistry  # noqa: E402

VARIANTS = {
    "C3": {
        "vectorizer": TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), min_df=1),
        "estimator": LinearSVC(),
    },
    "C4": {
        "vectorizer": TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), min_df=1),
        "estimator": LogisticRegression(max_iter=2000, class_weight="balanced"),
    },
}

CANONICAL_GATE_ACCURACY = 0.850
CANONICAL_GATE_MACRO_F1 = 0.850

DEFAULT_GOLD_SUBSET = (
    _ROOT.parent / "rag-service" / "src" / "main" / "resources" / "evaluation" / "gold-subset-v1.json"
)


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat()


def _default_gold_subset_path() -> Path:
    return DEFAULT_GOLD_SUBSET


def _predict_sklearn_indices(pipe: Pipeline, texts: np.ndarray, class_names: list[str]) -> np.ndarray:
    """Match EvaluationPipeline sklearn path: softmax over decision_function when needed."""
    clf_classes = [str(label) for label in pipe.named_steps["clf"].classes_]
    canon_idx = {name: i for i, name in enumerate(class_names)}
    y_pred = np.empty(len(texts), dtype=int)
    for i, text in enumerate(texts):
        probs = predict_proba(pipe, str(text))
        best_local = int(np.argmax(probs))
        label = clf_classes[best_local]
        y_pred[i] = canon_idx.get(label, canon_idx[class_names[0]])
    return y_pred


def _metrics_from_labels(
    y_true_labels: np.ndarray,
    y_pred_indices: np.ndarray,
    class_names: list[str],
) -> dict[str, Any]:
    label_to_idx = {name: i for i, name in enumerate(class_names)}
    y_true = np.array([label_to_idx[str(label)] for label in y_true_labels])
    report = classification_report(
        y_true,
        y_pred_indices,
        labels=list(range(len(class_names))),
        target_names=class_names,
        output_dict=True,
        zero_division=0,
    )
    cm = confusion_matrix(y_true, y_pred_indices, labels=list(range(len(class_names))))
    macro = report.get("macro avg", {})
    return {
        "accuracy": float(report.get("accuracy", 0)),
        "macro_avg_f1": float(macro.get("f1-score", 0)),
        "macro_avg_precision": float(macro.get("precision", 0)),
        "macro_avg_recall": float(macro.get("recall", 0)),
        "classification_report": report,
        "confusion_matrix": cm.tolist(),
        "rows": int(len(y_true_labels)),
        "correct": int((y_true == y_pred_indices).sum()),
    }


def _evaluate_pipe_on_dataframe(
    pipe: Pipeline,
    df: pd.DataFrame,
    class_names: list[str],
) -> dict[str, Any]:
    valid = df["QueryType"].astype(str).isin(class_names)
    filtered = df[valid].copy()
    if filtered.empty:
        raise ValueError("No valid rows after filtering by QueryType labels")
    texts = filtered["Question"].astype(str).values
    y_true = filtered["QueryType"].astype(str).values
    y_pred = _predict_sklearn_indices(pipe, texts, class_names)
    return _metrics_from_labels(y_true, y_pred, class_names)


def _evaluation_result_metrics(result: Any) -> dict[str, Any]:
    report = result.classification_report
    macro = report.get("macro avg", {})
    y_true_count = int(sum(
        float(report.get(name, {}).get("support", 0))
        for name in result.class_names
        if name in report
    ))
    correct = 0
    cm = np.array(result.confusion_matrix)
    for i in range(len(result.class_names)):
        correct += int(cm[i, i])
    return {
        "accuracy": float(report.get("accuracy", 0)),
        "macro_avg_f1": float(macro.get("f1-score", 0)),
        "macro_avg_precision": float(macro.get("precision", 0)),
        "macro_avg_recall": float(macro.get("recall", 0)),
        "classification_report": report,
        "confusion_matrix": result.confusion_matrix,
        "rows": y_true_count,
        "correct": correct,
    }


def _build_default_metadata(
    *,
    config: Config,
    variant: str,
    train_path: Path,
    primary_eval_path: Path,
    primary_metrics: dict[str, Any],
    synthetic_metrics: dict[str, Any],
    gold_metrics: dict[str, Any],
    synthetic_path: Path,
    gold_path: Path,
) -> dict[str, Any]:
    return {
        "name": config.get_default_model_name(),
        "modelType": "sklearn",
        "variant": variant,
        "createdAt": _now_iso(),
        "train_dataset": train_path.name,
        "primary_eval_dataset": primary_eval_path.name,
        "primary_eval": {
            "dataset": primary_eval_path.name,
            "gate": True,
            "metrics": {
                "accuracy": primary_metrics["accuracy"],
                "macro_avg_f1": primary_metrics["macro_avg_f1"],
                "macro_avg_precision": primary_metrics["macro_avg_precision"],
                "macro_avg_recall": primary_metrics["macro_avg_recall"],
                "rows": primary_metrics["rows"],
                "correct": primary_metrics["correct"],
            },
        },
        "synthetic_test": {
            "dataset": synthetic_path.name,
            "gate": False,
            "report_only": True,
            "metrics": {
                "accuracy": synthetic_metrics["accuracy"],
                "macro_avg_f1": synthetic_metrics["macro_avg_f1"],
                "rows": synthetic_metrics["rows"],
                "correct": synthetic_metrics["correct"],
            },
        },
        "gold_subset": {
            "dataset": gold_path.name,
            "gate": False,
            "probe": True,
            "metrics": {
                "accuracy": gold_metrics["accuracy"],
                "rows": gold_metrics["rows"],
                "correct": gold_metrics["correct"],
            },
        },
    }


def train_variant(
    variant: str,
    train_path: Path,
    eval_path: Path,
    *,
    model_id: str | None = None,
    install_default: bool = False,
    gold_path: Path | None = None,
    synthetic_test_path: Path | None = None,
) -> dict[str, Any]:
    if variant not in VARIANTS:
        raise ValueError(f"Unknown variant {variant}; expected one of {list(VARIANTS)}")
    cfg = VARIANTS[variant]
    class_names = list(JAVA_QUERY_TYPE_ORDER)

    train_df = load_classification_dataset(str(train_path))
    assert_training_dataset_hygiene(
        train_df,
        eval_path=str(eval_path),
        gold_path=str(gold_path) if gold_path else None,
    )

    train_df = train_df[train_df["QueryType"].astype(str).isin(class_names)]
    if train_df.empty:
        raise ValueError("No training rows left after filtering by Java QueryType labels")

    X_train = train_df["Question"].astype(str).values
    y_train = train_df["QueryType"].astype(str).values

    pipe = Pipeline([("tfidf", cfg["vectorizer"]), ("clf", cfg["estimator"])])
    pipe.fit(X_train, y_train)

    eval_df = load_classification_dataset(str(eval_path))
    eval_df = eval_df[eval_df["QueryType"].astype(str).isin(class_names)]
    quick_metrics = _evaluate_pipe_on_dataframe(pipe, eval_df, class_names)

    config = Config()
    registry = ModelRegistry(config)
    resolved_id = model_id or registry.create_new_model_id()
    labels_path = Path(tempfile.mkdtemp()) / "labels.txt"
    labels_path.write_text("\n".join(class_names) + "\n", encoding="utf-8")
    with tempfile.NamedTemporaryFile(suffix=".joblib", delete=False) as tmp:
        joblib.dump(pipe, tmp.name)
        artifact_tmp = tmp.name

    metadata = {
        "modelType": "sklearn",
        "variant": variant,
        "train_dataset": train_path.name,
        "primary_eval_dataset": eval_path.name,
        "metrics": {
            "accuracy": quick_metrics["accuracy"],
            "macro_avg_f1": quick_metrics["macro_avg_f1"],
        },
    }
    registry.register_model(
        resolved_id,
        name=f"sklearn-{variant.lower()}",
        model_path=artifact_tmp,
        labels_path=str(labels_path),
        metadata=metadata,
    )

    result: dict[str, Any] = {
        "modelId": resolved_id,
        "variant": variant,
        "macro_f1": quick_metrics["macro_avg_f1"],
        "accuracy": quick_metrics["accuracy"],
        "train_rows": int(len(train_df)),
        "eval_rows": int(len(eval_df)),
        "install_default": install_default,
    }

    if install_default:
        resolved_gold = gold_path or _default_gold_subset_path()
        resolved_synthetic = synthetic_test_path or (Path(config.get_data_dir()) / "classifier_test_balanced.csv")

        default_dir = Path(config.get_default_model_path()).parent
        default_dir.mkdir(parents=True, exist_ok=True)

        pipeline = EvaluationPipeline(config=config)
        primary_result = pipeline.evaluate(
            resolved_id,
            str(eval_path.resolve()),
            include_images=False,
        )
        primary_metrics = _evaluation_result_metrics(primary_result)

        if (
            primary_metrics["accuracy"] <= CANONICAL_GATE_ACCURACY
            or primary_metrics["macro_avg_f1"] <= CANONICAL_GATE_MACRO_F1
        ):
            raise ValueError(
                "Canonical gate not passed; refusing --install-default. "
                f"accuracy={primary_metrics['accuracy']:.4f} (need >{CANONICAL_GATE_ACCURACY}), "
                f"macro_f1={primary_metrics['macro_avg_f1']:.4f} (need >{CANONICAL_GATE_MACRO_F1})"
            )

        backup_dir = default_dir / "backups" / datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        backup_dir.mkdir(parents=True, exist_ok=True)
        for name in ("metadata.json", "model.joblib", "labels.txt"):
            src = default_dir / name
            if src.is_file():
                shutil.copy2(src, backup_dir / name)

        shutil.copy2(artifact_tmp, default_dir / "model.joblib")
        shutil.copy2(str(labels_path), default_dir / "labels.txt")

        synthetic_df = load_classification_dataset(str(resolved_synthetic))
        synthetic_metrics = _evaluate_pipe_on_dataframe(pipe, synthetic_df, class_names)

        gold_df = load_gold_subset_questions(str(resolved_gold))
        gold_metrics = _evaluate_pipe_on_dataframe(pipe, gold_df, class_names)

        meta_out = _build_default_metadata(
            config=config,
            variant=variant,
            train_path=train_path,
            primary_eval_path=eval_path,
            primary_metrics=primary_metrics,
            synthetic_metrics=synthetic_metrics,
            gold_metrics=gold_metrics,
            synthetic_path=resolved_synthetic,
            gold_path=resolved_gold,
        )
        with open(default_dir / "metadata.json", "w", encoding="utf-8") as f:
            json.dump(meta_out, f, indent=2)

        result.update(
            {
                "default_metadata": meta_out,
                "primary_eval": primary_metrics,
                "synthetic_test": synthetic_metrics,
                "gold_subset": gold_metrics,
                "backup_dir": str(backup_dir),
            }
        )

    return result


def main() -> int:
    data_dir = _ROOT / "data"
    p = argparse.ArgumentParser()
    p.add_argument("--variant", choices=sorted(VARIANTS), default="C3")
    p.add_argument(
        "--train",
        type=Path,
        default=data_dir / "basic_dataset_qa_clasificacion_final.xlsx",
        help="Training dataset (.csv or .xlsx with Question/QueryType columns).",
    )
    p.add_argument(
        "--eval",
        type=Path,
        default=data_dir / "evaluation_dataset.xlsx",
        help="Primary held-out evaluation dataset (.xlsx).",
    )
    p.add_argument(
        "--gold-subset",
        type=Path,
        default=_default_gold_subset_path(),
        help="Gold-subset manifest for probe metrics (report only).",
    )
    p.add_argument(
        "--synthetic-test",
        type=Path,
        default=data_dir / "classifier_test_balanced.csv",
        help="Synthetic balanced test CSV (report only, not a gate).",
    )
    p.add_argument("--model-id", default=None)
    p.add_argument("--install-default", action="store_true")
    args = p.parse_args()
    result = train_variant(
        args.variant,
        args.train.resolve(),
        args.eval.resolve(),
        model_id=args.model_id,
        install_default=args.install_default,
        gold_path=args.gold_subset.resolve(),
        synthetic_test_path=args.synthetic_test.resolve(),
    )
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
