"""
Unit tests for domain models: ClassificationResult, EvaluationResult.
"""
import base64
import importlib.util
from pathlib import Path

from app.models.classification_result import ClassificationResult

# Load EvaluationResult without importing app.evaluation (avoids seaborn in envs without it)
_result_path = Path(__file__).resolve().parent.parent.parent / "app" / "evaluation" / "result.py"
_spec = importlib.util.spec_from_file_location("evaluation_result", _result_path)
_result_mod = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_result_mod)
EvaluationResult = _result_mod.EvaluationResult


class TestClassificationResult:
    def test_to_response_dict_returns_camelCase(self):
        r = ClassificationResult(query_type="COUNT_DOCUMENTS")
        d = r.to_response_dict()
        assert d == {"queryType": "COUNT_DOCUMENTS"}
        assert "queryType" in d
        assert "query_type" not in d

    def test_to_response_dict_different_type(self):
        r = ClassificationResult(query_type="SUMMARIZE_MEETING")
        assert r.to_response_dict()["queryType"] == "SUMMARIZE_MEETING"


class TestEvaluationResult:
    def test_to_response_dict_includes_metrics_and_camelCase(self):
        result = EvaluationResult(
            model_id="default",
            classification_report={"accuracy": 0.9, "macro avg": {"f1-score": 0.85}},
            confusion_matrix=[[5, 1], [0, 4]],
            class_names=["A", "B"],
        )
        d = result.to_response_dict(include_images_base64=False)
        assert d["modelId"] == "default"
        assert "metrics" in d
        assert d["metrics"]["accuracy"] == 0.9
        assert d["metrics"]["classificationReport"] == result.classification_report
        assert d["metrics"]["confusionMatrix"] == [[5, 1], [0, 4]]
        assert d["metrics"]["classNames"] == ["A", "B"]
        assert "classificationReportImageBase64" not in d
        assert "confusionMatrixImageBase64" not in d

    def test_to_response_dict_with_images_base64(self):
        result = EvaluationResult(
            model_id="m1",
            classification_report={"accuracy": 1.0},
            confusion_matrix=[[1]],
            class_names=["X"],
            classification_report_image_bytes=b"\x89PNG\r\n\x1a\n",
            confusion_matrix_image_bytes=b"\x89PNG\r\n\x1a\n",
        )
        d = result.to_response_dict(include_images_base64=True)
        assert "classificationReportImageBase64" in d
        assert "confusionMatrixImageBase64" in d
        assert len(base64.b64decode(d["classificationReportImageBase64"])) > 0
        assert len(base64.b64decode(d["confusionMatrixImageBase64"])) > 0

    def test_to_response_dict_include_images_false_omits_images(self):
        result = EvaluationResult(
            model_id="m1",
            classification_report={},
            confusion_matrix=[],
            class_names=[],
            classification_report_image_bytes=b"png",
            confusion_matrix_image_bytes=b"png",
        )
        d = result.to_response_dict(include_images_base64=False)
        assert "classificationReportImageBase64" not in d
        assert "confusionMatrixImageBase64" not in d
