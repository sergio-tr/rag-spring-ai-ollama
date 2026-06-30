import zipfile

import pytest

from app.config import Config
from app.inference.model_loader import ModelLoader


def _reset_config_singleton():
    Config._instance = None


def _write_keras_zip(path, vocab_bytes: bytes):
    # Minimal .keras-like zip structure required by ModelLoader._maybe_fix_keras_text_vectorization_vocab.
    vocab_paths = (
        "assets/layers/text_vectorization/vocabulary.txt",
        "assets/layers/text_vectorization/_lookup_layer/vocabulary.txt",
    )
    with zipfile.ZipFile(path, "w") as z:
        z.writestr("metadata.json", b"{}")
        for p in vocab_paths:
            z.writestr(p, vocab_bytes)


def test_load_model_with_vocab_fix_repairs_latin1_vocab_and_retries(monkeypatch, tmp_path):
    _reset_config_singleton()

    models_dir = tmp_path / "models"
    default_dir = models_dir / "default"
    default_dir.mkdir(parents=True)

    model_path = default_dir / "model.keras"
    labels_path = default_dir / "labels.txt"

    # Write a .keras zip with latin-1 bytes that are invalid utf-8.
    _write_keras_zip(model_path, "áéíóú\n".encode("latin-1"))
    labels_path.write_text("COUNT_DOCUMENTS\n")
    (default_dir / "metadata.json").write_text('{"modelType":"keras"}', encoding="utf-8")

    monkeypatch.setenv("MODELS_DIR", str(models_dir))
    monkeypatch.setenv("DEFAULT_MODEL_ID", "default")

    import app.inference.model_loader as model_loader_mod

    calls = {"count": 0}

    def _fake_load_model(_path):
        calls["count"] += 1
        if calls["count"] == 1:
            raise ValueError("utf-8 codec can't decode bytes in TextVectorization")
        return object()

    monkeypatch.setattr(model_loader_mod.tf.keras.models, "load_model", _fake_load_model)

    loader = ModelLoader(config=Config())
    loaded = loader.load_by_id("default")

    assert loaded.artifact is not None
    assert loaded.class_names == ["COUNT_DOCUMENTS"]
    assert calls["count"] == 2  # initial failure + retry after vocab fix

    # Confirm vocab files are now utf-8 decodable.
    with zipfile.ZipFile(model_path, "r") as z:
        raw = z.read("assets/layers/text_vectorization/vocabulary.txt")
        raw.decode("utf-8")


def test_load_model_with_vocab_fix_does_not_swallow_unrelated_value_error(monkeypatch, tmp_path):
    _reset_config_singleton()

    models_dir = tmp_path / "models"
    default_dir = models_dir / "default"
    default_dir.mkdir(parents=True)

    model_path = default_dir / "model.keras"
    labels_path = default_dir / "labels.txt"
    _write_keras_zip(model_path, b"ok\n")
    labels_path.write_text("COUNT_DOCUMENTS\n")
    (default_dir / "metadata.json").write_text('{"modelType":"keras"}', encoding="utf-8")

    monkeypatch.setenv("MODELS_DIR", str(models_dir))
    monkeypatch.setenv("DEFAULT_MODEL_ID", "default")

    import app.inference.model_loader as model_loader_mod

    def _fake_load_model(_path):
        raise ValueError("some other keras error")

    monkeypatch.setattr(model_loader_mod.tf.keras.models, "load_model", _fake_load_model)

    loader = ModelLoader(config=Config())
    with pytest.raises(ValueError, match="some other keras error"):
        loader.load_by_id("default")

