"""HTTP middleware: reject WebSocket upgrade attempts on plain REST routes."""

from __future__ import annotations

from starlette.middleware.base import BaseHTTPMiddleware
from starlette.requests import Request
from starlette.responses import JSONResponse


class RejectUpgradeRequestsMiddleware(BaseHTTPMiddleware):
    """
    classifier-service exposes HTTP JSON only. Clients that send Connection: Upgrade
    (WebSocket, SSE upgrade, etc.) must receive a structured JSON 400 instead of
    uvicorn's plain-text protocol errors.
    """

    async def dispatch(self, request: Request, call_next):
        upgrade = (request.headers.get("upgrade") or "").strip().lower()
        connection = (request.headers.get("connection") or "").strip().lower()
        if upgrade == "websocket" or ("upgrade" in connection and upgrade):
            return JSONResponse(
                status_code=400,
                content={
                    "success": False,
                    "error": {
                        "code": "HTTP_PROTOCOL_ERROR",
                        "message": "WebSocket upgrade is not supported on this HTTP API.",
                    },
                    "message": "WebSocket upgrade is not supported on this HTTP API.",
                },
            )
        return await call_next(request)
