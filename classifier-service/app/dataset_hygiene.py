"""Dataset hygiene helpers: question normalization and train/eval leakage detection."""

from __future__ import annotations

import json
import re
from pathlib import Path
from typing import Any

import pandas as pd

from app.dataset_columns import normalize_excel_classification_columns
from app.query_type_contract import JAVA_QUERY_TYPE_ORDER


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
    """Load Question/QueryType dataset from .xlsx or .csv (question/query_type columns)."""
    p = Path(path)
    if p.suffix.lower() == ".csv":
        df = pd.read_csv(p)
        rename: dict[str, str] = {}
        if "question" in df.columns and "Question" not in df.columns:
            rename["question"] = "Question"
        if "query_type" in df.columns and "QueryType" not in df.columns:
            rename["query_type"] = "QueryType"
        if rename:
            df = df.rename(columns=rename)
        return normalize_excel_classification_columns(df)
    return normalize_excel_classification_columns(pd.read_excel(p))


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


def load_gold_subset_questions(path: str) -> pd.DataFrame:
    """Load gold-subset manifest entries as a Question/QueryType dataframe."""
    with open(path, "r", encoding="utf-8") as f:
        manifest = json.load(f)
    rows = [
        {
            "Question": str(entry["question"]),
            "QueryType": str(entry["queryTypeExpected"]),
        }
        for entry in manifest.get("entries", [])
    ]
    return pd.DataFrame(rows)


def find_train_gold_overlaps(train_df: pd.DataFrame, gold_df: pd.DataFrame) -> list[dict[str, Any]]:
    """Returns normalized questions present in both train and gold-subset probe."""
    train_norm = normalized_questions(train_df)
    gold_norm = normalized_questions(gold_df)
    train_qcol = "Question" if "Question" in train_df.columns else question_column(train_df)
    gold_qcol = "Question" if "Question" in gold_df.columns else question_column(gold_df)

    overlap_norms = set(train_norm) & set(gold_norm)
    overlap_norms.discard("")

    rows: list[dict[str, Any]] = []
    for norm in sorted(overlap_norms):
        t_idx = train_norm[train_norm == norm].index[0]
        g_idx = gold_norm[gold_norm == norm].index[0]
        rows.append(
            {
                "normalized": norm,
                "train_question": str(train_df.loc[t_idx, train_qcol]),
                "gold_question": str(gold_df.loc[g_idx, gold_qcol]),
                "train_query_type": str(train_df.loc[t_idx, "QueryType"]).strip(),
                "gold_query_type": str(gold_df.loc[g_idx, "QueryType"]).strip(),
            }
        )
    return rows


def assert_training_dataset_hygiene(
    train_df: pd.DataFrame,
    *,
    eval_path: str | None = None,
    gold_path: str | None = None,
) -> None:
    """Raise ValueError when train data leaks into held-out eval or gold-subset probes."""
    if train_df.empty:
        raise ValueError("Training dataset is empty")

    qcol = question_column(train_df)
    empty = train_df[qcol].astype(str).str.strip() == ""
    if empty.any():
        raise ValueError(f"Training dataset has {int(empty.sum())} empty questions")

    invalid = ~train_df["QueryType"].astype(str).isin(JAVA_QUERY_TYPE_ORDER)
    if invalid.any():
        bad = sorted(train_df.loc[invalid, "QueryType"].astype(str).unique().tolist())
        raise ValueError(f"Training dataset has invalid QueryType labels: {bad}")

    norms = normalized_questions(train_df)
    if norms.duplicated().any():
        dups = int(norms.duplicated().sum())
        raise ValueError(f"Training dataset has {dups} normalized duplicate questions")

    if eval_path:
        eval_df = load_classification_dataset(eval_path)
        overlaps = find_train_eval_overlaps(train_df, eval_df)
        if overlaps:
            raise ValueError(
                f"Train/eval leakage: {len(overlaps)} normalized overlap(s); "
                f"first={overlaps[0]['train_question']!r}"
            )

    if gold_path:
        gold_df = load_gold_subset_questions(gold_path)
        gold_overlaps = find_train_gold_overlaps(train_df, gold_df)
        if gold_overlaps:
            raise ValueError(
                f"Train/gold leakage: {len(gold_overlaps)} normalized overlap(s); "
                f"first={gold_overlaps[0]['train_question']!r}"
            )
