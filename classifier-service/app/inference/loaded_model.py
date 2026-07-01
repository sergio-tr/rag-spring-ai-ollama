"""Unified in-memory representation for Keras or sklearn classifier artifacts."""
from __future__ import annotations

from dataclasses import dataclass
from typing import Any, Literal

ModelType = Literal["keras", "sklearn"]


@dataclass(frozen=True)
class LoadedModel:
    """Classifier artifact loaded from disk with label contract."""

    model_type: ModelType
    artifact: Any
    class_names: list[str]
