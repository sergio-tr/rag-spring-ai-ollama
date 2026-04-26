"""
HTTP endpoints: health, models, classify, train, evaluate.
Delegates to services from the container; maps exceptions to structured error responses.
"""
import json
import logging
import tempfile
from pathlib import Path

from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, Request, UploadFile
from fastapi.responses import Response
from opentelemetry.trace import Status, StatusCode

from app.container import ServiceContainer
from app.telemetry import get_tracer
from app.exceptions import (
    ClassificationError,
    EvaluationError,
    ModelNotFoundError,
    TrainingError,
    ValidationError,
)
from app.models.api_errors import ErrorDetail
from app.schemas import ClassifyRequest

router = APIRouter()
_logger = logging.getLogger(__name__)


def get_container(request: Request) -> ServiceContainer:
    """FastAPI dependency: returns the service container from app.state."""
    return request.app.state.container


@router.get("/health")
def health(container: ServiceContainer = Depends(get_container)):
    """Service status. Includes whether the default model is loaded."""
    config = container.config
    default_id = config.get_default_model_id()
    loaded = container.loader.is_loaded(default_id)
    status = "loaded" if loaded else "not_loaded"
    return {"status": "ok", "model": status}


@router.get("/models", response_model=list)
def models(container: ServiceContainer = Depends(get_container)):
    """Lists available models: default first, then trained models."""
    items = container.model_registry_service.list_models()
    return [m.to_response_dict() for m in items]


@router.post("/classify", response_model=dict)
def classify(
    req: ClassifyRequest,
    container: ServiceContainer = Depends(get_container),
    modelId: str | None = Query(None, description="Model id (alternative to body)"),
):
    """
    Classifies a query. Expects body {"query": "...", "modelId": "default"} (modelId optional).
    Returns {"queryType": "COUNT_DOCUMENTS"} or similar. All JSON keys in camelCase.
    """
    query = (req.query or "").strip()
    body_model_id = req.modelId
    resolved_model_id = (modelId or body_model_id or "").strip() or None

    svc = container.classification_service
    tracer = get_tracer()
    span = tracer.start_span("classifier.classify") if tracer else None
    try:
        if span:
            span.set_attribute("query", (query or "")[:500])
            span.set_attribute("model_id", (resolved_model_id or "default")[:64])
        result = svc.classify(query=query, model_id=resolved_model_id or None)
        if span:
            span.set_attribute("query_type", (result.query_type or "")[:64])
            span.set_status(Status(StatusCode.OK))
        return result.to_response_dict()
    except ValidationError as e:
        if span:
            span.set_status(Status(StatusCode.ERROR, str(e)))
            span.record_exception(e)
        raise HTTPException(
            status_code=400,
            detail=ErrorDetail(code=e.code, message=e.message).to_response_dict(),
        ) from e
    except ModelNotFoundError as e:
        if span:
            span.set_status(Status(StatusCode.ERROR, str(e)))
            span.record_exception(e)
        raise HTTPException(
            status_code=404,
            detail=ErrorDetail(code=e.code, message=e.message).to_response_dict(),
        ) from e
    except ClassificationError as e:
        if span:
            span.set_status(Status(StatusCode.ERROR, str(e)))
            span.record_exception(e)
        raise HTTPException(
            status_code=503,
            detail=ErrorDetail(code=e.code, message=e.message).to_response_dict(),
        ) from e
    finally:
        if span:
            span.end()


@router.post("/train", response_model=dict)
async def train_endpoint(
    file: UploadFile = File(..., description="Excel dataset with columns Question and QueryType"),
    model_name: str = Form(..., description="Label/name for the trained model (tag)"),
    labels: str | None = Form(None, description="Optional JSON array of class names, e.g. [\"COUNT_DOCUMENTS\", \"SUMMARIZE_MEETING\"]"),
    labels_file: UploadFile | None = File(None, description="Optional labels file (one label per line, like query_type_labels.txt)"),
    epochs: int = Form(50),
    batch_size: int = Form(8),
    container: ServiceContainer = Depends(get_container),
):
    """Trains a new model from the uploaded dataset and registers it under the given name (tag). Optional labels define class order/whitelist."""
    if not file.filename or not file.filename.lower().endswith((".xlsx", ".xls")):
        raise HTTPException(
            status_code=400,
            detail=ErrorDetail(
                code="VALIDATION_ERROR",
                message="File must be an Excel file (.xlsx or .xls)",
            ).to_response_dict(),
        )
    class_names: list[str] | None = None
    if labels and labels.strip():
        try:
            class_names = json.loads(labels)
            if not isinstance(class_names, list) or not all(isinstance(x, str) for x in class_names):
                raise ValueError("labels must be a JSON array of strings")
        except json.JSONDecodeError as e:
            raise HTTPException(
                status_code=400,
                detail=ErrorDetail(code="VALIDATION_ERROR", message=f"Invalid labels JSON: {e}").to_response_dict(),
            ) from e
    elif labels_file and labels_file.filename:
        content = (await labels_file.read()).decode("utf-8", errors="replace")
        class_names = [line.strip() for line in content.splitlines() if line.strip()]
    tmp_path = None
    tracer = get_tracer()
    span = tracer.start_span("classifier.train") if tracer else None
    try:
        if span:
            span.set_attribute("model_name", (model_name or "")[:128])
            span.set_attribute("epochs", int(epochs))
            span.set_attribute("batch_size", int(batch_size))
        with tempfile.NamedTemporaryFile(suffix=Path(file.filename).suffix, delete=False) as tmp:
            content = await file.read()
            tmp.write(content)
            tmp_path = tmp.name
        svc = container.training_service
        result = svc.train(
            dataset_path=tmp_path,
            model_name=model_name,
            class_names=class_names,
            epochs=epochs,
            batch_size=batch_size,
        )
        if span:
            span.set_status(Status(StatusCode.OK))
        return result.to_response_dict()
    except ValidationError as e:
        if span:
            span.set_status(Status(StatusCode.ERROR, str(e)))
            span.record_exception(e)
        raise HTTPException(
            status_code=400,
            detail=ErrorDetail(code=e.code, message=e.message).to_response_dict(),
        ) from e
    except TrainingError as e:
        if span:
            span.set_status(Status(StatusCode.ERROR, str(e)))
            span.record_exception(e)
        raise HTTPException(
            status_code=500,
            detail=ErrorDetail(code=e.code, message=e.message).to_response_dict(),
        ) from e
    except Exception as e:
        if span:
            span.set_status(Status(StatusCode.ERROR, str(e)))
            span.record_exception(e)
        _logger.exception("Training failed: %s", e)
        raise HTTPException(
            status_code=500,
            detail=ErrorDetail(
                code="TRAINING_ERROR",
                message="Training failed",
                details={"error": str(e)},
            ).to_response_dict(),
        ) from e
    finally:
        if span:
            span.end()
        if tmp_path:
            Path(tmp_path).unlink(missing_ok=True)


