"""
Shared helpers for regression baseline capture/check (no HTTP here).
Used by capture_baseline.py / check_baseline.py and by pytest in this package.
"""

from __future__ import annotations

from pathlib import Path
from typing import Any


def service_root() -> Path:
    """classifier-service/ (parent of tests/)."""
    return Path(__file__).resolve().parent.parent.parent


def default_questions_path() -> Path:
    return Path(__file__).resolve().parent / "questions.txt"


def default_baseline_json_path() -> Path:
    return service_root() / "docs" / "classifier_regression_baseline.json"


def read_questions(path: Path) -> list[str]:
    out: list[str] = []
    with path.open("r", encoding="utf-8") as f:
        for raw in f:
            s = raw.strip()
            if not s or s.startswith("#"):
                continue
            out.append(s)
    return out


def mismatches_vs_baseline(
    classifications: dict[str, Any],
    actual_by_question: dict[str, str | None],
) -> list[dict[str, Any]]:
    """
    Compare baseline entries (per question: expected queryType) vs actual queryType strings.
    classifications: baseline JSON "classifications" map question -> { "queryType": ... }
    """
    mismatches: list[dict[str, Any]] = []
    for q, entry in classifications.items():
        expected = entry.get("queryType") if isinstance(entry, dict) else None
        actual = actual_by_question.get(q)
        if actual != expected:
            mismatches.append(
                {
                    "question": q,
                    "expected": expected,
                    "actual": actual,
                }
            )
    return mismatches
