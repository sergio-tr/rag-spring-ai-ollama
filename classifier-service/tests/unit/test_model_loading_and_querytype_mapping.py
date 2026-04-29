import numpy as np
from unittest import mock

from app.config import Config
from app.inference.inference_engine import InferenceEngine
from app.inference.model_loader import ModelLoader


QUERY_TYPES_ALLOWED = {
    "COUNT_DOCUMENTS",
    "EXTRACT_ENTITIES",
    "COUNT_AND_EXPLAIN",
    "FIND_PARAGRAPH",
    "DECISION_EXTRACTION",
    "GET_DURATION",
    "GET_FIELD",
    "SUMMARIZE_TOPIC",
    "SUMMARIZE_MEETING",
    "BOOLEAN_QUERY",
    "FILTER_AND_LIST",
    "COMPARE",
}


def _reset_config_singleton():
    # Config es singleton; resetear garantiza que los tests leen vars del env actuales.
    Config._instance = None


def test_model_loader_loads_default_model_and_labels_and_caches(monkeypatch, tmp_path):
    _reset_config_singleton()

    models_dir = tmp_path / "models"
    default_dir = models_dir / "default"
    default_dir.mkdir(parents=True)

    # Archivos dummy: no se valida formato real porque mockeamos load_model.
    (default_dir / "model.keras").write_bytes(b"dummy-model")
    (default_dir / "labels.txt").write_text("\n".join(["COUNT_DOCUMENTS", "SUMMARIZE_MEETING"]) + "\n")

    monkeypatch.setenv("MODELS_DIR", str(models_dir))
    monkeypatch.setenv("DEFAULT_MODEL_ID", "default")

    load_calls = {"count": 0, "paths": []}

    # Mock de TensorFlow dentro del módulo para evitar dependencias/formato real.
    import app.inference.model_loader as model_loader_mod

    def _fake_load_model(path):
        load_calls["count"] += 1
        load_calls["paths"].append(path)
        return object()

    monkeypatch.setattr(model_loader_mod.tf.keras.models, "load_model", _fake_load_model)

    loader = ModelLoader(config=Config())
    model, class_names = loader.load_by_id("default")

    assert loader.is_loaded("default") is True
    assert class_names == ["COUNT_DOCUMENTS", "SUMMARIZE_MEETING"]
    assert load_calls["count"] == 1
    assert len(load_calls["paths"]) == 1
    assert str(default_dir / "model.keras") in load_calls["paths"][0]

    # Segunda llamada debe usar caché (no vuelve a cargar).
    model2, class_names2 = loader.load_by_id("default")
    assert model2 is model
    assert class_names2 == class_names
    assert load_calls["count"] == 1


def test_inference_engine_predict_maps_argmax_to_label():
    _reset_config_singleton()

    labels = ["COUNT_DOCUMENTS", "SUMMARIZE_MEETING", "COMPARE"]
    expected = "SUMMARIZE_MEETING"

    class DummyModel:
        def predict(self, _x):
            # argmax -> index 1
            return np.array([[0.1, 0.9, 0.05]], dtype=float)

    loader = mock.MagicMock()
    loader.is_loaded.return_value = True
    loader.get_model.return_value = DummyModel()
    loader.get_class_names.return_value = labels

    engine = InferenceEngine(loader=loader, config=Config())
    out = engine.predict("q", model_id="default")

    assert out == expected
    assert out in QUERY_TYPES_ALLOWED


def test_inference_engine_predict_loads_model_when_not_loaded():
    _reset_config_singleton()

    class DummyModel:
        def predict(self, _x):
            return np.array([[0.9, 0.1]], dtype=float)

    loader = mock.MagicMock()
    loader.is_loaded.return_value = False
    loader.get_model.return_value = DummyModel()
    loader.get_class_names.return_value = ["COUNT_DOCUMENTS", "SUMMARIZE_MEETING"]

    engine = InferenceEngine(loader=loader, config=Config())
    out = engine.predict("q", model_id="default")

    assert out == "COUNT_DOCUMENTS"
    loader.load_by_id.assert_called_once_with("default")

