"""
HTTP endpoints: health, models, classify, train, evaluate.
Delegates to services from the container; maps exceptions to structured error responses.
"""
import logging
from typing import Annotated

from fastapi import APIRouter, Depends, File, Form, HTTPException, Query, Request, UploadFile
from fastapi.responses import Response
from opentelemetry.trace import Status, StatusCode

from app.container import ServiceContainer
from app.exceptions import (
    ClassificationError,
    EvaluationError,
    ModelNotFoundError,
    TrainingError,
    ValidationError,
)
from app.http_route_support import (
    maybe_save_eval_temp,
    parse_labels_json,
    read_class_names_from_labels_file,
    require_excel_upload,
    span_end,
    span_record_error,
    span_set_classify_ok,
    span_set_classify_start,
    write_upload_to_temp,
)
from app.models.api_errors import ErrorDetail
from app.schemas import ClassifyRequest
from app.telemetry import get_tracer

router = APIRouter()
_logger = logging.getLogger(__name__)

# --- OpenAPI: HTTPException status codes raised by handlers below ---

CLASSIFY_OPENAPI_RESPONSES = {
    400: {"description": "Validation error (e.g. empty or invalid query)."},
    404: {"description": "Requested model was not found."},
    503: {"description": "Classification failed or model unavailable."},
}

TRAIN_OPENAPI_RESPONSES = {
    400: {"description": "Validation error (file type or labels JSON)."},
    500: {"description": "Training failed."},
}

EVAL_OPENAPI_RESPONSES = {
    400: {"description": "Evaluation error."},
    404: {"description": "Model not found."},
}

EVAL_PNG_OPENAPI_RESPONSES = {
    400: {"description": "Evaluation error."},
    404: {"description": "Model not found."},
    500: {"description": "Image not generated."},
}


def get_container(request: Request) -> ServiceContainer:
    """FastAPI dependency: returns the service container from app.state."""
    return request.app.state.container


ServiceContainerDep = Annotated[ServiceContainer, Depends(get_container)]

# Defaults belong on the parameter (`= ...`), not inside Query/Form/File, when using Annotated (FastAPI).

ClassifyModelIdQuery = Annotated[
    str | None,
    Query(alias="modelId", description="Model id (alternative to body)"),
]

TrainExcelUpload = Annotated[
    UploadFile,
    File(..., description="Excel dataset with columns Question and QueryType"),
]
TrainModelNameForm = Annotated[str, Form(..., description="Label/name for the trained model (tag)")]
TrainLabelsJsonForm = Annotated[
    str | None,
    Form(
        description='Optional JSON array of class names, e.g. ["COUNT_DOCUMENTS", "SUMMARIZE_MEETING"]',
    ),
]
TrainLabelsFileUpload = Annotated[
    UploadFile | None,
    File(description="Optional labels file (one label per line, like query_type_labels.txt)"),
]
TrainEpochsForm = Annotated[int, Form(description="Training epochs (default 50)")]
TrainBatchSizeForm = Annotated[int, Form(description="Batch size (default 8)")]
TrainOwnerIdForm = Annotated[
    str | None,
    Form(
        alias="owner_id",
        description="Optional RAG user id stored in metadata.json (audit on shared MODELS_DIR)",
    ),
]

EvaluateModelIdQuery = Annotated[
    str | None,
    Query(alias="modelId", description="Model tag to evaluate; default model if omitted"),
]
EvaluateIncludeImagesQuery = Annotated[
    bool,
    Query(alias="includeImages", description="Include base64 PNG images in response"),
]
EvaluateDatasetFile = Annotated[
    UploadFile | None,
    File(description="Optional evaluation dataset Excel; uses default if omitted"),
]


@router.get("/health")
def health(container: ServiceContainerDep):
    """Service status. Includes whether the default model is loaded."""
    config = container.config
    default_id = config.get_default_model_id()
    loaded = container.loader.is_loaded(default_id)
    status = "loaded" if loaded else "not_loaded"
    return {"status": "ok", "model": status}


@router.get("/models", response_model=list)
def models(container: ServiceContainerDep):
    """Lists available models: default first, then trained models."""
    items = container.model_registry_service.list_models()
    return [m.to_response_dict() for m in items]


@router.post("/classify", response_model=dict, responses=CLASSIFY_OPENAPI_RESPONSES)
def classify(
    req: ClassifyRequest,
    container: ServiceContainerDep,
    model_id: ClassifyModelIdQuery = None,
):
    """
    Classifies a query. Expects body {"query": "...", "modelId": "default"} (modelId optional).
    Returns {"queryType": "COUNT_DOCUMENTS"} or similar. All JSON keys in camelCase.
    """
    query = (req.query or "").strip()
    body_model_id = req.modelId
    resolved_model_id = (model_id or body_model_id or "").strip() or None

    svc = container.classification_service
    tracer = get_tracer()
    span = tracer.start_span("classifier.classify") if tracer else None
    try:
        span_set_classify_start(span, query, resolved_model_id)
        result = svc.classify(query=query, model_id=resolved_model_id or None)
        span_set_classify_ok(span, result.query_type)
        return result.to_response_dict()
    except ValidationError as e:
        span_record_error(span, e)
        raise HTTPException(
            status_code=400,
            detail=ErrorDetail(code=e.code, message=e.message).to_response_dict(),
        ) from e
    except ModelNotFoundError as e:
        span_record_error(span, e)
        raise HTTPException(
            status_code=404,
            detail=ErrorDetail(code=e.code, message=e.message).to_response_dict(),
        ) from e
    except ClassificationError as e:
        span_record_error(span, e)
        raise HTTPException(
            status_code=503,
            detail=ErrorDetail(code=e.code, message=e.message).to_response_dict(),
        ) from e
    finally:
        span_end(span)


