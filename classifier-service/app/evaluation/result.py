"""
Domain model for evaluation output: metrics dict and optional image bytes.
"""
from dataclasses import dataclass
from typing import Any


@dataclass
class EvaluationResult:
    """
    Result of evaluating a model: full classification report, accuracy, macro avg,
    confusion matrix, and optional PNG image bytes for report and confusion matrix.
    """

    model_id: str
    classification_report: dict[str, Any]  # per-class + accuracy + macro avg
    confusion_matrix: list[list[int]]  # 2D row-major [true][pred]
    class_names: list[str]
    classification_report_image_bytes: bytes | None = None
    confusion_matrix_image_bytes: bytes | None = None

    def to_response_dict(self, include_images_base64: bool = True) -> dict:
        """API response (camelCase): metrics plus optional base64 images for webapp display/download."""
        out: dict[str, Any] = {
            "modelId": self.model_id,
            "metrics": {
                "classificationReport": self.classification_report,
                "accuracy": self.classification_report.get("accuracy"),
                "macroAvg": self.classification_report.get("macro avg"),
                "confusionMatrix": self.confusion_matrix,
                "classNames": self.class_names,
            },
        }
        if include_images_base64 and self.classification_report_image_bytes:
            import base64
            out["classificationReportImageBase64"] = base64.b64encode(
                self.classification_report_image_bytes
            ).decode("utf-8")
        if include_images_base64 and self.confusion_matrix_image_bytes:
            import base64
            out["confusionMatrixImageBase64"] = base64.b64encode(
                self.confusion_matrix_image_bytes
            ).decode("utf-8")
        return out
