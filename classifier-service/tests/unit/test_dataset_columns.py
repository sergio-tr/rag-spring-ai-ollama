"""Tests for Excel column normalization."""

from __future__ import annotations

import pandas as pd

from app.dataset_columns import normalize_excel_classification_columns


def test_renames_pregunta_to_question():
    df = pd.DataFrame({"Pregunta": ["a"], "QueryType": ["X"]})
    out = normalize_excel_classification_columns(df)
    assert "Question" in out.columns
    assert out["Question"].iloc[0] == "a"


def test_keeps_question_when_present():
    df = pd.DataFrame({"Question": ["b"], "QueryType": ["Y"]})
    out = normalize_excel_classification_columns(df)
    assert list(out.columns) == ["Question", "QueryType"]
