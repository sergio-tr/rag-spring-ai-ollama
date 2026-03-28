"""
Training pipeline: reads Excel dataset (Question, QueryType), trains Keras model, saves artifacts.
Parameterized by epochs, batch_size, etc. Delegates registration to ModelRegistry.
"""
import os
from pathlib import Path

import numpy as np
import pandas as pd
import tensorflow as tf
from sklearn.metrics import classification_report
from sklearn.model_selection import train_test_split

from app.base import Loggable
from app.config import Config
from app.dataset_columns import normalize_excel_classification_columns
from app.registry.model_registry import ModelRegistry

MODEL_FILENAME = "model.keras"
LABELS_FILENAME = "labels.txt"


class TrainingPipeline(Loggable):
    """Runs the full training: load dataset, train, save model and labels, register."""

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
        max_tokens: int = 5000,
        sequence_length: int = 40,
        early_stopping_patience: int = 5,
    ) -> dict:
        """
        Trains a classifier from an Excel file with columns Question and QueryType
        (Spanish 'Pregunta' is normalized to 'Question', same as evaluation).
        If class_names is provided, only those labels are used (order preserved); otherwise derived from data.
        Saves model, labels and metadata under output_dir or MODELS_DIR/{new_id}/.
        Returns dict with model_id, name, paths, metrics.
        """
        df = normalize_excel_classification_columns(pd.read_excel(dataset_path))
        if "Question" not in df.columns or "QueryType" not in df.columns:
            raise ValueError("Dataset must have columns 'Question' and 'QueryType'")
        if class_names:
            df = df[df["QueryType"].astype(str).isin(class_names)].copy()
            if df.empty:
                raise ValueError("No rows left after filtering by class_names")
            label_to_idx = {c: i for i, c in enumerate(class_names)}
            y_idx = df["QueryType"].astype(str).map(label_to_idx).values
            y = tf.keras.utils.to_categorical(y_idx, num_classes=len(class_names))
        else:
            y_raw = pd.get_dummies(df["QueryType"])
            y = y_raw.values
            class_names = y_raw.columns.tolist()
        X = df["Question"].astype(str).values

        try:
            X_train, X_val, y_train, y_val = train_test_split(
                X, y, test_size=validation_fraction, random_state=42, stratify=y
            )
        except ValueError:
            # Too few samples per class for stratification (e.g. tiny test datasets).
            X_train, X_val, y_train, y_val = train_test_split(
                X, y, test_size=validation_fraction, random_state=42
            )

        vectorizer = tf.keras.layers.TextVectorization(
            max_tokens=max_tokens,
            output_mode="int",
            output_sequence_length=sequence_length,
        )
        vectorizer.adapt(X_train)

        model = tf.keras.Sequential([
            vectorizer,
            tf.keras.layers.Embedding(input_dim=max_tokens, output_dim=32),
            tf.keras.layers.GlobalAveragePooling1D(),
            tf.keras.layers.Dense(64, activation="relu"),
            tf.keras.layers.Dropout(0.3),
            tf.keras.layers.Dense(len(class_names), activation="softmax"),
        ])
        model.compile(
            optimizer="adam",
            loss="categorical_crossentropy",
            metrics=["accuracy"],
        )

        early_stop = tf.keras.callbacks.EarlyStopping(
            monitor="val_loss",
            patience=early_stopping_patience,
            restore_best_weights=True,
        )

        model.fit(
            X_train, y_train,
            epochs=epochs,
            batch_size=batch_size,
            validation_data=(X_val, y_val),
            callbacks=[early_stop],
            verbose=0,
        )

        model_id = self._registry.create_new_model_id()
        base = Path(output_dir or self._config.get_models_dir())
        out_path = base / model_id
        out_path.mkdir(parents=True, exist_ok=True)

        model_file = out_path / MODEL_FILENAME
        labels_file = out_path / LABELS_FILENAME
        model.save(str(model_file))
        with open(labels_file, "w", encoding="utf-8") as f:
            f.write("\n".join(class_names))

        y_pred_probs = model.predict(X_val, verbose=0)
        y_pred = np.argmax(y_pred_probs, axis=1)
        y_true = np.argmax(y_val, axis=1)
        report = classification_report(
            y_true,
            y_pred,
            labels=list(range(len(class_names))),
            target_names=class_names,
            output_dict=True,
            zero_division=0,
        )
        accuracy = float(report.get("accuracy", 0))
        macro_f1 = 0.0
        if "macro avg" in report:
            macro_f1 = float(report["macro avg"].get("f1-score", 0))

        metadata = {
            "name": model_name,
            "epochs": epochs,
            "batch_size": batch_size,
            "dataset_filename": os.path.basename(dataset_path),
            "metrics": {
                "accuracy": accuracy,
                "macro_avg_f1": macro_f1,
            },
        }

        self._registry.register_model(
            model_id=model_id,
            name=model_name,
            model_path=str(model_file),
            labels_path=str(labels_file),
            metadata=metadata,
        )

        return {
            "model_id": model_id,
            "name": model_name,
            "model_path": str(model_file),
            "labels_path": str(labels_file),
            "metrics": metadata["metrics"],
        }
