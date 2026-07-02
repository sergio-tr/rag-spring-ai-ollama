"""Dataset hygiene: no train/eval leakage, active train/eval contract, archive backups."""

from __future__ import annotations

import json
from pathlib import Path

import pytest

from app.config import Config
from app.dataset_hygiene import (
    assert_training_dataset_hygiene,
    find_train_eval_overlaps,
    load_classification_dataset,
    normalize_question,
)
from app.query_type_contract import JAVA_QUERY_TYPES


ACTIVE_TRAIN = "basic_dataset_qa_clasificacion_final.xlsx"
ACTIVE_EVAL = "evaluation_dataset.xlsx"
ARCHIVE_DIR = "archive/final-classifier-cleanup-20250702"
LEGACY_BACKUP_DIR = f"{ARCHIVE_DIR}/backups_20250629"


def _data_dir() -> Path:
    return Path(Config().get_data_dir())


@pytest.fixture
def data_dir() -> Path:
    return _data_dir()


def test_active_datasets_exist(data_dir: Path):
    assert (data_dir / ACTIVE_TRAIN).is_file()
    assert (data_dir / ACTIVE_EVAL).is_file()


def test_archived_legacy_datasets_preserved(data_dir: Path):
    archive = data_dir / ARCHIVE_DIR
    assert archive.is_dir()
    assert (archive / "basic_dataset_qa_clasificacion.xlsx").is_file()
    assert (archive / "basic_dataset_qa_clasificacion_clean.xlsx").is_file()
    assert (archive / "evaluation_dataset_clean.xlsx").is_file()


def test_zero_train_eval_question_overlaps(data_dir: Path):
    train_df = load_classification_dataset(data_dir / ACTIVE_TRAIN)
    eval_df = load_classification_dataset(data_dir / ACTIVE_EVAL)
    overlaps = find_train_eval_overlaps(train_df, eval_df)
    assert overlaps == [], f"train/eval leakage: {overlaps}"


def test_training_dataset_hygiene_passes(data_dir: Path):
    train_df = load_classification_dataset(data_dir / ACTIVE_TRAIN)
    gold = (
        Path(Config().get_data_dir()).resolve().parents[1]
        / "rag-service"
        / "src"
        / "main"
        / "resources"
        / "evaluation"
        / "gold-subset-v1.json"
    )
    assert_training_dataset_hygiene(
        train_df,
        eval_path=str(data_dir / ACTIVE_EVAL),
        gold_path=str(gold) if gold.is_file() else None,
    )


def test_replacements_documented_in_archive(data_dir: Path):
    repl_path = data_dir / LEGACY_BACKUP_DIR / "train_replacements.json"
    assert repl_path.is_file(), "missing archived train_replacements.json"
    replacements = json.loads(repl_path.read_text(encoding="utf-8"))
    assert len(replacements) == 11
    for entry in replacements:
        assert entry["QueryType"] in JAVA_QUERY_TYPES
        assert normalize_question(entry["Question"])


def test_eval_row_count_unchanged(data_dir: Path):
    eval_df = load_classification_dataset(data_dir / ACTIVE_EVAL)
    assert len(eval_df) == 60


def test_train_row_count_final_dataset(data_dir: Path):
    train_df = load_classification_dataset(data_dir / ACTIVE_TRAIN)
    assert len(train_df) == 213


def test_no_duplicate_normalized_questions_within_train(data_dir: Path):
    train_df = load_classification_dataset(data_dir / ACTIVE_TRAIN)
    norms = train_df["Question"].apply(normalize_question).tolist()
    assert len(norms) == len(set(norms))


def test_no_duplicate_normalized_questions_within_eval(data_dir: Path):
    eval_df = load_classification_dataset(data_dir / ACTIVE_EVAL)
    qcol = "Question" if "Question" in eval_df.columns else "Pregunta"
    norms = eval_df[qcol].apply(normalize_question).tolist()
    assert len(norms) == len(set(norms))


def test_archive_backups_preserve_original_sha_manifest(data_dir: Path):
    manifest_path = data_dir / LEGACY_BACKUP_DIR / "manifest.json"
    assert manifest_path.is_file()
    manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    assert manifest["removedOverlapCount"] == 11
    assert manifest["overlapCountAfter"] == 0
    for key in ("train", "eval"):
        backup = data_dir / LEGACY_BACKUP_DIR / (
            "basic_dataset_qa_clasificacion.xlsx" if key == "train" else "evaluation_dataset.xlsx"
        )
        assert backup.is_file()
        assert manifest["before"][key]["sha256"]
