"""
Simplified Spanish phrasing rules mirroring rag-service ClassifierOverrides / ClassifierDeterministicResolver.
Used for candidate-matrix C6 (deterministic rules + ML fallback) offline evaluation.
"""
from __future__ import annotations

import re
from typing import Optional

from app.query_type_contract import JAVA_QUERY_TYPES

_RULE_SENTINEL = "DECISION_EXTRACTION"


def _has_dated(q: str) -> bool:
    if re.search(r"\b\d{1,2}[/-]\d{1,2}[/-]\d{2,4}\b", q):
        return True
    if re.search(
        r"\b\d{1,2}\s+de\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)",
        q,
    ):
        return True
    if re.search(r"\b(año|ano)\s+(del\s+)?\d{4}\b", q):
        return True
    return False


def apply_rules(query: str, classified: str) -> str:
    """Returns rule-overridden QueryType or the incoming classified label."""
    if not query or not query.strip():
        return classified
    q = query.lower()
    acta_context = any(w in q for w in ("acta", "minuta", "reunión", "reunion"))
    dated = _has_dated(q)
    count_cue = any(
        p in q
        for p in (
            "cuántas",
            "cuantas",
            "en cuántas",
            "en cuantas",
            "cuántos",
            "cuantos",
        )
    )

    if any(p in q for p in ("en cuántas actas aparece", "en cuantas actas aparece")):
        return "COUNT_DOCUMENTS"

    if dated and any(p in q for p in ("quién fue", "quien fue", "y quién", "y quien")):
        if any(p in q for p in ("secretari", "presidente", "presidenta")):
            return "GET_FIELD"

    if q.startswith("resume") or ("resume" in q and dated):
        return "SUMMARIZE_MEETING"

    if dated and any(p in q for p in ("duración", "duracion", "cuánto dur", "cuanto dur")):
        return "GET_DURATION"

    if any(p in q for p in ("qué actas tienen", "que actas tienen", "qué actas tratan", "que actas tratan")):
        return "FILTER_AND_LIST"

    if any(p in q for p in ("en qué actas aparece", "en que actas aparece")):
        return "FIND_PARAGRAPH"

    if ("hay actas" in q or "existen actas" in q) and ("menos de" in q or "más de" in q or "mas de" in q):
        return "BOOLEAN_QUERY"

    if acta_context and dated and not count_cue:
        if any(p in q for p in ("presidente", "presidió", "presidio", "secretari")):
            return "GET_FIELD"
        if any(p in q for p in ("participantes", "asistentes", "asistente")):
            return "GET_FIELD"

    if count_cue and acta_context:
        if any(p in q for p in ("qué se", "que se", "decidió", "decidio", "contexto")):
            return "COUNT_AND_EXPLAIN"
        return "COUNT_DOCUMENTS"

    if any(p in q for p in ("se habló de", "se hablo de")) and "en alguna reunión" in q:
        return "BOOLEAN_QUERY"

    return classified


def resolve_deterministic(query: str) -> Optional[str]:
    """
    Returns a QueryType when phrasing alone is sufficient (no ML), else None.
    Ambiguous undated presidente questions intentionally return None (Java contract).
    """
    if not query or not query.strip():
        return None
    q = query.lower()
    if "quién fue el presidente" in q or "quien fue el presidente" in q:
        if not _has_dated(q):
            return None
    adjusted = apply_rules(query, _RULE_SENTINEL)
    if adjusted == _RULE_SENTINEL:
        return None
    if adjusted not in JAVA_QUERY_TYPES:
        return None
    return adjusted


def predict_with_ml_fallback(query: str, ml_label: str) -> str:
    """Deterministic first, then ML prediction."""
    det = resolve_deterministic(query)
    if det is not None:
        return det
    return apply_rules(query, ml_label)
