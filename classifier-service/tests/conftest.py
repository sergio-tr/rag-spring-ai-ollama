"""
Shared fixtures for classifier-service tests.
"""
import io
import os

import pandas as pd
import pytest

os.environ.setdefault("MODELS_DIR", "models")


@pytest.fixture
def client():
    """HTTP client for the FastAPI app."""
    from main import app
    from fastapi.testclient import TestClient
    return TestClient(app)


@pytest.fixture
def minimal_dataset_excel():
    """Minimal Excel with Question and QueryType columns for POST /train."""
    df = pd.DataFrame({
        "Question": [
            "How many documents?",
            "How many actas?",
            "Summarize the meeting",
            "Summarize the minutes",
            "How many meetings?",
        ],
        "QueryType": [
            "COUNT_DOCUMENTS",
            "COUNT_DOCUMENTS",
            "SUMMARIZE_MEETING",
            "SUMMARIZE_MEETING",
            "COUNT_DOCUMENTS",
        ],
    })
    buf = io.BytesIO()
    df.to_excel(buf, index=False, engine="openpyxl")
    buf.seek(0)
    return buf