# --- Evaluation (metrics + images for webapp) ---

@router.post("/evaluate", response_model=dict)
async def evaluate_endpoint(
    modelId: str | None = Query(None, description="Model tag to evaluate; default model if omitted"),
    includeImages: bool = Query(True, description="Include base64 PNG images in response"),
    file: UploadFile | None = File(None, description="Optional evaluation dataset Excel; uses default if omitted"),
    container: ServiceContainer = Depends(get_container),
):
    """
    Evaluates a model by tag on an evaluation dataset. Returns classification report, confusion matrix,
    and optionally base64-encoded PNG images (classification report table + confusion matrix) for
    webapp display or download.
    """
    tmp_path = None
    eval_path = None
    if file and file.filename and file.filename.lower().endswith((".xlsx", ".xls")):
        try:
            with tempfile.NamedTemporaryFile(suffix=Path(file.filename).suffix, delete=False) as tmp:
                content = await file.read()
                tmp.write(content)
                tmp_path = tmp.name
            eval_path = tmp_path
        except Exception as e:
            _logger.exception("Failed to save uploaded eval file: %s", e)
    tracer = get_tracer()
    span = tracer.start_span("classifier.evaluate") if tracer else None
    try:
        if span:
            span.set_attribute("model_id", (modelId or "default")[:64])
            span.set_attribute("include_images", bool(includeImages))
        svc = container.evaluation_service
        result = svc.evaluate(
            model_id=modelId or None,
            eval_dataset_path=eval_path,
            include_images=includeImages,
        )
        if span:
            span.set_status(Status(StatusCode.OK))
        return result.to_response_dict(include_images_base64=includeImages)
    except ModelNotFoundError as e:
        if span:
            span.set_status(Status(StatusCode.ERROR, str(e)))
            span.record_exception(e)
        raise HTTPException(
            status_code=404,
            detail=ErrorDetail(code=e.code, message=e.message).to_response_dict(),
        ) from e
    except EvaluationError as e:
        if span:
            span.set_status(Status(StatusCode.ERROR, str(e)))
            span.record_exception(e)
        raise HTTPException(
            status_code=400,
            detail=ErrorDetail(code=e.code, message=f"Evaluation error: {e.message}").to_response_dict(),
        ) from e
    finally:
        if span:
            span.end()
        if tmp_path:
            Path(tmp_path).unlink(missing_ok=True)


@router.get("/evaluate/{model_id}/report.png", response_class=Response)
def evaluate_report_image(model_id: str, container: ServiceContainer = Depends(get_container)):
    """Returns the classification report heatmap PNG for the given model (uses default eval dataset)."""
    svc = container.evaluation_service
    try:
        result = svc.evaluate(model_id=model_id, include_images=True)
        if not result.classification_report_image_bytes:
            raise HTTPException(status_code=500, detail="Image not generated")
        return Response(
            content=result.classification_report_image_bytes,
            media_type="image/png",
            headers={"Content-Disposition": "inline; filename=classification_report.png"},
        )
    except ModelNotFoundError as e:
        raise HTTPException(
            status_code=404,
            detail=ErrorDetail(code=e.code, message=e.message).to_response_dict(),
        ) from e
    except EvaluationError as e:
        raise HTTPException(
            status_code=400,
            detail=ErrorDetail(code=e.code, message=f"Evaluation error: {e.message}").to_response_dict(),
        ) from e


@router.get("/evaluate/{model_id}/confusion.png", response_class=Response)
def evaluate_confusion_image(model_id: str, container: ServiceContainer = Depends(get_container)):
    """Returns the confusion matrix heatmap PNG for the given model (uses default eval dataset)."""
    svc = container.evaluation_service
    try:
        result = svc.evaluate(model_id=model_id, include_images=True)
        if not result.confusion_matrix_image_bytes:
            raise HTTPException(status_code=500, detail="Image not generated")
        return Response(
            content=result.confusion_matrix_image_bytes,
            media_type="image/png",
            headers={"Content-Disposition": "inline; filename=confusion_matrix.png"},
        )
    except ModelNotFoundError as e:
        raise HTTPException(
            status_code=404,
            detail=ErrorDetail(code=e.code, message=e.message).to_response_dict(),
        ) from e
    except EvaluationError as e:
        raise HTTPException(
            status_code=400,
            detail=ErrorDetail(code=e.code, message=f"Evaluation error: {e.message}").to_response_dict(),
        ) from e
