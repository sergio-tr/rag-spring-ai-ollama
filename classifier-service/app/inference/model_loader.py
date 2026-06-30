"""
Model loader: loads Keras or sklearn models and labels by model_id, with in-memory cache.
Resolves paths via config (default) or registry (trained models).
"""
from __future__ import annotations

import json
import zipfile
from pathlib import Path

import joblib
import tensorflow as tf

from app.base import Loggable
from app.config import Config
from app.inference.loaded_model import LoadedModel, ModelType
from app.query_type_contract import validate_loaded_labels
from app.registry.model_registry import ModelRegistry

KERAS_FILENAME = "model.keras"
SKLEARN_FILENAME = "model.joblib"
METADATA_FILENAME = "metadata.json"
LABELS_FILENAME = "labels.txt"


class ModelLoader(Loggable):
    """Loads and caches models by id. Delegates path resolution to config (default) or registry."""

    def __init__(self, config: Config | None = None, registry: ModelRegistry | None = None) -> None:
        self._config = config or Config()
        self._registry = registry or ModelRegistry(self._config)
        self._cache: dict[str, LoadedModel] = {}

    def is_loaded(self, model_id: str) -> bool:
        """Returns True if the model for this id is in cache."""
        return model_id in self._cache

    def load_by_id(self, model_id: str) -> LoadedModel:
        """
        Loads the model for the given id (default from config, others from registry).
        Caches and returns LoadedModel. Raises FileNotFoundError if not found.
        """
        if model_id in self._cache:
            return self._cache[model_id]
        if model_id == self._config.DEFAULT_MODEL_TAG:
            loaded = self._load_default()
        else:
            paths = self._registry.get_model_paths(model_id)
            if not paths:
                raise FileNotFoundError(f"Model '{model_id}' not found in registry")
            artifact_path, labels_path, model_type = paths
            loaded = self._load_from_paths(artifact_path, labels_path, model_type)
        self._cache[model_id] = loaded
        return loaded

    def get_loaded_model(self, model_id: str) -> LoadedModel:
        """Returns LoadedModel for model_id; loads it if not in cache."""
        if model_id not in self._cache:
            self.load_by_id(model_id)
        return self._cache[model_id]

    def get_model(self, model_id: str):
        """Returns the underlying Keras model or sklearn Pipeline."""
        return self.get_loaded_model(model_id).artifact

    def get_class_names(self, model_id: str) -> list[str]:
        """Returns the list of class labels for model_id; loads if not in cache."""
        return list(self.get_loaded_model(model_id).class_names)

    def get_model_type(self, model_id: str) -> ModelType:
        return self.get_loaded_model(model_id).model_type

    def _load_default(self) -> LoadedModel:
        model_path = self._config.get_default_model_path()
        labels_path = self._config.get_default_labels_path()
        model_type = self._infer_model_type(Path(model_path).parent, Path(model_path))
        return self._load_from_paths(model_path, labels_path, model_type)

    def _load_from_paths(self, artifact_path: str, labels_path: str, model_type: ModelType) -> LoadedModel:
        class_names = self._read_labels_file(labels_path)
        if model_type == "sklearn":
            artifact = joblib.load(artifact_path)
            return LoadedModel(model_type="sklearn", artifact=artifact, class_names=class_names)
        model = self._load_model_with_vocab_fix(artifact_path)
        return LoadedModel(model_type="keras", artifact=model, class_names=class_names)

    @staticmethod
    def _infer_model_type(model_dir: Path, artifact_path: Path) -> ModelType:
        meta_path = model_dir / METADATA_FILENAME
        if meta_path.exists():
            try:
                with open(meta_path, "r", encoding="utf-8") as f:
                    meta = json.load(f)
                raw = str(meta.get("modelType", "")).strip().lower()
                if raw == "sklearn":
                    return "sklearn"
                if raw == "keras":
                    return "keras"
            except (json.JSONDecodeError, OSError):
                pass
        if artifact_path.name == SKLEARN_FILENAME or artifact_path.suffix == ".joblib":
            return "sklearn"
        return "keras"

    def _read_labels_file(self, labels_path: str) -> list[str]:
        """Reads labels and validates each entry against the Java QueryType contract."""
        try:
            with open(labels_path, "r", encoding="utf-8-sig") as f:
                lines = [line.strip() for line in f.readlines() if line.strip() and not line.strip().startswith("#")]
        except UnicodeDecodeError:
            with open(labels_path, "r", encoding="utf-8", errors="replace") as f:
                lines = [line.strip() for line in f.readlines() if line.strip() and not line.strip().startswith("#")]
        return validate_loaded_labels(lines)

    def _load_model_with_vocab_fix(self, model_path: str):
        """
        Loads a .keras model, repairing older TextVectorization vocabulary assets when needed.

        Some historical models were saved with latin-1 encoded vocab files inside the .keras zip.
        Keras expects UTF-8 and will fail at load time with a UnicodeDecodeError wrapped in ValueError.
        """
        try:
            return tf.keras.models.load_model(model_path)
        except ValueError as e:
            msg = str(e)
            if "utf-8" not in msg or "TextVectorization" not in msg:
                raise
            fixed = self._maybe_fix_keras_text_vectorization_vocab(model_path)
            if not fixed:
                raise
            return tf.keras.models.load_model(model_path)

    def _maybe_fix_keras_text_vectorization_vocab(self, model_path: str) -> bool:
        vocab_paths = (
            "assets/layers/text_vectorization/vocabulary.txt",
            "assets/layers/text_vectorization/_lookup_layer/vocabulary.txt",
        )
        try:
            with zipfile.ZipFile(model_path, "r") as z:
                names = set(z.namelist())
                if not all(p in names for p in vocab_paths):
                    return False
                replacements: dict[str, bytes] = {}
                changed = False
                for p in vocab_paths:
                    raw = z.read(p)
                    try:
                        raw.decode("utf-8")
                        continue
                    except UnicodeDecodeError:
                        fixed = raw.decode("latin-1").encode("utf-8")
                        fixed.decode("utf-8")
                        replacements[p] = fixed
                        changed = True
        except Exception:
            return False

        if not changed:
            return False

        tmp_path = f"{model_path}.tmp"
        with zipfile.ZipFile(model_path, "r") as zin, zipfile.ZipFile(tmp_path, "w") as zout:
            for info in zin.infolist():
                name = info.filename
                buf = replacements.get(name)
                if buf is None:
                    buf = zin.read(name)
                zi = zipfile.ZipInfo(filename=name, date_time=info.date_time)
                zi.compress_type = info.compress_type
                zi.external_attr = info.external_attr
                zi.internal_attr = info.internal_attr
                zi.flag_bits = info.flag_bits
                zi.create_system = info.create_system
                zout.writestr(zi, buf)
        import os

        os.replace(tmp_path, model_path)
        return True
