"""Unit tests for ModelRegistry (filesystem layout under MODELS_DIR)."""

from __future__ import annotations

import json
from pathlib import Path
from unittest.mock import patch

import pytest

from app.config import Config
from app.registry.model_registry import METADATA_FILENAME, MODEL_FILENAME, LABELS_FILENAME, ModelRegistry


def _reset_config():
    Config._instance = None


@pytest.fixture
def models_root(tmp_path, monkeypatch):
    _reset_config()
    monkeypatch.setenv("MODELS_DIR", str(tmp_path))
    return tmp_path


def test_list_models_default_only_when_empty(models_root):
    reg = ModelRegistry(Config())
    models = reg.list_models()
    assert len(models) == 1
    assert models[0]["id"] == "default"


def test_get_model_paths_default_returns_config_paths(models_root):
    reg = ModelRegistry(Config())
    paths = reg.get_model_paths("default")
    assert paths is not None
    artifact, labels, model_type = paths
    assert labels.endswith("labels.txt")
    assert model_type in ("keras", "sklearn")


def test_get_model_paths_missing_model(models_root):
    reg = ModelRegistry(Config())
    assert reg.get_model_paths("unknown-id") is None


def test_get_model_paths_returns_tuple_when_files_exist(models_root):
    mid = "abc12345"
    d = models_root / mid
    d.mkdir()
    (d / MODEL_FILENAME).write_bytes(b"x")
    (d / LABELS_FILENAME).write_text("A\nB\n")
    reg = ModelRegistry(Config())
    paths = reg.get_model_paths(mid)
    assert paths is not None
    mp, lp, model_type = paths
    assert Path(mp).name == MODEL_FILENAME
    assert Path(lp).name == LABELS_FILENAME
    assert model_type == "keras"


def test_list_models_includes_registered_sorted_by_created_at(models_root):
    reg = ModelRegistry(Config())

    def _register(suffix: str, created: str):
        mid = f"id{suffix}"
        base = models_root / mid
        base.mkdir()
        (base / MODEL_FILENAME).write_bytes(b"m")
        (base / LABELS_FILENAME).write_text("X\n")
        meta = {"name": f"n{suffix}", "createdAt": created, "metrics": {"a": 1}}
        (base / METADATA_FILENAME).write_text(json.dumps(meta), encoding="utf-8")

    _register("a", "2020-01-01T00:00:00Z")
    _register("b", "2021-01-01T00:00:00Z")

    models = reg.list_models()
    ids = [m["id"] for m in models if m["id"] != "default"]
    assert ids == ["idb", "ida"]


def test_list_models_skips_non_dirs_and_incomplete_entries(models_root):
    (models_root / "notadir.txt").write_text("x")
    bad = models_root / "incomplete"
    bad.mkdir()
    (bad / MODEL_FILENAME).write_bytes(b"x")
    reg = ModelRegistry(Config())
    extra = [m for m in reg.list_models() if m["id"] != "default"]
    assert extra == []


def test_list_models_invalid_json_uses_empty_meta(models_root):
    mid = "badjson"
    d = models_root / mid
    d.mkdir()
    (d / MODEL_FILENAME).write_bytes(b"m")
    (d / LABELS_FILENAME).write_text("L\n")
    (d / METADATA_FILENAME).write_text("{ not json", encoding="utf-8")
    reg = ModelRegistry(Config())
    row = next(m for m in reg.list_models() if m["id"] == mid)
    assert row["name"] == mid


def test_register_model_copies_and_writes_metadata(models_root):
    reg = ModelRegistry(Config())
    src_m = models_root / "src.keras"
    src_l = models_root / "labels.txt"
    src_m.write_bytes(b"modelbytes")
    src_l.write_text("A\nB\n")
    mid = "reg1"
    reg.register_model(
        model_id=mid,
        name="My Model",
        model_path=str(src_m),
        labels_path=str(src_l),
        metadata={"epochs": 3},
    )
    dest = models_root / mid
    assert (dest / MODEL_FILENAME).read_bytes() == b"modelbytes"
    assert (dest / LABELS_FILENAME).read_text() == "A\nB\n"
    meta = json.loads((dest / METADATA_FILENAME).read_text(encoding="utf-8"))
    assert meta["name"] == "My Model"
    assert meta["epochs"] == 3
    assert "createdAt" in meta


def test_register_model_keeps_model_id_distinct_from_display_name(models_root):
    reg = ModelRegistry(Config())
    src_m = models_root / "src-name.keras"
    src_l = models_root / "labels-name.txt"
    src_m.write_bytes(b"modelbytes")
    src_l.write_text("A\nB\n")
    model_id = "unique42"
    reg.register_model(
        model_id=model_id,
        name="default",
        model_path=str(src_m),
        labels_path=str(src_l),
        metadata={"ownerId": "rag-user-1"},
    )

    rows = reg.list_models()
    trained = next(m for m in rows if m["id"] == model_id)
    assert trained["id"] == model_id
    assert trained["name"] == "default"
    assert rows[0]["id"] == "default"
    meta = json.loads((models_root / model_id / METADATA_FILENAME).read_text(encoding="utf-8"))
    assert meta["ownerId"] == "rag-user-1"


def test_register_model_skips_copy_when_same_path(models_root):
    reg = ModelRegistry(Config())
    mid = "samepath"
    d = models_root / mid
    d.mkdir(parents=True)
    dest_m = d / MODEL_FILENAME
    dest_l = d / LABELS_FILENAME
    dest_m.write_bytes(b"keep")
    dest_l.write_text("Z\n")
    with patch("app.registry.model_registry.shutil.copy2") as cp:
        reg.register_model(
            model_id=mid,
            name="n",
            model_path=str(dest_m.resolve()),
            labels_path=str(dest_l.resolve()),
        )
    cp.assert_not_called()


def test_create_new_model_id_format():
    _reset_config()
    mid = ModelRegistry.create_new_model_id()
    assert len(mid) == 8


def test_create_new_model_id_is_unique_across_sample():
    _reset_config()
    ids = {ModelRegistry.create_new_model_id() for _ in range(100)}
    assert len(ids) == 100

