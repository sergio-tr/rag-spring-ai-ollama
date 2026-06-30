"""
Unit tests for Config singleton (paths and defaults from env).
"""
import os

import pytest

from app.config import Config


@pytest.fixture(autouse=True)
def reset_config_singleton():
    """Reset Config singleton so tests can set env and get fresh behaviour."""
    Config._instance = None
    yield
    Config._instance = None


def test_config_singleton_same_instance():
    a = Config()
    b = Config()
    assert a is b


def test_get_port_default():
    if "PORT" in os.environ:
        del os.environ["PORT"]
    Config._instance = None
    c = Config()
    assert c.get_port() == 8000


def test_get_models_dir_default():
    if "MODELS_DIR" in os.environ:
        del os.environ["MODELS_DIR"]
    Config._instance = None
    c = Config()
    assert c.get_models_dir() == "models"


def test_get_data_dir_default():
    if "DATA_DIR" in os.environ:
        del os.environ["DATA_DIR"]
    Config._instance = None
    c = Config()
    assert c.get_data_dir() == "data"


def test_get_default_eval_dataset_path_uses_data_dir():
    os.environ["DATA_DIR"] = "custom-data"
    Config._instance = None
    c = Config()
    assert c.get_default_eval_dataset_path().endswith("custom-data/evaluation_dataset.xlsx")
    del os.environ["DATA_DIR"]


def test_get_default_model_id_default():
    if "DEFAULT_MODEL_ID" in os.environ:
        del os.environ["DEFAULT_MODEL_ID"]
    Config._instance = None
    c = Config()
    assert c.get_default_model_id() == "default"


def test_get_default_model_id_from_env():
    os.environ["DEFAULT_MODEL_ID"] = "custom"
    Config._instance = None
    c = Config()
    assert c.get_default_model_id() == "custom"
    del os.environ["DEFAULT_MODEL_ID"]


def test_get_default_model_path_uses_models_dir(tmp_path, monkeypatch):
    if "MODEL_PATH" in os.environ:
        del os.environ["MODEL_PATH"]
    monkeypatch.setenv("MODELS_DIR", str(tmp_path / "empty-models"))
    Config._instance = None
    c = Config()
    path = c.get_default_model_path()
    assert "empty-models" in path
    assert "default" in path
    assert path.endswith("model.keras")


def test_default_model_tag_constant():
    assert Config.DEFAULT_MODEL_TAG == "default"
