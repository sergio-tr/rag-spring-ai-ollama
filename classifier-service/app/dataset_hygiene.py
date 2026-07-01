"""Dataset hygiene helpers: question normalization and train/eval leakage detection."""

from __future__ import annotations

import re
from typing import Any

import pandas as pd

from app.dataset_columns import normalize_excel_classification_columns


def normalize_question(text: Any) -> str:
    """Lowercase, collapse whitespace, strip punctuation for overlap comparison."""
    if text is None or (isinstance(text, float) and pd.isna(text)):
        return ""
    s = str(text).strip().lower()
    s = re.sub(r"\s+", " ", s)
    s = re.sub(r"[^\w\sáéíóúüñ¿?¡!.,;:()\-]", "", s, flags=re.UNICODE)
    return s.strip()


def question_column(df: pd.DataFrame) -> str:
    if "Question" in df.columns:
        return "Question"
    if "Pregunta" in df.columns:
        return "Pregunta"
    raise ValueError("Dataset must have 'Question' or 'Pregunta' column")


def load_classification_dataset(path: str) -> pd.DataFrame:
    return normalize_excel_classification_columns(pd.read_excel(path))


def normalized_questions(df: pd.DataFrame) -> pd.Series:
    qcol = question_column(df)
    renamed = df
    if qcol == "Pregunta" and "Question" not in df.columns:
        renamed = df.rename(columns={"Pregunta": "Question"})
    return renamed["Question"].apply(normalize_question)


def find_train_eval_overlaps(train_df: pd.DataFrame, eval_df: pd.DataFrame) -> list[dict[str, Any]]:
    """Returns one entry per normalized question present in both train and eval."""
    train_norm = normalized_questions(train_df)
    eval_norm = normalized_questions(eval_df)
    train_qcol = "Question" if "Question" in train_df.columns else question_column(train_df)
    eval_qcol = "Question" if "Question" in eval_df.columns else question_column(eval_df)

    overlap_norms = set(train_norm) & set(eval_norm)
    overlap_norms.discard("")

    rows: list[dict[str, Any]] = []
    for norm in sorted(overlap_norms):
        t_idx = train_norm[train_norm == norm].index[0]
        e_idx = eval_norm[eval_norm == norm].index[0]
        t_qt = str(train_df.loc[t_idx, "QueryType"]).strip()
        e_qt = str(eval_df.loc[e_idx, "QueryType"]).strip()
        rows.append(
            {
                "normalized": norm,
                "train_question": str(train_df.loc[t_idx, train_qcol]),
                "eval_question": str(eval_df.loc[e_idx, eval_qcol]),
                "train_query_type": t_qt,
                "eval_query_type": e_qt,
                "label_match": t_qt == e_qt,
            }
        )
    return rows
