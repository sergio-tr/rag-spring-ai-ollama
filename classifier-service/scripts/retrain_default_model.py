#!/usr/bin/env python3
"""
Retrain models/default from train-only dataset (no eval leakage).

Guards:
  - Fails if train/eval normalized-question overlap exists.
  - Fails if any eval row appears in the training file (--no-eval-training, default on).
"""
from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

# Allow running from classifier-service root
_ROOT = Path(__file__).resolve().parents[1]
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

from app.config import Config  # noqa: E402
from app.dataset_hygiene import (  # noqa: E402
    find_train_eval_overlaps,
    load_classification_dataset,
    normalized_questions,
)
from app.query_type_contract import JAVA_QUERY_TYPE_ORDER  # noqa: E402
from app.training.trainer import TrainingPipeline  # noqa: E402


def _default_train_xlsx(data_dir: Path) -> Path:
    final = data_dir / "basic_dataset_qa_clasificacion_final.xlsx"
    if final.is_file():
        return final
    clean = data_dir / "basic_dataset_qa_clasificacion_clean.xlsx"
    if clean.is_file():
        return clean
    return data_dir / "basic_dataset_qa_clasificacion.xlsx"


def _assert_train_eval_separation(
    train_path: Path,
    eval_path: Path | None,
    *,
    no_eval_training: bool,
) -> None:
    if eval_path is None or not eval_path.is_file():
        return
    train_df = load_classification_dataset(str(train_path))
    eval_df = load_classification_dataset(str(eval_path))
    overlaps = find_train_eval_overlaps(train_df, eval_df)
    if overlaps:
        sample = overlaps[0]["normalized"]
        raise SystemExit(
            f"Train/eval overlap detected ({len(overlaps)} rows). Example: {sample!r}. "
            "Fix datasets before retraining."
        )
    if not no_eval_training:
        return
    train_norms = set(normalized_questions(train_df))
    eval_norms = set(normalized_questions(eval_df))
    leaked = train_norms & eval_norms
    leaked.discard("")
    if leaked:
        raise SystemExit(
            f"Eval rows present in train ({len(leaked)} normalized questions). "
            "Training must use train-only data."
        )


def build_parser() -> argparse.ArgumentParser:
    config = Config()
    data_dir = Path(config.get_data_dir())
    default_dir = Path(config.get_models_dir()) / config.DEFAULT_MODEL_TAG

    p = argparse.ArgumentParser(description="Retrain models/default from train-only Excel.")
    p.add_argument(
        "--train-xlsx",
        type=Path,
        default=_default_train_xlsx(data_dir),
        help="Training dataset (Question, QueryType). Never concatenates eval.",
    )
    p.add_argument(
        "--eval-xlsx",
        type=Path,
        default=data_dir / "evaluation_dataset.xlsx",
        help="Held-out eval file used only for overlap guards (not training).",
    )
    p.add_argument(
        "--model-output",
        type=Path,
        default=default_dir,
        help="Directory for model.keras and labels.txt (default: models/default/).",
    )
    p.add_argument(
        "--no-eval-training",
        action=argparse.BooleanOptionalAction,
        default=True,
        help="Fail if eval questions appear in train; never load eval for training (default: true).",
    )
    p.add_argument("--epochs", type=int, default=40)
    p.add_argument("--batch-size", type=int, default=8)
    p.add_argument(
        "--class-weight",
        action="store_true",
        help="Apply sklearn balanced class weights during Keras training.",
    )
    return p


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    config = Config()

    train_path = args.train_xlsx.resolve()
    if not train_path.is_file():
        raise SystemExit(f"Training dataset not found: {train_path}")

    eval_path = args.eval_xlsx.resolve() if args.eval_xlsx else None
    _assert_train_eval_separation(train_path, eval_path, no_eval_training=args.no_eval_training)

    output_dir = args.model_output.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    pipeline = TrainingPipeline(config=config)
    result = pipeline.train(
        dataset_path=str(train_path),
        model_name=config.get_default_model_name(),
        output_dir=str(output_dir),
        class_names=list(JAVA_QUERY_TYPE_ORDER),
        epochs=args.epochs,
        batch_size=args.batch_size,
        class_weight=args.class_weight,
    )
    shutil.copy2(result["model_path"], output_dir / "model.keras")
    shutil.copy2(result["labels_path"], output_dir / "labels.txt")

    print(f"Default model updated under {output_dir}")
    print(f"Train source: {train_path}")
    print(f"Eval used for training: no")
    print(f"Metrics: {result.get('metrics')}")
    print(f"Intermediate train id: {result.get('model_id')}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
