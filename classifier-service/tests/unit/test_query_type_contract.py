"""Unit tests for shared Java QueryType contract helpers."""

from __future__ import annotations

import pytest

from app.query_type_contract import (
    JAVA_QUERY_TYPE_ORDER,
    JAVA_QUERY_TYPES,
    LEGACY_TRAINING_LABEL_MAP,
    canonical_class_order,
    label_set_hash,
    validate_loaded_labels,
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


def test_validate_loaded_labels_rejects_unknown():
    with pytest.raises(ValueError, match="unsupported labels"):
        validate_loaded_labels(["COUNT_DOCUMENTS", "NOT_A_TYPE"])


def test_canonical_class_order_matches_java_declaration():
    shuffled = ["COMPARE", "COUNT_DOCUMENTS", "BOOLEAN_QUERY"]
    ordered = canonical_class_order(shuffled)
    assert ordered.index("COUNT_DOCUMENTS") < ordered.index("BOOLEAN_QUERY")
    assert ordered.index("BOOLEAN_QUERY") < ordered.index("COMPARE")
    for name in ordered:
        assert name in JAVA_QUERY_TYPES


def test_label_set_hash_is_stable():
    assert label_set_hash(["A", "B"]) == label_set_hash(["A", "B"])
    assert label_set_hash(["A", "B"]) != label_set_hash(["B", "A"])


def test_java_query_type_order_covers_all_labels():
    assert set(JAVA_QUERY_TYPE_ORDER) == set(JAVA_QUERY_TYPES)
