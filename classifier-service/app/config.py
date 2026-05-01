"""
Service configuration: environment variables only.
Exposed as a singleton Config class so all code uses the same instance and no loose functions.
"""
import os
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
        """Path to the default Keras model file. Default: models/default/model.keras."""
        return os.environ.get(
            "MODEL_PATH",
            os.path.join(self.get_models_dir(), "default", "model.keras"),
        )

    def get_default_labels_path(self) -> str:
        """Path to the default labels file. Default: models/default/labels.txt."""
        return os.environ.get(
            "LABELS_PATH",
            os.path.join(self.get_models_dir(), "default", "labels.txt"),
        )

    def get_default_model_id(self) -> str:
        """Model id used when not provided. Always returns a string (default 'default')."""
        return os.environ.get("DEFAULT_MODEL_ID") or self.DEFAULT_MODEL_TAG

    def get_default_model_name(self) -> str:
        """Display name for the default model in GET /models."""
        return os.environ.get("DEFAULT_MODEL_NAME", "default")

    def get_default_eval_dataset_path(self) -> str:
        """Path to the default evaluation dataset (Excel). Used when no file is uploaded."""
        return os.path.join(self.get_data_dir(), "evaluation_dataset.xlsx")
