#!/usr/bin/env python3
"""Train and register sklearn classifier variants C3/C4 for serving."""
from __future__ import annotations

import argparse
import json
import sys
import tempfile
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report
from sklearn.pipeline import Pipeline
from sklearn.svm import LinearSVC

_ROOT = Path(__file__).resolve().parents[1]
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

from app.config import Config  # noqa: E402
from app.dataset_hygiene import load_classification_dataset  # noqa: E402
from app.query_type_contract import JAVA_QUERY_TYPE_ORDER  # noqa: E402
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


def train_variant(
    variant: str,
    train_path: Path,
    eval_path: Path,
    *,
    model_id: str | None = None,
    install_default: bool = False,
) -> dict:
    if variant not in VARIANTS:
        raise ValueError(f"Unknown variant {variant}; expected one of {list(VARIANTS)}")
    cfg = VARIANTS[variant]
    train_df = load_classification_dataset(str(train_path))
    eval_df = load_classification_dataset(str(eval_path))
    class_names = list(JAVA_QUERY_TYPE_ORDER)
    label_to_idx = {c: i for i, c in enumerate(class_names)}

    train_df = train_df[train_df["QueryType"].astype(str).isin(label_to_idx)]
    eval_df = eval_df[eval_df["QueryType"].astype(str).isin(label_to_idx)]
    X_train = train_df["Question"].astype(str).values
    y_train = train_df["QueryType"].astype(str).values
    X_eval = eval_df["Question"].astype(str).values
    y_eval = eval_df["QueryType"].astype(str).values

    pipe = Pipeline([("tfidf", cfg["vectorizer"]), ("clf", cfg["estimator"])])
    pipe.fit(X_train, y_train)
    y_pred = pipe.predict(X_eval)
    report = classification_report(y_eval, y_pred, labels=class_names, output_dict=True, zero_division=0)
    macro_f1 = float(report.get("macro avg", {}).get("f1-score", 0))

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
        "dataset_filename": train_path.name,
        "metrics": {
            "accuracy": float(report.get("accuracy", 0)),
            "macro_avg_f1": macro_f1,
        },
    }
    registry.register_model(
        resolved_id,
        name=f"sklearn-{variant.lower()}",
        model_path=artifact_tmp,
        labels_path=str(labels_path),
        metadata=metadata,
    )

    if install_default:
        default_dir = Path(config.get_default_model_path()).parent
        default_dir.mkdir(parents=True, exist_ok=True)
        import shutil

        shutil.copy2(artifact_tmp, default_dir / "model.joblib")
        shutil.copy2(str(labels_path), default_dir / "labels.txt")
        meta_out = {
            "name": config.get_default_model_name(),
            "modelType": "sklearn",
            "variant": variant,
            "createdAt": metadata.get("createdAt"),
            "metrics": metadata["metrics"],
        }
        with open(default_dir / "metadata.json", "w", encoding="utf-8") as f:
            json.dump(meta_out, f, indent=2)

    return {
        "modelId": resolved_id,
        "variant": variant,
        "macro_f1": macro_f1,
        "accuracy": float(report.get("accuracy", 0)),
        "train_rows": int(len(train_df)),
        "eval_rows": int(len(eval_df)),
        "install_default": install_default,
    }


def main() -> int:
    data_dir = _ROOT / "data"
    p = argparse.ArgumentParser()
    p.add_argument("--variant", choices=sorted(VARIANTS), default="C3")
    p.add_argument("--train-xlsx", type=Path, default=data_dir / "basic_dataset_qa_clasificacion_clean.xlsx")
    p.add_argument("--eval-xlsx", type=Path, default=data_dir / "evaluation_dataset.xlsx")
    p.add_argument("--model-id", default=None)
    p.add_argument("--install-default", action="store_true")
    args = p.parse_args()
    result = train_variant(
        args.variant,
        args.train_xlsx.resolve(),
        args.eval_xlsx.resolve(),
        model_id=args.model_id,
        install_default=args.install_default,
    )
    print(json.dumps(result, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
