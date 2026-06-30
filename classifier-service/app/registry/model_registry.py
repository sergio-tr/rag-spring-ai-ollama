"""
Model registry: list models, resolve paths by id, register new trained models.
Persistence on disk under MODELS_DIR; each model in a subdirectory {id}/ with model.keras or model.joblib,
labels.txt, metadata.json.
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
KERAS_FILENAME = "model.keras"
SKLEARN_FILENAME = "model.joblib"
LABELS_FILENAME = "labels.txt"
# Backward-compatible alias for tests and legacy scripts
MODEL_FILENAME = KERAS_FILENAME


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
        Returns list of {id, name, createdAt, metrics, modelType}.
        """
        default_meta = self._read_default_metadata()
        result = [
            {
                "id": self._config.DEFAULT_MODEL_TAG,
                "name": self._config.get_default_model_name(),
                "createdAt": default_meta.get("createdAt"),
                "metrics": default_meta.get("metrics"),
                "modelType": default_meta.get("modelType", "keras"),
            }
        ]
        base = self._ensure_models_dir()
        for path in base.iterdir():
            if not path.is_dir():
                continue
            model_id = path.name
            meta_path = path / METADATA_FILENAME
            keras_path = path / KERAS_FILENAME
            sklearn_path = path / SKLEARN_FILENAME
            if not meta_path.exists() or (not keras_path.exists() and not sklearn_path.exists()):
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
                "modelType": meta.get("modelType", "keras" if keras_path.exists() else "sklearn"),
            })
        result[1:] = sorted(result[1:], key=lambda x: x.get("createdAt", "") or "", reverse=True)
        return result

    def _read_default_metadata(self) -> dict:
        default_dir = Path(self._config.get_default_model_path()).parent
        meta_path = default_dir / METADATA_FILENAME
        if not meta_path.exists():
            return {}
        try:
            with open(meta_path, "r", encoding="utf-8") as f:
                return json.load(f)
        except (json.JSONDecodeError, OSError):
            return {}

    def get_model_paths(self, model_id: str) -> tuple[str, str, str] | None:
        """
        Returns (artifact_path, labels_path, model_type) for model_id, or None if not found.
        'default' is resolved via config paths.
        """
        if model_id == self._config.DEFAULT_MODEL_TAG:
            artifact = self._config.get_default_model_path()
            labels = self._config.get_default_labels_path()
            model_type = self._infer_model_type(Path(artifact).parent, Path(artifact))
            return artifact, labels, model_type
        base = Path(self._config.get_models_dir())
        dir_path = base / model_id
        labels_path = dir_path / LABELS_FILENAME
        meta_path = dir_path / METADATA_FILENAME
        keras_path = dir_path / KERAS_FILENAME
        sklearn_path = dir_path / SKLEARN_FILENAME
        if not labels_path.exists():
            return None
        model_type = "keras"
        artifact_path = keras_path
        if sklearn_path.exists():
            artifact_path = sklearn_path
            model_type = "sklearn"
        elif not keras_path.exists():
            return None
        if meta_path.exists():
            try:
                with open(meta_path, "r", encoding="utf-8") as f:
                    meta = json.load(f)
                raw = str(meta.get("modelType", "")).strip().lower()
                if raw in ("keras", "sklearn"):
                    model_type = raw
            except (json.JSONDecodeError, OSError):
                pass
        return str(artifact_path), str(labels_path), model_type

    @staticmethod
    def _infer_model_type(model_dir: Path, artifact_path: Path) -> str:
        meta_path = model_dir / METADATA_FILENAME
        if meta_path.exists():
            try:
                with open(meta_path, "r", encoding="utf-8") as f:
                    meta = json.load(f)
                raw = str(meta.get("modelType", "")).strip().lower()
                if raw in ("keras", "sklearn"):
                    return raw
            except (json.JSONDecodeError, OSError):
                pass
        if artifact_path.name == SKLEARN_FILENAME or artifact_path.suffix == ".joblib":
            return "sklearn"
        return "keras"

    def register_model(
        self,
        model_id: str,
        name: str,
        model_path: str,
        labels_path: str,
        metadata: dict | None = None,
    ) -> None:
        """
        Registers a model: writes artifact and labels under MODELS_DIR/{model_id}/ and metadata.json.
        metadata should include modelType ('keras' | 'sklearn') and training metrics.
        """
        base = self._ensure_models_dir()
        dir_path = base / model_id
        dir_path.mkdir(parents=True, exist_ok=True)
        meta = metadata or {}
        model_type = str(meta.get("modelType", "keras")).strip().lower()
        dest_name = SKLEARN_FILENAME if model_type == "sklearn" else KERAS_FILENAME
        dest_model = dir_path / dest_name
        dest_labels = dir_path / LABELS_FILENAME
        if os.path.abspath(model_path) != os.path.abspath(str(dest_model)):
            shutil.copy2(model_path, dest_model)
        if os.path.abspath(labels_path) != os.path.abspath(str(dest_labels)):
            shutil.copy2(labels_path, dest_labels)
        meta_out = {
            "name": name,
            "createdAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
            "modelType": model_type,
            **meta,
        }
        with open(dir_path / METADATA_FILENAME, "w", encoding="utf-8") as f:
            json.dump(meta_out, f, indent=2)

    @staticmethod
    def create_new_model_id() -> str:
        """Generates a unique id for a new model (short uuid4)."""
        return str(uuid.uuid4())[:8]
