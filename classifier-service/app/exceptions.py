"""
Custom exceptions for the classifier service.
Each maps to a clear HTTP status and structured error response.
"""


class ClassifierServiceError(Exception):
    """Base exception for classifier service errors."""

    def __init__(self, message: str, code: str = "INTERNAL_ERROR"):
        self.message = message
        self.code = code
        super().__init__(message)


class ValidationError(ClassifierServiceError):
    """Invalid input (e.g. empty query, wrong file type). Maps to 400."""

    def __init__(self, message: str, code: str = "VALIDATION_ERROR"):
        super().__init__(message, code)


class ModelNotFoundError(ClassifierServiceError):
    """Requested model id not found in registry. Maps to 404."""

    def __init__(self, model_id: str, code: str = "MODEL_NOT_FOUND"):
        super().__init__(f"Model '{model_id}' not found", code)


class ClassificationError(ClassifierServiceError):
    """Classification failed (model not loaded, inference error). Maps to 503."""

    def __init__(self, message: str, code: str = "CLASSIFICATION_ERROR"):
        super().__init__(message, code)


class TrainingError(ClassifierServiceError):
    """Training failed (invalid dataset, runtime error). Maps to 400 or 500."""

    def __init__(self, message: str, code: str = "TRAINING_ERROR"):
        super().__init__(message, code)


class EvaluationError(ClassifierServiceError):
    """Evaluation failed (model not found, invalid eval dataset, etc.). Maps to 400 or 404."""

    def __init__(self, message: str, code: str = "EVALUATION_ERROR"):
        super().__init__(message, code)
