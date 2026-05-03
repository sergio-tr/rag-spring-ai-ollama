"""
Standard API error response shape for consistent error handling.
"""
from dataclasses import dataclass
from typing import Any


@dataclass
class ErrorDetail:
    """Structured error for API responses: code, message, optional details."""

    code: str
    message: str
    details: dict[str, Any] | None = None

    def to_response_dict(self) -> dict:
        """JSON body for error responses."""
        out: dict[str, Any] = {"code": self.code, "message": self.message}
        if self.details:
            out["details"] = self.details
        return out
