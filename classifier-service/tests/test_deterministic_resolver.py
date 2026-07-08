"""Unit tests for deterministic_resolver (C6 / Java contract alignment)."""

from app.deterministic_resolver import predict_with_ml_fallback, resolve_deterministic


def test_ambiguous_president_without_date_is_not_deterministic():
    """Mirrors ClassifierDeterministicResolverTest.ambiguousPresidentWithoutDateIsNotDeterministic."""
    assert resolve_deterministic("¿Quién fue el presidente?") is None


def test_dated_president_resolves_get_field():
    assert resolve_deterministic("¿Quién fue el presidente en la reunión del 25/02/2026?") == "GET_FIELD"


def test_hay_actas_menos_de_boolean():
    assert resolve_deterministic("¿Hay actas con menos de 10 participantes?") == "BOOLEAN_QUERY"


def test_ml_fallback_applies_rules_after_ml():
    assert predict_with_ml_fallback("¿Cuántas actas hay?", "COUNT_AND_EXPLAIN") == "COUNT_DOCUMENTS"
