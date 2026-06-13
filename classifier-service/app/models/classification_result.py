"""
Domain model for a classification response.
"""
from dataclasses import dataclass


@dataclass(frozen=True)
class TopPrediction:
    """Single ranked prediction with confidence."""

    query_type: str
    confidence: float

    def to_dict(self) -> dict:
        return {"queryType": self.query_type, "confidence": self.confidence}


@dataclass(frozen=True)
class ClassificationResult:
    """Result of classifying a query."""

    query_type: str
    confidence: float | None = None
    top_predictions: tuple[TopPrediction, ...] = ()
    label_set_hash: str | None = None

    def to_response_dict(self) -> dict:
        """API response shape with optional reliability metadata."""
        out: dict = {"queryType": self.query_type}
        if self.confidence is not None:
            out["confidence"] = self.confidence
        if self.top_predictions:
            out["topPredictions"] = [p.to_dict() for p in self.top_predictions]
        if self.label_set_hash:
            out["labelSetHash"] = self.label_set_hash
        return out
