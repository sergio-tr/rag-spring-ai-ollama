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


def label_set_hash(labels: list[str]) -> str:
    """Stable short hash for a model label ordering."""
    import hashlib

    payload = ",".join(labels)
    return hashlib.sha256(payload.encode("utf-8")).hexdigest()[:16]


def validate_loaded_labels(labels: list[str]) -> list[str]:
    """Ensures every label is a known Java QueryType; raises ValueError on unknown entries."""
    if not labels:
        raise ValueError("labels file must not be empty")
    unknown = [label for label in labels if label not in JAVA_QUERY_TYPES]
    if unknown:
        raise ValueError(f"unsupported labels (not in Java QueryType enum): {unknown}")
    return labels


def canonical_class_order(class_names: list[str]) -> list[str]:
    """Orders model class names using the shared Java enum declaration order."""
    present = set(class_names)
    ordered = [name for name in JAVA_QUERY_TYPE_ORDER if name in present]
    extras = [name for name in class_names if name not in ordered]
    return ordered + extras


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
