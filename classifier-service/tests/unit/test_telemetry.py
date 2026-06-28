"""Unit tests for telemetry helpers (minimal OTLP assumptions)."""

from __future__ import annotations

import builtins
import os
from unittest.mock import MagicMock, patch

import pytest

from app import telemetry as tel

_real_import = builtins.__import__


def _import_deny_opentelemetry(name, globals=None, locals=None, fromlist=(), level=0):
    if name == "opentelemetry" or name.startswith("opentelemetry."):
        raise ImportError("opentelemetry unavailable in test")
    return _real_import(name, globals, locals, fromlist, level)


def test_setup_telemetry_no_endpoint_is_noop():
    with patch.dict(os.environ, {}, clear=True):
        app = MagicMock()
        tel.setup_telemetry(app)
    app.assert_not_called()


def test_get_meter_returns_none_before_setup():
    assert tel.get_meter() is None


def test_record_classifier_call_does_not_raise_without_meter():
    tel.record_classifier_call("success", "m1")


def test_truncate_long_string():
    long_val = "x" * 600
    out = tel._truncate(long_val)
    assert len(out) <= tel.MAX_ATTR_LEN + 3
    assert out.endswith("...")


def test_run_traced_without_tracer_executes_fn():
    called = []

    def inner():
        called.append(1)
        return 42

    with patch.object(tel, "get_tracer", return_value=None):
        assert tel.run_traced("span", inner) == 42
    assert called == [1]


def test_run_traced_with_tracer_records_span():
    tracer = MagicMock()
    span = MagicMock()
    tracer.start_span.return_value = span

    def inner():
        return "done"

    with patch.object(tel, "get_tracer", return_value=tracer):
        assert (
            tel.run_traced(
                "op",
                inner,
                input_attrs={"a": "b"},
                output_attr="out",
                output_value_fn=lambda x: x,
            )
            == "done"
        )
    span.end.assert_called_once()


def test_record_train_and_evaluate_no_meter():
    tel.record_train_complete("m")
    tel.record_evaluate_complete("m")


def test_truncate_none_returns_empty():
    assert tel._truncate(None) == ""


def test_get_tracer_returns_none_when_opentelemetry_import_fails():
    with patch("builtins.__import__", new=_import_deny_opentelemetry):
        assert tel.get_tracer() is None


def test_setup_telemetry_logs_and_skips_when_opentelemetry_missing():
    app = MagicMock()
    with patch.dict(os.environ, {"OTEL_EXPORTER_OTLP_ENDPOINT": "http://localhost:4318"}):
        with patch("builtins.__import__", new=_import_deny_opentelemetry):
            tel.setup_telemetry(app)
    app.assert_not_called()


def test_get_tracer_returns_none_when_trace_get_tracer_raises():
    pytest.importorskip("opentelemetry")
    from opentelemetry import trace as otel_trace

    with patch.object(otel_trace, "get_tracer", side_effect=RuntimeError("no provider")):
        assert tel.get_tracer() is None


def test_run_traced_exception_records_error():
    tracer = MagicMock()
    span = MagicMock()
    tracer.start_span.return_value = span

    def inner():
        raise ValueError("fail")

    with patch.object(tel, "get_tracer", return_value=tracer):
        with pytest.raises(ValueError, match="fail"):
            tel.run_traced("op", inner)
    span.set_status.assert_called()
    span.record_exception.assert_called_once()
    span.end.assert_called_once()


def test_classification_service_span_attrs_use_query_length_not_raw_query():
    from unittest.mock import patch

    from app.models.classification_result import ClassificationResult
    from app.services.classification_service import ClassificationService

    engine = MagicMock()
    engine._loader.is_loaded.return_value = True
    engine.predict_detailed.return_value = ClassificationResult(
        query_type="COUNT_DOCUMENTS", confidence=0.95
    )
    service = ClassificationService(engine)
    captured_attrs: dict = {}

    def capture_run_traced(_name, fn, input_attrs=None, output_attr=None, output_value_fn=None):
        if input_attrs:
            captured_attrs.update(input_attrs)
        return fn()

    with patch("app.services.classification_service.ClassificationService.run_traced", side_effect=capture_run_traced):
        result = service.classify("sensitive user question", "m1")

    assert result.query_type == "COUNT_DOCUMENTS"
    assert "query" not in captured_attrs
    assert captured_attrs.get("queryLength") == str(len("sensitive user question"))
    assert captured_attrs.get("modelId") == "m1"


def test_record_classifier_call_with_counter_add_failure():
    counter = MagicMock()
    counter.add.side_effect = RuntimeError("no export")
    try:
        with patch.object(tel, "_classifier_counter", counter):
            tel.record_classifier_call("success", "m")
    finally:
        tel._classifier_counter = None
