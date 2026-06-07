"""
Canonical QueryType labels shared with rag-service Java enum com.uniovi.rag.domain.model.QueryType.
Order matches Java declaration for default model training.
"""
from __future__ import annotations

JAVA_QUERY_TYPE_ORDER: tuple[str, ...] = (
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
)

JAVA_QUERY_TYPES: frozenset[str] = frozenset(JAVA_QUERY_TYPE_ORDER)

LEGACY_TRAINING_LABEL_MAP: dict[str, str] = {
    "BOOLEAN_VERIFICATION": "BOOLEAN_QUERY",
    "COMPARE_VALUES": "COMPARE",
    "GET_LITERAL_FIELD": "GET_FIELD",
    "LIST_ENTITIES": "EXTRACT_ENTITIES",
}


def validate_query_type_label(label: str) -> str:
    """Returns the label if it is a known Java QueryType; raises ValueError otherwise."""
    normalized = (label or "").strip()
    if not normalized:
        raise ValueError("query type label must be non-empty")
    if normalized not in JAVA_QUERY_TYPES:
        raise ValueError(f"unknown query type label (not in Java enum): {normalized!r}")
    return normalized


def read_labels_file_lines(path: str) -> list[str]:
    """Reads non-empty, non-comment lines from a labels file."""
    from pathlib import Path

    p = Path(path)
    if not p.is_file():
        return []
    return [
        ln.strip()
        for ln in p.read_text(encoding="utf-8").splitlines()
        if ln.strip() and not ln.strip().startswith("#")
    ]
