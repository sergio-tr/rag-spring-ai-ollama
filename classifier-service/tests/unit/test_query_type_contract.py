"""Unit tests for shared Java QueryType contract helpers."""

from __future__ import annotations

import pytest

from app.query_type_contract import (
    JAVA_QUERY_TYPES,
    LEGACY_TRAINING_LABEL_MAP,
    validate_query_type_label,
)


def test_validate_accepts_all_java_labels():
    for label in sorted(JAVA_QUERY_TYPES):
        assert validate_query_type_label(label) == label


def test_validate_rejects_unknown_and_empty():
    with pytest.raises(ValueError, match="non-empty"):
        validate_query_type_label("")
    with pytest.raises(ValueError, match="not in Java enum"):
        validate_query_type_label("NOT_A_QUERY_TYPE")
    with pytest.raises(ValueError, match="not in Java enum"):
        validate_query_type_label("LIST_ENTITIES")


def test_legacy_map_targets_java_enum():
    for legacy, canonical in LEGACY_TRAINING_LABEL_MAP.items():
        assert canonical in JAVA_QUERY_TYPES
        assert legacy not in JAVA_QUERY_TYPES
