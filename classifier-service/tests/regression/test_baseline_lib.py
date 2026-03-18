"""Unit tests for regression harness helpers (no HTTP)."""

from __future__ import annotations

import importlib.util
import tempfile
from pathlib import Path

_REG = Path(__file__).resolve().parent


def _load_baseline_lib():
    spec = importlib.util.spec_from_file_location(
        "regression_baseline_lib",
        _REG / "baseline_lib.py",
    )
    m = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(m)
    return m


bl = _load_baseline_lib()


def test_service_root_points_to_classifier_service():
    root = bl.service_root()
    assert (root / "main.py").exists() or (root / "app").is_dir()


def test_default_paths_exist():
    assert bl.default_questions_path().exists()
    assert bl.default_baseline_json_path().parent.is_dir()


def test_read_questions_skips_empty_and_comments():
    with tempfile.NamedTemporaryFile("w", suffix=".txt", delete=False, encoding="utf-8") as f:
        f.write("# comment\n\n  line1  \nline2\n")
        p = f.name
    try:
        assert bl.read_questions(Path(p)) == ["line1", "line2"]
    finally:
        Path(p).unlink(missing_ok=True)


def test_mismatches_vs_baseline_empty_actual():
    classifications = {"q1": {"queryType": "COUNT_DOCUMENTS"}}
    assert bl.mismatches_vs_baseline(classifications, {}) == [
        {"question": "q1", "expected": "COUNT_DOCUMENTS", "actual": None}
    ]


def test_mismatches_vs_baseline_match():
    classifications = {
        "a": {"queryType": "COMPARE"},
        "b": {"queryType": "COUNT_DOCUMENTS"},
    }
    actual = {"a": "COMPARE", "b": "COUNT_DOCUMENTS"}
    assert bl.mismatches_vs_baseline(classifications, actual) == []


def test_mismatches_vs_baseline_mismatch():
    classifications = {"x": {"queryType": "SUMMARIZE_MEETING"}}
    assert bl.mismatches_vs_baseline(classifications, {"x": "COUNT_DOCUMENTS"}) == [
        {"question": "x", "expected": "SUMMARIZE_MEETING", "actual": "COUNT_DOCUMENTS"}
    ]


def test_questions_file_has_minimum_lines():
    qs = bl.read_questions(bl.default_questions_path())
    assert len(qs) >= 5
    assert all(len(q) > 0 for q in qs)


def test_questions_file_no_duplicate_lines():
    qs = bl.read_questions(bl.default_questions_path())
    assert len(qs) == len(set(qs))
