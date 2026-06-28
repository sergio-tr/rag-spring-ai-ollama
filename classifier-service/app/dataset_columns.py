"""Normalize Excel column names for training/evaluation datasets (Spanish/English headers)."""

from __future__ import annotations

import pandas as pd


def normalize_excel_classification_columns(df: pd.DataFrame) -> pd.DataFrame:
    """
    Ensure a 'Question' text column exists. Maps Spanish 'Pregunta' to 'Question'.
    QueryType must remain as-is.
    """
    out = df.copy()
    if "Question" not in out.columns and "Pregunta" in out.columns:
        out = out.rename(columns={"Pregunta": "Question"})
    return out
