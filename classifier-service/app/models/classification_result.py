"""
Domain model for a classification response.
"""
from dataclasses import dataclass


@dataclass(frozen=True)
class ClassificationResult:
    """Result of classifying a query: the predicted query type label (e.g. COUNT_DOCUMENTS)."""

    query_type: str

    def to_response_dict(self) -> dict:
        """API response shape: {"queryType": ...}."""
        return {"queryType": self.query_type}
