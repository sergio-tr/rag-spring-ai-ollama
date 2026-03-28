"""
Entry point: creates the app from app.main and runs uvicorn.
"""
import uvicorn

from app.config import Config
from app.main import create_app

app = create_app()

if __name__ == "__main__":
    uvicorn.run("main:app", host="0.0.0.0", port=Config().get_port(), reload=False)
