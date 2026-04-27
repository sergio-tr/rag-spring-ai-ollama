"""
Model registry: list models, resolve paths by id, register new trained models.
Persistence on disk under MODELS_DIR; each model in a subdirectory {id}/ with model.keras, labels.txt, metadata.json.
"""
import json
import os
import shutil
import uuid
from datetime import datetime, timezone
from pathlib import Path

from app.base import Loggable
from app.config import Config

METADATA_FILENAME = "metadata.json"
MODEL_FILENAME = "model.keras"
LABELS_FILENAME = "labels.txt"


class ModelRegistry(Loggable):
    """Registry of trained models (default is not stored here; it uses config paths)."""

    def __init__(self, config: Config | None = None) -> None:
        self._config = config or Config()

    def _ensure_models_dir(self) -> Path:
        d = Path(self._config.get_models_dir())
        d.mkdir(parents=True, exist_ok=True)
        return d

    def list_models(self) -> list[dict]:
        """
        Lists available models: first the one with tag "default", then registered trained models.
        Returns list of {id, name, createdAt, metrics}.
        """
        result = [
            {
                "id": self._config.DEFAULT_MODEL_TAG,
                "name": self._config.get_default_model_name(),
                "createdAt": None,
                "metrics": None,
            }
        ]
        base = self._ensure_models_dir()
        for path in base.iterdir():
            if not path.is_dir():
                continue
            model_id = path.name
            meta_path = path / METADATA_FILENAME
            model_path = path / MODEL_FILENAME
            if not model_path.exists() or not meta_path.exists():
                continue
            try:
                with open(meta_path, "r", encoding="utf-8") as f:
                    meta = json.load(f)
            except (json.JSONDecodeError, OSError):
                meta = {}
            result.append({
                "id": model_id,
                "name": meta.get("name", model_id),
                "createdAt": meta.get("createdAt", ""),
                "metrics": meta.get("metrics"),
            })
        result[1:] = sorted(result[1:], key=lambda x: x.get("createdAt", "") or "", reverse=True)
        return result

    def get_model_paths(self, model_id: str) -> tuple[str, str] | None:
        """Returns (model_path, labels_path) for model_id, or None if not found. 'default' is not in registry."""
        if model_id == self._config.DEFAULT_MODEL_TAG:
            return None
        base = Path(self._config.get_models_dir())
        dir_path = base / model_id
        model_path = dir_path / MODEL_FILENAME
        labels_path = dir_path / LABELS_FILENAME
        if model_path.exists() and labels_path.exists():
            return str(model_path), str(labels_path)
        return None

    def register_model(
        self,
        model_id: str,
        name: str,
        model_path: str,
        labels_path: str,
        metadata: dict | None = None,
    ) -> None:
        """
        Registers a model: writes model.keras and labels.txt under MODELS_DIR/{model_id}/ and metadata.json.
        metadata can include epochs, batch_size, dataset_filename, metrics, etc.
        """
        base = self._ensure_models_dir()
        dir_path = base / model_id
        dir_path.mkdir(parents=True, exist_ok=True)
        dest_model = dir_path / MODEL_FILENAME
        dest_labels = dir_path / LABELS_FILENAME
        if os.path.abspath(model_path) != os.path.abspath(str(dest_model)):
            shutil.copy2(model_path, dest_model)
        if os.path.abspath(labels_path) != os.path.abspath(str(dest_labels)):
            shutil.copy2(labels_path, dest_labels)
        meta = {
            "name": name,
            "createdAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            **(metadata or {}),
        }
        with open(dir_path / METADATA_FILENAME, "w", encoding="utf-8") as f:
            json.dump(meta, f, indent=2)

    @staticmethod
    def create_new_model_id() -> str:
        """Generates a unique id for a new model (short uuid4)."""
        return str(uuid.uuid4())[:8]
