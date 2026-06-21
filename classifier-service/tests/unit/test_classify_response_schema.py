"""Tests for /classify response schema and confidence fields."""

from __future__ import annotations

from app.models.classification_result import ClassificationResult, TopPrediction


def test_to_response_dict_includes_confidence_and_top_predictions():
    result = ClassificationResult(
        query_type="COUNT_DOCUMENTS",
        confidence=0.87,
        top_predictions=(
            TopPrediction("COUNT_DOCUMENTS", 0.87),
            TopPrediction("FIND_PARAGRAPH", 0.08),
        ),
        label_set_hash="abc123",
    )
    payload = result.to_response_dict()
    assert payload["queryType"] == "COUNT_DOCUMENTS"
    assert payload["confidence"] == 0.87
    assert payload["labelSetHash"] == "abc123"
    assert len(payload["topPredictions"]) == 2
    assert payload["topPredictions"][0]["queryType"] == "COUNT_DOCUMENTS"


def test_to_response_dict_omits_absent_optional_fields():
    result = ClassificationResult(query_type="BOOLEAN_QUERY")
    payload = result.to_response_dict()
    assert payload == {"queryType": "BOOLEAN_QUERY"}
