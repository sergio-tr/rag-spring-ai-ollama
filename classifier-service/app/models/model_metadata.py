"""
Domain model for a registered model's metadata.
"""
from dataclasses import dataclass
from typing import Any


@dataclass
class ModelMetadata:
    """Metadata for a model (default or trained): id, name, optional creation time and metrics."""

    id: str
    name: str
    created_at: str | None
    metrics: dict[str, Any] | None

    def to_response_dict(self) -> dict:
        """API response shape for GET /models."""
        return {
            "id": self.id,
            "name": self.name,
            "createdAt": self.created_at,
            "metrics": self.metrics,
        }
