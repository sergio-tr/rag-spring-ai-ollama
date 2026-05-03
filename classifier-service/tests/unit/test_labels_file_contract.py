"""
If models/default/labels.txt exists, every label must match the backend Java QueryType enum set.
Keeps classifier-service and rag-service in sync for classification outputs.
"""

from __future__ import annotations

from pathlib import Path

import pytest

from app.config import Config

# Same set as rag-service QueryType / tests/test_api.py
QUERY_TYPES_ALLOWED = {
    "COUNT_DOCUMENTS",
    "EXTRACT_ENTITIES",
    "COUNT_AND_EXPLAIN",
    "FIND_PARAGRAPH",
    "DECISION_EXTRACTION",
    "GET_DURATION",
    "GET_FIELD",
    "SUMMARIZE_TOPIC",
    "SUMMARIZE_MEETING",
    "BOOLEAN_QUERY",
    "FILTER_AND_LIST",
    "COMPARE",
}


def test_default_labels_file_lines_are_valid_query_types():
    path = Path(Config().get_default_labels_path())
    if not path.is_file():
        pytest.skip(f"No labels file at {path}")
    lines = [
        ln.strip()
        for ln in path.read_text(encoding="utf-8").splitlines()
        if ln.strip() and not ln.strip().startswith("#")
    ]
    assert lines, "labels file should not be empty"
    for label in lines:
        assert label in QUERY_TYPES_ALLOWED, f"Unknown label (not in Java enum): {label!r}"
