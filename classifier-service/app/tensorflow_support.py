"""Optional TensorFlow import for legacy Keras models and Lab training (not needed for sklearn default)."""

from __future__ import annotations

_KERAS_IMAGE_HINT = (
    "Keras/TensorFlow is not installed in this classifier image. "
    "Rebuild with INSTALL_GPU_EXTRAS=1 and a CUDA base (./docker/scripts/up.sh dev --classifier-gpu), "
    "or pip install -r requirements-gpu.txt locally."
)


def require_tensorflow():
    """Return the tensorflow module or raise with a product-facing error."""
    try:
        import tensorflow as tf
    except ImportError as exc:
        raise RuntimeError(_KERAS_IMAGE_HINT) from exc
    return tf
