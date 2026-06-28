#!/usr/bin/env python3
"""
Retrain models/default from aligned datasets (M6 contract).
Combines training + evaluation Excel, uses canonical 12-class order, copies artifacts to models/default/.
"""
from __future__ import annotations

import shutil
import sys
import tempfile
from pathlib import Path

import pandas as pd

# Allow running from classifier-service root
_ROOT = Path(__file__).resolve().parents[1]
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

from app.config import Config  # noqa: E402
from app.dataset_columns import normalize_excel_classification_columns  # noqa: E402
from app.query_type_contract import JAVA_QUERY_TYPE_ORDER  # noqa: E402
from app.training.trainer import TrainingPipeline  # noqa: E402


def _combined_dataset_path(data_dir: Path, out: Path) -> Path:
    basic = normalize_excel_classification_columns(
        pd.read_excel(data_dir / "basic_dataset_qa_clasificacion.xlsx")
    )
    eval_df = normalize_excel_classification_columns(
        pd.read_excel(data_dir / "evaluation_dataset.xlsx")
    )
    combined = pd.concat([basic, eval_df], ignore_index=True)
    combined.to_excel(out, index=False)
    return out


def main() -> int:
    config = Config()
    data_dir = Path(config.get_data_dir())
    default_dir = Path(config.get_models_dir()) / config.DEFAULT_MODEL_TAG
    default_dir.mkdir(parents=True, exist_ok=True)

    with tempfile.TemporaryDirectory() as tmp:
        dataset = _combined_dataset_path(data_dir, Path(tmp) / "combined_train.xlsx")
        pipeline = TrainingPipeline(config=config)
        result = pipeline.train(
            dataset_path=str(dataset),
            model_name=config.get_default_model_name(),
            class_names=list(JAVA_QUERY_TYPE_ORDER),
            epochs=40,
            batch_size=8,
        )
        shutil.copy2(result["model_path"], default_dir / "model.keras")
        shutil.copy2(result["labels_path"], default_dir / "labels.txt")

    print(f"Default model updated under {default_dir}")
    print(f"Metrics: {result.get('metrics')}")
    print(f"Intermediate train id: {result.get('model_id')}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
