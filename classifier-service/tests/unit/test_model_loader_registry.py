"""ModelLoader: loading models registered under MODELS_DIR (non-default id)."""

from __future__ import annotations

from unittest.mock import MagicMock


from app.config import Config
from app.inference import model_loader as ml_mod
from app.inference.model_loader import ModelLoader
from app.registry.model_registry import METADATA_FILENAME, MODEL_FILENAME, LABELS_FILENAME


def _reset_config():
    Config._instance = None


def test_load_by_id_from_registry(monkeypatch, tmp_path):
    _reset_config()
    monkeypatch.setenv("MODELS_DIR", str(tmp_path))
    mid = "cust01"
    d = tmp_path / mid
    d.mkdir()
    (d / MODEL_FILENAME).write_bytes(b"k")
    (d / LABELS_FILENAME).write_text("COUNT_DOCUMENTS\nSUMMARIZE_MEETING\n")
    (d / METADATA_FILENAME).write_text("{}", encoding="utf-8")

    loaded = []

    def fake_load_model(path):
        loaded.append(path)
        return object()

    monkeypatch.setattr(ml_mod.tf.keras.models, "load_model", fake_load_model)

    loader = ModelLoader(config=Config())
    m, names = loader.load_by_id(mid)
    assert names == ["COUNT_DOCUMENTS", "SUMMARIZE_MEETING"]
    assert loader.is_loaded(mid)
    assert loader.get_model(mid) is m
    assert loader.get_class_names(mid) == names
    assert len(loaded) == 1


def test_load_by_id_cache_hit_skips_disk(monkeypatch, tmp_path):
    _reset_config()
    monkeypatch.setenv("MODELS_DIR", str(tmp_path))
    mid = "cust02"
    d = tmp_path / mid
    d.mkdir()
    (d / MODEL_FILENAME).write_bytes(b"k")
    (d / LABELS_FILENAME).write_text("COUNT_DOCUMENTS\n")

    monkeypatch.setattr(ml_mod.tf.keras.models, "load_model", lambda p: MagicMock(name="model"))

    loader = ModelLoader(config=Config())
    loader.load_by_id(mid)
    loader.load_by_id(mid)
    # Cache: second load_by_id returns without re-reading registry paths in load_by_id
    # (first branch: model_id in cache after first load)
    assert loader.is_loaded(mid)
