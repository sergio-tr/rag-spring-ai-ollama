"""Unit tests for ModelRegistry default path guards."""
from __future__ import annotations

import pytest

from app.config import Config
from app.registry.model_registry import ModelRegistry


def test_register_model_rejects_default_id(tmp_path, monkeypatch):
    monkeypatch.setenv("MODELS_DIR", str(tmp_path / "models"))
    Config._instance = None
    registry = ModelRegistry(Config())

    with pytest.raises(ValueError, match="reserved id 'default'"):
        registry.register_model(
            "default",
            "n",
            __file__,
            __file__,
            metadata={"modelType": "sklearn"},
        )
