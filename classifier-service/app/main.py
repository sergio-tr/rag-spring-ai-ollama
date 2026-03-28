"""
FastAPI application: lifespan (load default model), router registration, error handlers.
No business logic here; services are obtained from the container.
"""
import logging
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse

from app.config import Config
from app.container import ServiceContainer
from app.models.api_errors import ErrorDetail
from app.routes import router
from app.telemetry import setup_telemetry

_logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    """On startup: load the default model (tag 'default') via the container."""
    container: ServiceContainer = app.state.container
    config = container.config
    try:
        default_id = config.get_default_model_id()
        if not container.loader.is_loaded(default_id):
            container.loader.load_by_id(default_id)
        _logger.info("Default model '%s' loaded", default_id)
    except Exception as e:
        _logger.warning("Startup load failed (will try on first request): %s", e)
    yield


def create_app() -> FastAPI:
    config = Config()
    container = ServiceContainer(config)
    app = FastAPI(title="Classifier Service (RAG Query Classifier)", lifespan=lifespan)
    app.state.container = container
    app.include_router(router)
    setup_telemetry(app)

    @app.exception_handler(RequestValidationError)
    async def validation_exception_handler(request: Request, exc: RequestValidationError):
        """Return 422 with the same envelope as other errors: success + error object."""
        errors = exc.errors() if hasattr(exc, "errors") else []
        err = ErrorDetail(
            code="VALIDATION_ERROR",
            message="Request validation failed",
            details={"errors": errors},
        ).to_response_dict()
        return JSONResponse(status_code=422, content={"success": False, "error": err})

    @app.exception_handler(HTTPException)
    async def http_exception_handler(request: Request, exc: HTTPException):
        """Normalize HTTPException (400/404/503 from routes) to { success, error }."""
        detail = exc.detail
        if isinstance(detail, dict):
            error_body = detail
        else:
            error_body = {"code": "HTTP_ERROR", "message": str(detail)}
        return JSONResponse(status_code=exc.status_code, content={"success": False, "error": error_body})

    return app
