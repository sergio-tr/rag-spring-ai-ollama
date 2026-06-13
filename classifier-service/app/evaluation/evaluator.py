"""
Evaluation pipeline: load model, run predictions on eval dataset, compute metrics and generate images.
Matches the behaviour of the prior trainer (classification report table + confusion matrix heatmaps).
"""
import io

import numpy as np
import pandas as pd
import tensorflow as tf
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
import seaborn as sns
from sklearn.metrics import classification_report, confusion_matrix

from app.base import Loggable
from app.dataset_columns import normalize_excel_classification_columns
from app.evaluation.result import EvaluationResult
from app.inference.model_loader import ModelLoader
from app.query_type_contract import canonical_class_order
from app.registry.model_registry import ModelRegistry


class EvaluationPipeline(Loggable):
    """Runs evaluation for a model: metrics and optional PNG images (report table, confusion matrix)."""

    def __init__(
        self,
        config: "Config | None" = None,
        loader: ModelLoader | None = None,
        registry: ModelRegistry | None = None,
    ) -> None:
        from app.config import Config
        self._config = config or Config()
        self._registry = registry or ModelRegistry(self._config)
        self._loader = loader or ModelLoader(self._config, self._registry)

    def evaluate(
        self,
        model_id: str,
        eval_dataset_path: str,
        include_images: bool = True,
    ) -> EvaluationResult:
        """
        Evaluates the model with the given id on the evaluation dataset (Excel: Question, QueryType).
        Returns metrics and, if include_images, PNG bytes for classification report and confusion matrix.
        """
        if not self._loader.is_loaded(model_id):
            self._loader.load_by_id(model_id)
        model = self._loader.get_model(model_id)
        model_class_names = self._loader.get_class_names(model_id)
        class_names = canonical_class_order(model_class_names)
        canon_idx = {name: i for i, name in enumerate(class_names)}

        df = normalize_excel_classification_columns(pd.read_excel(eval_dataset_path))
        if "Question" not in df.columns or "QueryType" not in df.columns:
            raise ValueError("Evaluation dataset must have columns 'Question' and 'QueryType'")
        X = df["Question"].astype(str).values
        y_raw = df["QueryType"].astype(str)
        valid = y_raw.isin(canon_idx)
        if not valid.all():
            self._logger.warning("Dropping %d rows with QueryType not in model labels", (~valid).sum())
        X = X[valid.values]
        y_raw = y_raw[valid]
        if len(X) == 0:
            raise ValueError(
                "No valid rows in evaluation dataset after filtering by model labels. "
                "Ensure QueryType values match the model labels."
            )

        y_pred_probs = model.predict(tf.constant([str(x) for x in X]), verbose=0)
        y_pred_model_idx = np.argmax(y_pred_probs, axis=1)
        y_true = np.array([canon_idx[c] for c in y_raw])
        y_pred = np.array([canon_idx[model_class_names[int(i)]] for i in y_pred_model_idx])

        report = classification_report(
            y_true, y_pred, target_names=class_names, output_dict=True, zero_division=0
        )
        conf_matrix = confusion_matrix(y_true, y_pred, labels=list(range(len(class_names))))

        report_image_bytes: bytes | None = None
        confusion_image_bytes: bytes | None = None
        if include_images:
            report_image_bytes = self._build_classification_report_image(report, class_names)
            confusion_image_bytes = self._build_confusion_matrix_image(conf_matrix, class_names)

        return EvaluationResult(
            model_id=model_id,
            classification_report=report,
            confusion_matrix=conf_matrix.tolist(),
            class_names=class_names,
            classification_report_image_bytes=report_image_bytes,
            confusion_matrix_image_bytes=confusion_image_bytes,
        )

    def _build_classification_report_image(self, report: dict, class_names: list[str]) -> bytes:
        """Build PNG heatmap of classification report (precision, recall, f1) like the prior trainer."""
        report_df = pd.DataFrame(report).transpose()
        rows = [r for r in class_names if r in report_df.index]
        if not rows:
            rows = [r for r in report_df.index if r not in ("accuracy", "macro avg", "weighted avg")]
        cols = [c for c in ("precision", "recall", "f1-score") if c in report_df.columns]
        if not cols:
            cols = report_df.columns.tolist()
        data = report_df.loc[rows, cols].apply(pd.to_numeric, errors="coerce").astype(float)
        _, ax = plt.subplots(figsize=(10, max(4, len(data) * 0.5)))
        sns.heatmap(data, annot=True, fmt=".2f", cmap="Greens", ax=ax)
        ax.set_title("Classification Report")
        plt.tight_layout()
        buf = io.BytesIO()
        plt.savefig(buf, format="png", dpi=100, bbox_inches="tight")
        plt.close()
        buf.seek(0)
        return buf.read()

    def _build_confusion_matrix_image(self, conf_matrix: np.ndarray, class_names: list[str]) -> bytes:
        """Build PNG heatmap of confusion matrix (Blues) like the prior trainer."""
        _, ax = plt.subplots(figsize=(12, 8))
        sns.heatmap(
            conf_matrix,
            annot=True,
            fmt="d",
            cmap="Blues",
            xticklabels=class_names,
            yticklabels=class_names,
            ax=ax,
        )
        ax.set_xlabel("Predicted")
        ax.set_ylabel("True")
        ax.set_title("Confusion Matrix")
        plt.tight_layout()
        buf = io.BytesIO()
        plt.savefig(buf, format="png", dpi=100, bbox_inches="tight")
        plt.close()
        buf.seek(0)
        return buf.read()
