"""
Default labels file must be a 1:1 bijection with the backend Java QueryType enum.
"""

from __future__ import annotations

from pathlib import Path

import pytest

from app.config import Config
from app.query_type_contract import JAVA_QUERY_TYPES, JAVA_QUERY_TYPE_ORDER, read_labels_file_lines


def test_default_labels_file_lines_are_valid_query_types():
    path = Path(Config().get_default_labels_path())
    if not path.is_file():
        pytest.skip(f"No labels file at {path}")
    lines = read_labels_file_lines(str(path))
    assert lines, "labels file should not be empty"
    for label in lines:
        assert label in JAVA_QUERY_TYPES, f"Unknown label (not in Java enum): {label!r}"


def test_default_labels_file_is_bijection_with_java_query_type_enum():
    """M6: exactly 12 labels, each unique, covering the full Java enum."""
    path = Path(Config().get_default_labels_path())
    if not path.is_file():
        pytest.skip(f"No labels file at {path}")
    lines = read_labels_file_lines(str(path))
    assert len(lines) == len(JAVA_QUERY_TYPE_ORDER), (
        f"expected {len(JAVA_QUERY_TYPE_ORDER)} labels, got {len(lines)}: {lines}"
    )
    assert len(set(lines)) == len(lines), f"duplicate labels in {path}"
    assert set(lines) == JAVA_QUERY_TYPES


def test_java_query_type_order_matches_enum_cardinality():
    assert len(JAVA_QUERY_TYPE_ORDER) == 12
    assert JAVA_QUERY_TYPES == frozenset(JAVA_QUERY_TYPE_ORDER)