@router.post("/train", response_model=dict, responses=TRAIN_OPENAPI_RESPONSES)
async def train_endpoint(
    container: ServiceContainerDep,
    file: TrainExcelUpload,
    model_name: TrainModelNameForm,
    labels: TrainLabelsJsonForm = None,
    labels_file: TrainLabelsFileUpload = None,
    epochs: TrainEpochsForm = 50,
    batch_size: TrainBatchSizeForm = 8,
    owner_id: TrainOwnerIdForm = None,
):
    """Trains a new model from the uploaded dataset and registers it under the given name (tag). Optional labels define class order/whitelist."""
    require_excel_upload(file)

    class_names: list[str] | None = None
    if labels and labels.strip():
        class_names = parse_labels_json(labels)
    elif labels_file and labels_file.filename:
        class_names = await read_class_names_from_labels_file(labels_file)

    tmp_path = None
    tracer = get_tracer()
    span = tracer.start_span("classifier.train") if tracer else None
    try:
        tmp_path = await write_upload_to_temp(file, temp_prefix="clf-train")
        if span:
            span.set_attribute("model_name", (model_name or "")[:128])
            span.set_attribute("epochs", int(epochs))
            span.set_attribute("batch_size", int(batch_size))
        svc = container.training_service
        result = svc.train(
            dataset_path=str(tmp_path),
            model_name=model_name,
            class_names=class_names,
            epochs=epochs,
            batch_size=batch_size,
            owner_id=(owner_id.strip() if owner_id and owner_id.strip() else None),
        )
        if span:
            span.set_status(Status(StatusCode.OK))
        return result.to_response_dict()
    except ValidationError as e:
        span_record_error(span, e)
        raise HTTPException(
            status_code=400,
            detail=ErrorDetail(code=e.code, message=e.message).to_response_dict(),
        ) from e
    except TrainingError as e:
        span_record_error(span, e)
        raise HTTPException(
            status_code=500,
            detail=ErrorDetail(code=e.code, message=e.message).to_response_dict(),
        ) from e
    except Exception as e:
        span_record_error(span, e)
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
        span_end(span)
        if tmp_path is not None:
            tmp_path.unlink(missing_ok=True)


@router.post("/evaluate", response_model=dict, responses=EVAL_OPENAPI_RESPONSES)
async def evaluate_endpoint(
    container: ServiceContainerDep,
    model_id: EvaluateModelIdQuery = None,
    include_images: EvaluateIncludeImagesQuery = True,
    file: EvaluateDatasetFile = None,
):
    """
    Evaluates a model by tag on an evaluation dataset. Returns classification report, confusion matrix,
    and optionally base64-encoded PNG images (classification report table + confusion matrix) for
    webapp display or download.
    """
    tmp_path = await maybe_save_eval_temp(file)
    tracer = get_tracer()
    span = tracer.start_span("classifier.evaluate") if tracer else None
    try:
        if span:
            span.set_attribute("model_id", (model_id or "default")[:64])
            span.set_attribute("include_images", bool(include_images))
        svc = container.evaluation_service
        eval_arg = str(tmp_path) if tmp_path else None
        result = svc.evaluate(
            model_id=model_id or None,
            eval_dataset_path=eval_arg,
            include_images=include_images,
        )
        if span:
            span.set_status(Status(StatusCode.OK))
        return result.to_response_dict(include_images_base64=include_images)
    except ModelNotFoundError as e:
        span_record_error(span, e)
        raise HTTPException(
            status_code=404,
            detail=ErrorDetail(code=e.code, message=e.message).to_response_dict(),
        ) from e
    except EvaluationError as e:
        span_record_error(span, e)
        raise HTTPException(
            status_code=400,
            detail=ErrorDetail(code=e.code, message=f"Evaluation error: {e.message}").to_response_dict(),
        ) from e
    finally:
        span_end(span)
        if tmp_path:
            tmp_path.unlink(missing_ok=True)


def _evaluate_png_response(container: ServiceContainer, model_id: str, *, report: bool) -> Response:
    svc = container.evaluation_service
    try:
        result = svc.evaluate(model_id=model_id, include_images=True)
        raw = result.classification_report_image_bytes if report else result.confusion_matrix_image_bytes
        if not raw:
            raise HTTPException(status_code=500, detail="Image not generated")
        fname = "classification_report.png" if report else "confusion_matrix.png"
        return Response(
            content=raw,
            media_type="image/png",
            headers={"Content-Disposition": f"inline; filename={fname}"},
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


@router.get("/evaluate/{model_id}/report.png", response_class=Response, responses=EVAL_PNG_OPENAPI_RESPONSES)
def evaluate_report_image(model_id: str, container: ServiceContainerDep):
    """Returns the classification report heatmap PNG for the given model (uses default eval dataset)."""
    return _evaluate_png_response(container, model_id, report=True)


@router.get("/evaluate/{model_id}/confusion.png", response_class=Response, responses=EVAL_PNG_OPENAPI_RESPONSES)
def evaluate_confusion_image(model_id: str, container: ServiceContainerDep):
    """Returns the confusion matrix heatmap PNG for the given model (uses default eval dataset)."""
    return _evaluate_png_response(container, model_id, report=False)
