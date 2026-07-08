#!/usr/bin/env python3
"""
Apply guarded train-only expansion to reach 8 examples/class minimum.
Writes data/basic_dataset_qa_clasificacion_expanded.xlsx after hygiene checks.
"""
from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

import pandas as pd

_ROOT = Path(__file__).resolve().parents[1]
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

from app.dataset_hygiene import (  # noqa: E402
    find_train_eval_overlaps,
    load_classification_dataset,
    normalize_question,
)
from app.query_type_contract import JAVA_QUERY_TYPE_ORDER  # noqa: E402

# Train-only additions: diverse Spanish acta phrasing, no eval overlap verified at apply time.
EXPANSION_ROWS: list[dict[str, str]] = [
    # BOOLEAN_QUERY (+4 -> 8)
    {"Question": "¿Consta en algún acta la firma de María López Sánchez?", "QueryType": "BOOLEAN_QUERY"},
    {"Question": "¿Se debatió el protocolo de seguridad en marzo de 2027?", "QueryType": "BOOLEAN_QUERY"},
    {"Question": "¿Aparece la palabra subvención en las actas de 2025?", "QueryType": "BOOLEAN_QUERY"},
    {"Question": "¿Hubo votación sobre el calendario académico en junio?", "QueryType": "BOOLEAN_QUERY"},
    # COMPARE (+4 -> 8)
    {"Question": "¿Qué trimestre registró más acuerdos, el primero o el tercero?", "QueryType": "COMPARE"},
    {"Question": "¿Las reuniones de primavera fueron más cortas que las de otoño?", "QueryType": "COMPARE"},
    {"Question": "¿Se trató el presupuesto con más detalle en 2026 o en 2027?", "QueryType": "COMPARE"},
    {"Question": "¿Hubo más intervenciones en ruegos y preguntas en mayo o en septiembre?", "QueryType": "COMPARE"},
    # COUNT_DOCUMENTS (+3 -> 8)
    {"Question": "¿En cuántas actas se menciona el plan de movilidad?", "QueryType": "COUNT_DOCUMENTS"},
    {"Question": "¿Cuántas reuniones abordaron el mantenimiento de jardines?", "QueryType": "COUNT_DOCUMENTS"},
    {"Question": "¿Cuántos actas del segundo semestre hablan de becas?", "QueryType": "COUNT_DOCUMENTS"},
    # DECISION_EXTRACTION (+6 -> 8)
    {"Question": "¿Qué resoluciones figuran en el acta del 3 de marzo de 2027?", "QueryType": "DECISION_EXTRACTION"},
    {"Question": "Enumera los acuerdos adoptados en la sesión del 14 de abril de 2026.", "QueryType": "DECISION_EXTRACTION"},
    {"Question": "¿Qué decisiones se registraron sobre contratación en mayo de 2026?", "QueryType": "DECISION_EXTRACTION"},
    {"Question": "Indica los acuerdos del punto 4 del orden del día del 8 de julio.", "QueryType": "DECISION_EXTRACTION"},
    {"Question": "¿Qué votaciones quedaron reflejadas en el acta del 19 de noviembre?", "QueryType": "DECISION_EXTRACTION"},
    {"Question": "Resume los acuerdos finales de la reunión extraordinaria de octubre.", "QueryType": "DECISION_EXTRACTION"},
    # EXTRACT_ENTITIES (+4 -> 8)
    {"Question": "¿Quiénes firmaron el acta de la reunión del 6 de junio de 2026?", "QueryType": "EXTRACT_ENTITIES"},
    {"Question": "¿Qué cargos aparecen en la lista de asistentes del 11 de mayo?", "QueryType": "EXTRACT_ENTITIES"},
    {"Question": "¿Qué departamentos intervinieron en la sesión del 22 de agosto?", "QueryType": "EXTRACT_ENTITIES"},
    {"Question": "¿Qué entidades externas se citan en el acta del 17 de septiembre?", "QueryType": "EXTRACT_ENTITIES"},
    # FILTER_AND_LIST (+6 -> 8)
    {"Question": "Lista las actas de 2026 donde participó más de la mitad del claustro.", "QueryType": "FILTER_AND_LIST"},
    {"Question": "Indica las reuniones de enero de 2027 con debate sobre infraestructura.", "QueryType": "FILTER_AND_LIST"},
    {"Question": "Enumera los actas de verano que trataron mantenimiento eléctrico.", "QueryType": "FILTER_AND_LIST"},
    {"Question": "¿Qué sesiones de 2025 incluyen ruegos sobre biblioteca?", "QueryType": "FILTER_AND_LIST"},
    {"Question": "Lista las actas con más de quince asistentes en el segundo trimestre.", "QueryType": "FILTER_AND_LIST"},
    {"Question": "Indica reuniones de 2026 donde se citó el reglamento de convivencia.", "QueryType": "FILTER_AND_LIST"},
    # FIND_PARAGRAPH (+5 -> 8)
    {"Question": "¿Qué párrafo describe la inspección de instalaciones?", "QueryType": "FIND_PARAGRAPH"},
    {"Question": "¿Dónde se comenta la revisión del plan director?", "QueryType": "FIND_PARAGRAPH"},
    {"Question": "¿Qué apartado menciona la rotura de tuberías?", "QueryType": "FIND_PARAGRAPH"},
    {"Question": "¿Qué se dijo exactamente sobre la ampliación del aparcamiento?", "QueryType": "FIND_PARAGRAPH"},
    {"Question": "¿En qué sección se habla del informe de sostenibilidad?", "QueryType": "FIND_PARAGRAPH"},
    # GET_DURATION (+4 -> 8)
    {"Question": "¿Cuánto duró la sesión del 2 de octubre de 2026?", "QueryType": "GET_DURATION"},
    {"Question": "¿Cuál fue la duración total de la reunión del 18 de marzo?", "QueryType": "GET_DURATION"},
    {"Question": "¿Cuántos minutos duró el acta del 30 de abril de 2027?", "QueryType": "GET_DURATION"},
    {"Question": "¿Qué duración tuvo la asamblea celebrada el 7 de diciembre?", "QueryType": "GET_DURATION"},
    # GET_FIELD (+3 -> 8)
    {"Question": "¿Quién actuó como secretario en la reunión del 4 de febrero de 2026?", "QueryType": "GET_FIELD"},
    {"Question": "¿En qué aula se celebró la sesión del 15 de junio de 2027?", "QueryType": "GET_FIELD"},
    {"Question": "¿Qué hora de inicio consta en el acta del 21 de julio de 2026?", "QueryType": "GET_FIELD"},
    # SUMMARIZE_MEETING (+6 -> 8)
    {"Question": "Resume la reunión del 9 de mayo de 2026 en pocas líneas.", "QueryType": "SUMMARIZE_MEETING"},
    {"Question": "Sintetiza los puntos principales del acta del 27 de febrero de 2027.", "QueryType": "SUMMARIZE_MEETING"},
    {"Question": "Haz un resumen ejecutivo de la sesión del 13 de agosto de 2026.", "QueryType": "SUMMARIZE_MEETING"},
    {"Question": "Condensa la reunión ordinaria del 5 de noviembre de 2026.", "QueryType": "SUMMARIZE_MEETING"},
    {"Question": "Resume globalmente el acta del 16 de enero de 2027.", "QueryType": "SUMMARIZE_MEETING"},
    {"Question": "Sintetiza la sesión plenaria del 28 de septiembre de 2026.", "QueryType": "SUMMARIZE_MEETING"},
    # SUMMARIZE_TOPIC (+5 -> 8)
    {"Question": "Resume lo debatido sobre conectividad wifi en 2026.", "QueryType": "SUMMARIZE_TOPIC"},
    {"Question": "¿Cómo resumirías las intervenciones sobre laboratorio químico?", "QueryType": "SUMMARIZE_TOPIC"},
    {"Question": "Dime qué se dijo sobre movilidad internacional en las actas.", "QueryType": "SUMMARIZE_TOPIC"},
    {"Question": "Resume el hilo de conversación sobre eficiencia energética.", "QueryType": "SUMMARIZE_TOPIC"},
    {"Question": "Sintetiza las menciones al plan de igualdad en las reuniones.", "QueryType": "SUMMARIZE_TOPIC"},
    # COUNT_AND_EXPLAIN (+2 -> 10)
    {"Question": "¿Cuántas veces se citó el código ético y en qué actas?", "QueryType": "COUNT_AND_EXPLAIN"},
    {"Question": "¿En cuántas sesiones aparece teletrabajo y qué se dijo?", "QueryType": "COUNT_AND_EXPLAIN"},
]


