"""
Domain model for the result of a training run.
"""
from dataclasses import dataclass


@dataclass
class TrainingResult:
    """Result of training: model_id, name, and metrics."""

    model_id: str
    name: str
    metrics: dict

    def to_response_dict(self) -> dict:
        """API response shape for POST /train."""
        return {
            "modelId": self.model_id,
            "name": self.name,
            "metrics": self.metrics,
        }
