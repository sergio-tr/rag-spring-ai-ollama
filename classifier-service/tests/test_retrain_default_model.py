"""Tests for retrain_default_model.py - eval must never be used for training."""

from __future__ import annotations

import importlib.util
import sys
from pathlib import Path

import pandas as pd
import pytest

from app.dataset_hygiene import load_classification_dataset, normalize_question
from app.query_type_contract import JAVA_QUERY_TYPE_ORDER

_SCRIPT = Path(__file__).resolve().parents[1] / "scripts" / "retrain_default_model.py"


def _load_retrain_module():
    spec = importlib.util.spec_from_file_location("retrain_default_model", _SCRIPT)
    mod = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(mod)
    return mod


@pytest.fixture
def retrain_mod():
    return _load_retrain_module()


def test_retrain_script_does_not_define_combined_dataset_helper(retrain_mod):
    assert not hasattr(retrain_mod, "_combined_dataset_path")


def test_assert_train_eval_separation_fails_on_overlap(retrain_mod, tmp_path):
    train = tmp_path / "train.xlsx"
    ev = tmp_path / "eval.xlsx"
    row = {"Question": "¿Cuántas actas hay?", "QueryType": "COUNT_DOCUMENTS"}
    pd.DataFrame([row]).to_excel(train, index=False)
    pd.DataFrame([row]).to_excel(ev, index=False)
    with pytest.raises(SystemExit, match="overlap"):
        retrain_mod._assert_train_eval_separation(train, ev, no_eval_training=True)


def test_train_only_path_never_reads_eval_for_training(retrain_mod, tmp_path, monkeypatch):
    """TrainingPipeline.train must receive only the train xlsx path, not eval."""
    train = tmp_path / "train.xlsx"
    ev = tmp_path / "eval.xlsx"
    rows = [
        {"Question": "Train-only question A", "QueryType": "COUNT_DOCUMENTS"},
        {"Question": "Train-only question B", "QueryType": "BOOLEAN_QUERY"},
    ]
    pd.DataFrame(rows).to_excel(train, index=False)
    pd.DataFrame([{"Question": "Eval-only question", "QueryType": "GET_FIELD"}]).to_excel(ev, index=False)

    captured: dict = {}

    class FakePipeline:
        def __init__(self, config=None):
            pass

        def train(self, **kwargs):
            captured.update(kwargs)
            out = tmp_path / "out"
            out.mkdir(exist_ok=True)
            (out / "model.keras").write_bytes(b"fake")
            (out / "labels.txt").write_text("\n".join(JAVA_QUERY_TYPE_ORDER), encoding="utf-8")
            return {
                "model_path": str(out / "model.keras"),
                "labels_path": str(out / "labels.txt"),
                "metrics": {"accuracy": 1.0, "macro_avg_f1": 1.0},
                "model_id": "fake-id",
            }

    monkeypatch.setattr(retrain_mod, "TrainingPipeline", FakePipeline)
    rc = retrain_mod.main(
        [
            "--train-xlsx",
            str(train),
            "--eval-xlsx",
            str(ev),
            "--model-output",
            str(tmp_path / "default"),
        ]
    )
    assert rc == 0
    assert captured["dataset_path"] == str(train.resolve())
    assert "evaluation" not in captured["dataset_path"].lower()


def test_default_train_xlsx_prefers_clean_copy(retrain_mod, tmp_path, monkeypatch):
    data_dir = tmp_path / "data"
    data_dir.mkdir()
    clean = data_dir / "basic_dataset_qa_clasificacion_clean.xlsx"
    basic = data_dir / "basic_dataset_qa_clasificacion.xlsx"
    pd.DataFrame([{"Question": "q", "QueryType": "COUNT_DOCUMENTS"}]).to_excel(clean, index=False)
    pd.DataFrame([{"Question": "q2", "QueryType": "BOOLEAN_QUERY"}]).to_excel(basic, index=False)
    monkeypatch.setenv("DATA_DIR", str(data_dir))
    from app.config import Config

    Config._instance = None
    chosen = retrain_mod._default_train_xlsx(data_dir)
    assert chosen == clean


def test_real_datasets_have_zero_overlap():
    from app.config import Config

    data_dir = Path(Config().get_data_dir())
    train_df = load_classification_dataset(data_dir / "basic_dataset_qa_clasificacion_final.xlsx")
    eval_df = load_classification_dataset(data_dir / "evaluation_dataset.xlsx")
    train_norms = {normalize_question(q) for q in train_df["Question"]}
    eval_norms = {normalize_question(q) for q in eval_df["Question"]}
    assert not (train_norms & eval_norms - {""})
