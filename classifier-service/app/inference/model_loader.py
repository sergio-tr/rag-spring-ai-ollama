"""
Model loader: loads Keras model and labels by model_id, with in-memory cache.
Resolves paths via config (default) or registry (trained models).
"""
import zipfile

import tensorflow as tf

from app.base import Loggable
from app.config import Config
from app.registry.model_registry import ModelRegistry


class ModelLoader(Loggable):
    """Loads and caches models by id. Delegates path resolution to config (default) or registry."""

    def __init__(self, config: Config | None = None, registry: ModelRegistry | None = None) -> None:
        self._config = config or Config()
        self._registry = registry or ModelRegistry(self._config)
        self._cache: dict[str, tuple] = {}

    def is_loaded(self, model_id: str) -> bool:
        """Returns True if the model for this id is in cache."""
        return model_id in self._cache

    def load_by_id(self, model_id: str) -> tuple:
        """
        Loads the model for the given id (default from config, others from registry).
        Caches and returns (model, class_names). Raises FileNotFoundError if not found.
        """
        if model_id == self._config.DEFAULT_MODEL_TAG:
            return self._load_default()
        if model_id in self._cache:
            return self._cache[model_id]
        paths = self._registry.get_model_paths(model_id)
        if not paths:
            raise FileNotFoundError(f"Model '{model_id}' not found in registry")
        model_path, labels_path = paths
        model = self._load_model_with_vocab_fix(model_path)
        class_names = self._read_labels_file(labels_path)
        self._cache[model_id] = (model, class_names)
        return model, class_names

    def _load_default(self) -> tuple:
        """Loads the default model from config paths. Idempotent."""
        if self._config.DEFAULT_MODEL_TAG in self._cache:
            return self._cache[self._config.DEFAULT_MODEL_TAG]
        model_path = self._config.get_default_model_path()
        labels_path = self._config.get_default_labels_path()
        model = self._load_model_with_vocab_fix(model_path)
        class_names = self._read_labels_file(labels_path)
        self._cache[self._config.DEFAULT_MODEL_TAG] = (model, class_names)
        return model, class_names

    def _read_labels_file(self, labels_path: str) -> list[str]:
        """
        Reads labels for StringLookup/TextVectorization.
        """
        try:
            with open(labels_path, "r", encoding="utf-8-sig") as f:
                return [line.strip() for line in f.readlines() if line.strip()]
        except UnicodeDecodeError:
            with open(labels_path, "r", encoding="utf-8", errors="replace") as f:
                return [line.strip() for line in f.readlines() if line.strip()]

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
                        # Validate output is UTF-8 now.
                        fixed.decode("utf-8")
                        replacements[p] = fixed
                        changed = True
        except Exception:
            return False

        if not changed:
            return False

        # Rewrite zip in-place (atomic replace).
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

    def get_model(self, model_id: str):
        """Returns the Keras model for model_id; loads it if not in cache."""
        if model_id not in self._cache:
            self.load_by_id(model_id)
        return self._cache[model_id][0]

    def get_class_names(self, model_id: str) -> list[str]:
        """Returns the list of class labels for model_id; loads if not in cache."""
        if model_id not in self._cache:
            self.load_by_id(model_id)
        return self._cache[model_id][1]