def apply_expansion(train_path: Path, eval_path: Path, output_path: Path) -> dict:
    train_df = load_classification_dataset(str(train_path))
    eval_df = load_classification_dataset(str(eval_path))
    additions = pd.DataFrame(EXPANSION_ROWS)
    expanded = pd.concat([train_df, additions], ignore_index=True)

    train_norm = set(train_df["Question"].map(normalize_question))
    eval_norm = set(eval_df["Question"].map(normalize_question))
    dup_train = []
    dup_eval = []
    for q in additions["Question"]:
        n = normalize_question(q)
        if n in train_norm:
            dup_train.append(q)
        if n in eval_norm:
            dup_eval.append(q)

    overlaps = find_train_eval_overlaps(expanded, eval_df)
    per_class = expanded["QueryType"].value_counts().to_dict()
    min_per_class = min(per_class.get(c, 0) for c in JAVA_QUERY_TYPE_ORDER)

    report = {
        "train_path": str(train_path),
        "eval_path": str(eval_path),
        "output_path": str(output_path),
        "rows_before": int(len(train_df)),
        "rows_added": int(len(additions)),
        "rows_after": int(len(expanded)),
        "min_per_class": int(min_per_class),
        "per_class": {k: int(v) for k, v in sorted(per_class.items())},
        "duplicate_in_train": dup_train,
        "duplicate_in_eval": dup_eval,
        "train_eval_overlaps": len(overlaps),
    }

    if dup_train or dup_eval or overlaps:
        raise ValueError(f"Hygiene check failed: {json.dumps(report, ensure_ascii=False, indent=2)}")

    output_path.parent.mkdir(parents=True, exist_ok=True)
    expanded.to_excel(output_path, index=False)
    return report


def main() -> int:
    config_data_dir = _ROOT / "data"
    p = argparse.ArgumentParser()
    p.add_argument("--train-xlsx", type=Path, default=config_data_dir / "basic_dataset_qa_clasificacion_clean.xlsx")
    p.add_argument("--eval-xlsx", type=Path, default=config_data_dir / "evaluation_dataset.xlsx")
    p.add_argument("--output-xlsx", type=Path, default=config_data_dir / "basic_dataset_qa_clasificacion_expanded.xlsx")
    p.add_argument("--report-json", type=Path, default=None)
    args = p.parse_args()

    report = apply_expansion(args.train_xlsx.resolve(), args.eval_xlsx.resolve(), args.output_xlsx.resolve())
    print(json.dumps(report, ensure_ascii=False, indent=2))
    if args.report_json:
        args.report_json.parent.mkdir(parents=True, exist_ok=True)
        args.report_json.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
