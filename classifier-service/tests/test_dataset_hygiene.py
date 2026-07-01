"""Dataset hygiene: no train/eval leakage, replacements documented, clean copies present."""

from __future__ import annotations

import json
from pathlib import Path

import pandas as pd
import pytest

from app.config import Config
from app.dataset_hygiene import find_train_eval_overlaps, load_classification_dataset, normalize_question
from app.query_type_contract import JAVA_QUERY_TYPES


def _data_dir() -> Path:
    return Path(Config().get_data_dir())


@pytest.fixture
def data_dir() -> Path:
    return _data_dir()


def test_canonical_datasets_exist(data_dir: Path):
    assert (data_dir / "basic_dataset_qa_clasificacion.xlsx").is_file()
    assert (data_dir / "evaluation_dataset.xlsx").is_file()


def test_clean_dataset_copies_exist(data_dir: Path):
    assert (data_dir / "basic_dataset_qa_clasificacion_clean.xlsx").is_file()
    assert (data_dir / "evaluation_dataset_clean.xlsx").is_file()


def test_zero_train_eval_question_overlaps(data_dir: Path):
    train_df = load_classification_dataset(data_dir / "basic_dataset_qa_clasificacion.xlsx")
    eval_df = load_classification_dataset(data_dir / "evaluation_dataset.xlsx")
    overlaps = find_train_eval_overlaps(train_df, eval_df)
    assert overlaps == [], f"train/eval leakage: {overlaps}"


def test_clean_copies_also_have_zero_overlaps(data_dir: Path):
    train_df = load_classification_dataset(data_dir / "basic_dataset_qa_clasificacion_clean.xlsx")
    eval_df = load_classification_dataset(data_dir / "evaluation_dataset_clean.xlsx")
    assert find_train_eval_overlaps(train_df, eval_df) == []


def test_replacements_documented(data_dir: Path):
    repl_path = data_dir / "backups" / "20250629" / "train_replacements.json"
    assert repl_path.is_file(), "missing train_replacements.json backup"
    replacements = json.loads(repl_path.read_text(encoding="utf-8"))
    assert len(replacements) == 11
    for entry in replacements:
        assert entry["QueryType"] in JAVA_QUERY_TYPES
        assert normalize_question(entry["Question"])


def test_eval_row_count_unchanged(data_dir: Path):
    eval_df = load_classification_dataset(data_dir / "evaluation_dataset.xlsx")
    assert len(eval_df) == 60


def test_train_row_count_after_clean_split(data_dir: Path):
    train_df = load_classification_dataset(data_dir / "basic_dataset_qa_clasificacion.xlsx")
    assert len(train_df) == 46


def test_no_duplicate_normalized_questions_within_train(data_dir: Path):
    train_df = load_classification_dataset(data_dir / "basic_dataset_qa_clasificacion.xlsx")
    norms = train_df["Question"].apply(normalize_question).tolist()
    assert len(norms) == len(set(norms))


def test_no_duplicate_normalized_questions_within_eval(data_dir: Path):
    eval_df = load_classification_dataset(data_dir / "evaluation_dataset.xlsx")
    qcol = "Question" if "Question" in eval_df.columns else "Pregunta"
    norms = eval_df[qcol].apply(normalize_question).tolist()
    assert len(norms) == len(set(norms))


def test_backups_preserve_original_sha_manifest(data_dir: Path):
    manifest_path = data_dir / "backups" / "20250629" / "manifest.json"
    assert manifest_path.is_file()
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    assert manifest["removedOverlapCount"] == 11
    assert manifest["overlapCountAfter"] == 0
    for key in ("train", "eval"):
        backup = data_dir / "backups" / "20250629" / (
            "basic_dataset_qa_clasificacion.xlsx" if key == "train" else "evaluation_dataset.xlsx"
        )
        assert backup.is_file()
        assert manifest["before"][key]["sha256"]
