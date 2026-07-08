#!/usr/bin/env python3
"""
Apply classifier train/eval leakage closure: backup originals, remove train overlaps,
insert documented replacements, write *_clean.xlsx and refresh canonical train file.
"""
from __future__ import annotations

import hashlib
import json
import shutil
import sys
from datetime import datetime, timezone
from pathlib import Path

import pandas as pd

_ROOT = Path(__file__).resolve().parents[1]
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

from app.dataset_hygiene import (  # noqa: E402
    find_train_eval_overlaps,
    load_classification_dataset,
    normalize_question,
    normalized_questions,
)

AUDIT_DATE = "20250629"
DATA_DIR = _ROOT / "data"
BACKUP_DIR = DATA_DIR / "backups" / AUDIT_DATE
TRAIN_PATH = DATA_DIR / "basic_dataset_qa_clasificacion.xlsx"
EVAL_PATH = DATA_DIR / "evaluation_dataset.xlsx"
TRAIN_CLEAN = DATA_DIR / "basic_dataset_qa_clasificacion_clean.xlsx"
EVAL_CLEAN = DATA_DIR / "evaluation_dataset_clean.xlsx"
REPLACEMENTS_JSON = DATA_DIR / "backups" / AUDIT_DATE / "train_replacements.json"

# Training-only replacements for removed overlap rows (eval held out unchanged).
TRAIN_REPLACEMENTS: list[dict[str, str]] = [
    {
        "removed_normalized": "¿cuántas actas mencionan el ascensor?",
        "Question": "¿Cuántas reuniones de 2026 trataron el mantenimiento de la piscina?",
        "Respuesta": "",
        "QueryType": "COUNT_DOCUMENTS",
        "rationale": "Count-documents phrasing; distinct date/topic from eval held-out set.",
    },
    {
        "removed_normalized": "¿quién fue la secretaria en la reunión del 25 de agosto de 2025?",
        "Question": "¿Quién actuó como vocal en la reunión del 12 de diciembre de 2026?",
        "Respuesta": "",
        "QueryType": "GET_FIELD",
        "rationale": "Field extraction (role); different meeting date than eval overlap.",
    },
    {
        "removed_normalized": "¿cuánto duró la reunión del 25 de agosto de 2028?",
        "Question": "¿Cuánto duró la sesión ordinaria del 10 de enero de 2027?",
        "Respuesta": "",
        "QueryType": "GET_DURATION",
        "rationale": "Duration query on a meeting date not present in eval.",
    },
    {
        "removed_normalized": "¿hubo más asistentes en la reunión de agosto o en la de febrero de 2025?",
        "Question": "¿Hubo más menciones al ascensor en febrero o en agosto de 2026?",
        "Respuesta": "",
        "QueryType": "COMPARE",
        "rationale": "Month comparison pattern; different dimension (mentions vs attendees).",
    },
    {
        "removed_normalized": "¿se mencionó la seguridad en alguna reunión de 2026?",
        "Question": "¿Figura en el acta del 15 de enero de 2024 alguna votación sobre obras?",
        "Respuesta": "",
        "QueryType": "BOOLEAN_QUERY",
        "rationale": "Yes/no verification on acta content; distinct from eval security question.",
    },
    {
        "removed_normalized": "¿qué decisiones se tomaron en la reunión del 25 de febrero de 2026?",
        "Question": "¿Qué acuerdos constan en el acta del 12 de diciembre de 2026?",
        "Respuesta": "",
        "QueryType": "DECISION_EXTRACTION",
        "rationale": "Decision extraction; replaces overlap while keeping class coverage in train.",
    },
    {
        "removed_normalized": "extrae las decisiones acordadas el 24 de febrero de 2025.",
        "Question": "Indica las decisiones registradas en la reunión del 10 de enero de 2027.",
        "Respuesta": "",
        "QueryType": "DECISION_EXTRACTION",
        "rationale": "Second train-only decision-extraction example after overlap removal.",
    },
    {
        "removed_normalized": "resume la reunión del 24 de febrero de 2025.",
        "Question": "Sintetiza los temas tratados en la reunión del 12 de diciembre de 2026.",
        "Respuesta": "",
        "QueryType": "SUMMARIZE_MEETING",
        "rationale": "Meeting summary; new date not in eval overlap set.",
    },
    {
        "removed_normalized": "dame un resumen de la reunión celebrada el 25 de agosto de 2026.",
        "Question": "Resume brevemente la reunión celebrada el 10 de enero de 2027.",
        "Respuesta": "",
        "QueryType": "SUMMARIZE_MEETING",
        "rationale": "Second train-only meeting-summary example.",
    },
    {
        "removed_normalized": "lista los asistentes de la reunión del 25 de agosto de 2026 en la que se trató el tema del ascensor.",
        "Question": "Lista las reuniones de agosto de 2026 sobre climatización con más de 20 asistentes.",
        "Respuesta": "",
        "QueryType": "FILTER_AND_LIST",
        "rationale": "Filter-and-list with attendance threshold; distinct entity focus.",
    },
    {
        "removed_normalized": "¿qué temas se discutieron en las reuniones celebradas en febrero que contaron con más de 15 asistentes?",
        "Question": "Indica los asistentes de las actas de febrero de 2025 donde se debatió el presupuesto.",
        "Respuesta": "",
        "QueryType": "FILTER_AND_LIST",
        "rationale": "Filter-and-list on budget topic; preserves class after overlap removal.",
    },
]


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(8192), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> int:
    if not TRAIN_PATH.is_file() or not EVAL_PATH.is_file():
        print("Missing train or eval dataset.", file=sys.stderr)
        return 2

    BACKUP_DIR.mkdir(parents=True, exist_ok=True)
    manifest: dict = {
        "auditDate": AUDIT_DATE,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "before": {},
        "after": {},
        "removedOverlapCount": 0,
        "replacementCount": len(TRAIN_REPLACEMENTS),
    }

    for label, path in (("train", TRAIN_PATH), ("eval", EVAL_PATH)):
        dest = BACKUP_DIR / path.name
        if not dest.exists():
            shutil.copy2(path, dest)
        manifest["before"][label] = {"path": str(path), "sha256": sha256_file(path), "rows": len(pd.read_excel(path))}

    train_df = load_classification_dataset(TRAIN_PATH)
    eval_df = load_classification_dataset(EVAL_PATH)
    overlaps = find_train_eval_overlaps(train_df, eval_df)
    overlap_norms = {o["normalized"] for o in overlaps}
    manifest["removedOverlapCount"] = len(overlap_norms)

    train_norm = normalized_questions(train_df)
    clean_train = train_df[~train_norm.isin(overlap_norms)].copy()
    removed = len(train_df) - len(clean_train)
    if removed != len(TRAIN_REPLACEMENTS):
        print(f"Expected {len(TRAIN_REPLACEMENTS)} overlaps, removed {removed}", file=sys.stderr)
        return 3

    replacement_rows = pd.DataFrame(
        [{k: v for k, v in r.items() if k not in ("removed_normalized", "rationale")} for r in TRAIN_REPLACEMENTS]
    )
    eval_norms = set(normalized_questions(eval_df))
    for r in TRAIN_REPLACEMENTS:
        norm = normalize_question(r["Question"])
        if norm in eval_norms:
            print(f"Replacement still overlaps eval: {r['Question']}", file=sys.stderr)
            return 4

    clean_train = pd.concat([clean_train, replacement_rows], ignore_index=True)
    eval_clean = eval_df.copy()

    TRAIN_CLEAN.parent.mkdir(parents=True, exist_ok=True)
    clean_train.to_excel(TRAIN_CLEAN, index=False)
    eval_clean.to_excel(EVAL_CLEAN, index=False)
    clean_train.to_excel(TRAIN_PATH, index=False)

    REPLACEMENTS_JSON.write_text(json.dumps(TRAIN_REPLACEMENTS, ensure_ascii=False, indent=2), encoding="utf-8")

    post_overlaps = find_train_eval_overlaps(load_classification_dataset(TRAIN_PATH), eval_df)
    if post_overlaps:
        print(f"Leakage remains: {len(post_overlaps)}", file=sys.stderr)
        return 5

    manifest["after"]["train"] = {
        "path": str(TRAIN_PATH),
        "cleanCopy": str(TRAIN_CLEAN),
        "sha256": sha256_file(TRAIN_PATH),
        "rows": len(clean_train),
    }
    manifest["after"]["eval"] = {
        "path": str(EVAL_PATH),
        "cleanCopy": str(EVAL_CLEAN),
        "sha256": sha256_file(EVAL_PATH),
        "rows": len(eval_clean),
    }
    manifest["overlapCountAfter"] = 0
    (BACKUP_DIR / "manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")

    print(json.dumps(manifest, indent=2))
    print(f"OK: zero overlaps; train rows={len(clean_train)} eval rows={len(eval_clean)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
