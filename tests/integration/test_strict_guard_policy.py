from __future__ import annotations

import conftest


def test_classifier_reachability_required_allows_model_not_loaded_skip_when_model_not_required(
    monkeypatch,
) -> None:
    monkeypatch.setenv("INTEGRATION_REQUIRE_CLASSIFIER_MODEL", "0")

    assert not conftest._classifier_skip_requires_failure(
        "tests/integration/test_stack_integration.py::TestClassifierService::test_classify_returns_query_type",
        "Skipped: Keras model not loaded in classifier-service (/health -> model != loaded).",
    )


def test_classifier_reachability_required_fails_model_not_loaded_skip_when_model_required(
    monkeypatch,
) -> None:
    monkeypatch.setenv("INTEGRATION_REQUIRE_CLASSIFIER_MODEL", "1")

    assert conftest._classifier_skip_requires_failure(
        "tests/integration/test_stack_integration.py::TestClassifierService::test_classify_returns_query_type",
        "Skipped: Keras model not loaded in classifier-service (/health -> model != loaded).",
    )


def test_classifier_reachability_required_still_fails_other_classifier_skips(monkeypatch) -> None:
    monkeypatch.setenv("INTEGRATION_REQUIRE_CLASSIFIER_MODEL", "0")

    assert conftest._classifier_skip_requires_failure(
        "tests/integration/test_stack_integration.py::TestClassifierService::test_models_returns_list",
        "Skipped: classifier-service unreachable.",
    )


def test_non_classifier_skips_do_not_trigger_classifier_guard(monkeypatch) -> None:
    monkeypatch.setenv("INTEGRATION_REQUIRE_CLASSIFIER_MODEL", "1")

    assert not conftest._classifier_skip_requires_failure(
        "tests/integration/test_stack_integration.py::TestObservabilityStack::test_prometheus",
        "Skipped: Observability stack not reachable.",
    )


def test_observability_classifier_service_skip_does_not_trigger_classifier_guard(monkeypatch) -> None:
    monkeypatch.setenv("INTEGRATION_REQUIRE_CLASSIFIER_MODEL", "1")

    assert not conftest._classifier_skip_requires_failure(
        "tests/integration/test_stack_integration.py::TestObservabilityStack::test_jaeger_lists_classifier_service_after_traffic",
        "Skipped: Observability tests disabled (INTEGRATION_CHECK_OBS=0)",
    )
