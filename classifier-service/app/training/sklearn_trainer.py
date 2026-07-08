"""
Sklearn training pipeline for HTTP /train (no TensorFlow required).
"""
from __future__ import annotations

import os
import tempfile
from pathlib import Path

import joblib
import numpy as np
import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report
from sklearn.model_selection import train_test_split
from sklearn.pipeline import Pipeline

from app.base import Loggable
from app.config import Config
from app.dataset_columns import normalize_excel_classification_columns
from app.inference.sklearn_predict import predict_proba
from app.query_type_contract import JAVA_QUERY_TYPES, LEGACY_TRAINING_LABEL_MAP, validate_query_type_label
from app.registry.model_registry import ModelRegistry, SKLEARN_FILENAME, LABELS_FILENAME

RESERVED_MODEL_ID = "default"


class SklearnTrainingPipeline(Loggable):
    """Trains a sklearn Pipeline from Excel (Question, QueryType) and registers artifacts."""

    def __init__(self, config: Config | None = None, registry: ModelRegistry | None = None) -> None:
        self._config = config or Config()
        self._registry = registry or ModelRegistry(self._config)

    def train(
        self,
        dataset_path: str,
        model_name: str,
        output_dir: str | None = None,
        class_names: list[str] | None = None,
        epochs: int = 50,
        batch_size: int = 8,
        validation_fraction: float = 0.2,
        owner_id: str | None = None,
        **_kwargs,
    ) -> dict:
        normalized_name = (model_name or "").strip()
        if not normalized_name:
            raise ValueError("model_name must be non-empty")
        if normalized_name.lower() == RESERVED_MODEL_ID:
            raise ValueError("Model name 'default' is reserved; choose a custom tag")

        df = normalize_excel_classification_columns(pd.read_excel(dataset_path))
        if "Question" not in df.columns or "QueryType" not in df.columns:
            raise ValueError("Dataset must have columns 'Question' and 'QueryType'")

        df["QueryType"] = (
            df["QueryType"]
            .astype(str)
            .str.strip()
            .replace(LEGACY_TRAINING_LABEL_MAP)
        )
        unknown = sorted({v for v in df["QueryType"].unique() if v not in JAVA_QUERY_TYPES})
        if unknown:
            raise ValueError(f"Dataset QueryType values not in Java enum: {unknown}")
        for label in df["QueryType"].unique():
            validate_query_type_label(str(label))

        if class_names:
            df = df[df["QueryType"].astype(str).isin(class_names)].copy()
            if df.empty:
                raise ValueError("No rows left after filtering by class_names")
            labels = list(class_names)
        else:
            labels = sorted(df["QueryType"].astype(str).unique().tolist())

        X = df["Question"].astype(str).to_numpy()
        y = df["QueryType"].astype(str).to_numpy()

        pipe = Pipeline([
            ("tfidf", TfidfVectorizer(analyzer="char_wb", ngram_range=(3, 5), min_df=1)),
            ("clf", LogisticRegression(max_iter=2000, class_weight="balanced")),
        ])
        pipe.fit(X, y)

        try:
            x_train, x_val, y_train, y_val = train_test_split(
                X, y, test_size=validation_fraction, random_state=42, stratify=y
            )
        except ValueError:
            x_train, x_val, y_train, y_val = train_test_split(
                X, y, test_size=validation_fraction, random_state=42
            )

        y_pred = self._predict_indices(pipe, x_val, labels)
        label_to_idx = {name: i for i, name in enumerate(labels)}
        y_true_idx = np.array([label_to_idx[str(v)] for v in y_val])
        report = classification_report(
            y_true_idx,
            y_pred,
            labels=list(range(len(labels))),
            target_names=labels,
            output_dict=True,
            zero_division=0,
        )
        accuracy = float(report.get("accuracy", 0))
        macro_f1 = 0.0
        if "macro avg" in report:
            macro_f1 = float(report["macro avg"].get("f1-score", 0))

        model_id = self._registry.create_new_model_id()
        if model_id.lower() == RESERVED_MODEL_ID:
            raise ValueError("Generated model id is reserved; retry training")
        base = Path(output_dir or self._config.get_models_dir())
        out_path = base / model_id
        if out_path.name.lower() == RESERVED_MODEL_ID:
            raise ValueError("Cannot write trained model under reserved id 'default'")

        out_path.mkdir(parents=True, exist_ok=True)
        model_file = out_path / SKLEARN_FILENAME
        labels_file = out_path / LABELS_FILENAME

        with open(labels_file, "w", encoding="utf-8") as f:
            f.write("\n".join(labels))

        with tempfile.NamedTemporaryFile(suffix=".joblib", delete=False) as tmp:
            joblib.dump(pipe, tmp.name)
            artifact_tmp = tmp.name

        metadata = {
            "name": normalized_name,
            "modelType": "sklearn",
            "variant": "web",
            "epochs": epochs,
            "batch_size": batch_size,
            "dataset_filename": os.path.basename(dataset_path),
            "metrics": {
                "accuracy": accuracy,
                "macro_avg_f1": macro_f1,
            },
        }
        if owner_id:
            metadata["ownerId"] = owner_id

        self._registry.register_model(
            model_id=model_id,
            name=normalized_name,
            model_path=artifact_tmp,
            labels_path=str(labels_file),
            metadata=metadata,
        )
        Path(artifact_tmp).unlink(missing_ok=True)

        return {
            "model_id": model_id,
            "name": normalized_name,
            "model_path": str(model_file),
            "labels_path": str(labels_file),
            "metrics": metadata["metrics"],
        }

    @staticmethod
    def _predict_indices(pipe: Pipeline, texts: np.ndarray, class_names: list[str]) -> np.ndarray:
        clf_classes = [str(label) for label in pipe.named_steps["clf"].classes_]
        canon_idx = {name: i for i, name in enumerate(class_names)}
        y_pred = np.empty(len(texts), dtype=int)
        for i, text in enumerate(texts):
            probs = predict_proba(pipe, str(text))
            best_local = int(np.argmax(probs))
            label = clf_classes[best_local]
            y_pred[i] = canon_idx.get(label, canon_idx[class_names[0]])
        return y_pred
