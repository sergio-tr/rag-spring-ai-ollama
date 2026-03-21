"""Unit tests for telemetry helpers (minimal OTLP assumptions)."""

from __future__ import annotations

import os
from unittest.mock import MagicMock, patch

from app import telemetry as tel


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
