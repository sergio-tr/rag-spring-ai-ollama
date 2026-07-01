"""
Service configuration: environment variables only.
Exposed as a singleton Config class so all code uses the same instance and no loose functions.
"""
import json
import os
from pathlib import Path
from typing import ClassVar

# Reduce TensorFlow log noise before it is imported elsewhere
os.environ.setdefault("TF_CPP_MIN_LOG_LEVEL", "3")


class Config:
    """
    Configuration holder (singleton). All paths and options read from environment.
    Use Config() to get the single instance.
    """

    _instance: ClassVar["Config | None"] = None

    def __new__(cls) -> "Config":
        if cls._instance is None:
            cls._instance = super().__new__(cls)
        return cls._instance

    # Reserved tag for the default model
    DEFAULT_MODEL_TAG: str = "default"

    def get_port(self) -> int:
        """Server port (default 8000)."""
        return int(os.environ.get("PORT", "8000"))

    def get_models_dir(self) -> str:
        """Directory where trained models are stored (one subdirectory per model id)."""
        return os.environ.get("MODELS_DIR", "models")

    def get_data_dir(self) -> str:
        """Directory for default datasets (training/evaluation)."""
        return os.environ.get("DATA_DIR", "data")

    def get_default_model_path(self) -> str:
        """Path to the default classifier artifact (Keras or sklearn joblib)."""
        explicit = os.environ.get("MODEL_PATH")
        if explicit:
            return explicit
        default_dir = Path(self.get_models_dir()) / self.DEFAULT_MODEL_TAG
        return str(self._resolve_artifact_in_dir(default_dir))

    def get_default_labels_path(self) -> str:
        """Path to the default labels file. Default: models/default/labels.txt."""
        return os.environ.get(
            "LABELS_PATH",
            os.path.join(self.get_models_dir(), "default", "labels.txt"),
        )

    @staticmethod
    def _resolve_artifact_in_dir(model_dir: Path) -> Path:
        meta_path = model_dir / "metadata.json"
        if meta_path.exists():
            try:
                with open(meta_path, "r", encoding="utf-8") as f:
                    meta = json.load(f)
                raw = str(meta.get("modelType", "")).strip().lower()
                if raw == "sklearn" and (model_dir / "model.joblib").exists():
                    return model_dir / "model.joblib"
                if raw == "keras" and (model_dir / "model.keras").exists():
                    return model_dir / "model.keras"
            except (json.JSONDecodeError, OSError):
                pass
        joblib_path = model_dir / "model.joblib"
        keras_path = model_dir / "model.keras"
        if joblib_path.exists():
            return joblib_path
        return keras_path

    def get_default_model_id(self) -> str:
        """Model id used when not provided. Always returns a string (default 'default')."""
        return os.environ.get("DEFAULT_MODEL_ID") or self.DEFAULT_MODEL_TAG

    def get_default_model_name(self) -> str:
        """Display name for the default model in GET /models."""
        return os.environ.get("DEFAULT_MODEL_NAME", "default")

    def get_default_eval_dataset_path(self) -> str:
        """Path to the default evaluation dataset (Excel). Used when no file is uploaded."""
        return os.path.join(self.get_data_dir(), "evaluation_dataset.xlsx")
