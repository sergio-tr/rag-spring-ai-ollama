"""
Entry point: creates the app from app.main and runs uvicorn.

Named ``uvicorn_entry`` (not ``main``) so coverage/Cobertura does not merge this file with ``app/main.py``.
"""
import os

import uvicorn

from app.config import Config
from app.main import create_app

app = create_app()

if __name__ == "__main__":
    # Bind to loopback by default (S8392); set UVICORN_HOST=0.0.0.0 for container networking.
    _host = os.environ.get("UVICORN_HOST", "127.0.0.1")  # pragma: no cover
    uvicorn.run(
        "uvicorn_entry:app",
        host=_host,
        port=Config().get_port(),
        reload=False,
    )  # pragma: no cover
