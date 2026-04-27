"""
Shared helpers for HTTP routes: OpenTelemetry span handling, async temp files, validation.
Keeps cognitive complexity low in router functions (Sonar).
"""
from __future__ import annotations

import json
import logging
import tempfile
import uuid
from pathlib import Path
import aiofiles
from fastapi import HTTPException, UploadFile
from opentelemetry.trace import Span, Status, StatusCode

from app.models.api_errors import ErrorDetail

_logger = logging.getLogger(__name__)


def span_set_classify_start(span: Span | None, query: str, resolved_model_id: str | None) -> None:
    if not span:
        return
    span.set_attribute("query", (query or "")[:500])
    span.set_attribute("model_id", (resolved_model_id or "default")[:64])


def span_set_classify_ok(span: Span | None, query_type: str | None) -> None:
    if not span:
        return
    span.set_attribute("query_type", (query_type or "")[:64])
    span.set_status(Status(StatusCode.OK))


def span_record_error(span: Span | None, exc: BaseException) -> None:
    if not span:
        return
    span.set_status(Status(StatusCode.ERROR, str(exc)))
    span.record_exception(exc)


def span_end(span: Span | None) -> None:
    if span:
        span.end()


def require_excel_upload(file: UploadFile) -> None:
    if not file.filename or not file.filename.lower().endswith((".xlsx", ".xls")):
        raise HTTPException(
            status_code=400,
            detail=ErrorDetail(
                code="VALIDATION_ERROR",
                message="File must be an Excel file (.xlsx or .xls)",
            ).to_response_dict(),
        )


def parse_labels_json(labels: str | None) -> list[str] | None:
    if not labels or not labels.strip():
        return None
    try:
        class_names = json.loads(labels)
    except json.JSONDecodeError as e:
        raise HTTPException(
            status_code=400,
            detail=ErrorDetail(code="VALIDATION_ERROR", message=f"Invalid labels JSON: {e}").to_response_dict(),
        ) from e
    if not isinstance(class_names, list) or not all(isinstance(x, str) for x in class_names):
        raise HTTPException(
            status_code=400,
            detail=ErrorDetail(
                code="VALIDATION_ERROR",
                message="labels must be a JSON array of strings",
            ).to_response_dict(),
        )
    return class_names


async def read_class_names_from_labels_file(labels_file: UploadFile) -> list[str]:
    content = (await labels_file.read()).decode("utf-8", errors="replace")
    return [line.strip() for line in content.splitlines() if line.strip()]


async def write_upload_to_temp(file: UploadFile, *, temp_prefix: str) -> Path:
    """Writes upload body to a unique file under the system temp directory (async I/O)."""
    suffix = Path(file.filename or "data").suffix or ".bin"
    path = Path(tempfile.gettempdir()) / f"{temp_prefix}-{uuid.uuid4().hex}{suffix}"
    body = await file.read()
    async with aiofiles.open(path, "wb") as out:
        await out.write(body)
    return path


async def maybe_save_eval_temp(file: UploadFile | None) -> Path | None:
    if not file or not file.filename or not file.filename.lower().endswith((".xlsx", ".xls")):
        return None
    try:
        return await write_upload_to_temp(file, temp_prefix="clf-eval")
    except Exception as e:
        _logger.exception("Failed to save uploaded eval file: %s", e)
        return None


def unwrap_http_exception(exc: HTTPException, span: "Span | None") -> HTTPException:
    span_record_error(span, exc)
    return exc

