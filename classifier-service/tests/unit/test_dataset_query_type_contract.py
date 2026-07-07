"""Training and evaluation Excel datasets must use Java QueryType labels only."""

from __future__ import annotations

from pathlib import Path

import pandas as pd
import pytest

from app.config import Config
from app.dataset_columns import normalize_excel_classification_columns
from app.query_type_contract import JAVA_QUERY_TYPES, LEGACY_TRAINING_LABEL_MAP


@pytest.mark.parametrize(
    "filename",
    ["basic_dataset_qa_clasificacion_final.xlsx", "evaluation_dataset.xlsx"],
)
def test_dataset_query_types_are_java_enum_labels(filename: str):
    data_dir = Path(Config().get_data_dir())
    path = data_dir / filename
    if not path.is_file():
        pytest.skip(f"Missing dataset {path}")
    df = normalize_excel_classification_columns(pd.read_excel(path))
    assert "QueryType" in df.columns
    labels = df["QueryType"].astype(str).str.strip().unique().tolist()
    legacy_hits = [l for l in labels if l in LEGACY_TRAINING_LABEL_MAP]
    assert not legacy_hits, f"{filename} still contains legacy labels: {legacy_hits}"
    unknown = sorted({l for l in labels if l not in JAVA_QUERY_TYPES})
    assert not unknown, f"{filename} unknown QueryType values: {unknown}"
