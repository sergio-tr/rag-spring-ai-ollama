"""
OpenTelemetry setup for classifier-service. When OTEL_EXPORTER_OTLP_ENDPOINT is set,
configures tracing and metrics export to the collector (e.g. otel-collector:4318).
FastAPI is instrumented so HTTP requests and custom spans are traced; metrics are exported via OTLP.
"""
import logging
import os

_logger = logging.getLogger(__name__)

_meter = None


def setup_telemetry(app):
    """
    Configures OTLP tracing and metrics, and instruments the FastAPI app.
    No-op if OTEL_EXPORTER_OTLP_ENDPOINT is not set.
    """
    endpoint = os.environ.get("OTEL_EXPORTER_OTLP_ENDPOINT", "").strip()
    if not endpoint:
        _logger.info("OTEL_EXPORTER_OTLP_ENDPOINT not set; telemetry disabled")
        return

    try:
        from opentelemetry import trace, metrics
        from opentelemetry.sdk.trace import TracerProvider
        from opentelemetry.sdk.trace.export import BatchSpanProcessor
        from opentelemetry.sdk.metrics import MeterProvider
        from opentelemetry.sdk.metrics.export import PeriodicExportingMetricReader
        from opentelemetry.sdk.resources import Resource
        from opentelemetry.exporter.otlp.proto.http.trace_exporter import OTLPSpanExporter
        from opentelemetry.exporter.otlp.proto.http.metric_exporter import OTLPMetricExporter
        from opentelemetry.instrumentation.fastapi import FastAPIInstrumentor
    except ImportError as e:
        _logger.warning("OpenTelemetry packages not installed; telemetry disabled: %s", e)
        return

    service_name = os.environ.get("OTEL_SERVICE_NAME", "classifier-service")
    resource = Resource.create({"service.name": service_name})
    base_url = endpoint if endpoint.startswith("http") else f"http://{endpoint}"
    base_url = base_url.rstrip("/")

    # Tracing
    tracer_provider = TracerProvider(resource=resource)
    span_exporter = OTLPSpanExporter(endpoint=f"{base_url}/v1/traces")
    tracer_provider.add_span_processor(BatchSpanProcessor(span_exporter))
    trace.set_tracer_provider(tracer_provider)

    # Metrics (OTLP export to collector)
    metric_exporter = OTLPMetricExporter(endpoint=f"{base_url}/v1/metrics")
    reader = PeriodicExportingMetricReader(metric_exporter, export_interval_millis=15000)
    meter_provider = MeterProvider(resource=resource, metric_readers=[reader])
    metrics.set_meter_provider(meter_provider)
    global _meter
    _meter = meter_provider.get_meter("classifier-service", "1.0.0")

    # Instrument FastAPI (creates spans for each request)
    FastAPIInstrumentor.instrument_app(app)

    _logger.info("Telemetry enabled: service_name=%s, endpoint=%s (traces + metrics)", service_name, base_url)


def get_tracer():
    """Returns the current tracer if telemetry is set up, otherwise None (caller should no-op)."""
    try:
        from opentelemetry import trace
        return trace.get_tracer(__name__, "1.0.0")
    except Exception:
        return None


def get_meter():
    """Returns the global meter if metrics are set up, otherwise None (caller should no-op)."""
    return _meter


def _counter(name: str, description: str):
    m = get_meter()
    if m is None:
        return None
    try:
        return m.create_counter(name, description=description, unit="1")
    except Exception:
        return None


_classifier_counter = None
_train_counter = None
_evaluate_counter = None


def record_classifier_call(status: str, model_id: str) -> None:
    """Increments classifier_requests_total counter (status=success|error, model_id=...). No-op if meter not set."""
    global _classifier_counter
    if _classifier_counter is None:
        _classifier_counter = _counter("classifier_requests_total", "Total classifier classification requests")
    if _classifier_counter is not None:
        try:
            _classifier_counter.add(1, {"status": status, "model_id": (model_id or "default")[:64]})
        except Exception as e:
            _logger.debug("Could not record classifier metric: %s", e)


def record_train_complete(model_id: str) -> None:
    """Increments classifier_train_total counter. No-op if meter not set."""
    global _train_counter
    if _train_counter is None:
        _train_counter = _counter("classifier_train_total", "Total training jobs completed")
    if _train_counter is not None:
        try:
            _train_counter.add(1, {"model_id": (model_id or "")[:64]})
        except Exception as e:
            _logger.debug("Could not record train metric: %s", e)


def record_evaluate_complete(model_id: str) -> None:
    """Increments classifier_evaluate_total counter. No-op if meter not set."""
    global _evaluate_counter
    if _evaluate_counter is None:
        _evaluate_counter = _counter("classifier_evaluate_total", "Total evaluation runs completed")
    if _evaluate_counter is not None:
        try:
            _evaluate_counter.add(1, {"model_id": (model_id or "default")[:64]})
        except Exception as e:
            _logger.debug("Could not record evaluate metric: %s", e)


MAX_ATTR_LEN = 512


def _truncate(value: str | None) -> str:
    if value is None:
        return ""
    s = str(value)
    return s if len(s) <= MAX_ATTR_LEN else s[:MAX_ATTR_LEN] + "..."


def run_traced(
    span_name: str,
    fn,
    input_attrs: dict | None = None,
    output_attr: str | None = None,
    output_value_fn=None,
):
    """
    Runs fn() inside a new span. Records input_attrs on start; on success records output_attr
    from output_value_fn(result) if provided; on exception records error and re-raises.
    No-op if tracer is not configured (returns fn() result without span).
    """
    tracer = get_tracer()
    if not tracer:
        return fn()
    span = tracer.start_span(span_name)
    try:
        if input_attrs:
            for k, v in input_attrs.items():
                if v is not None:
                    span.set_attribute(k, _truncate(str(v)))
        result = fn()
        if output_attr and output_value_fn is not None:
            out_val = output_value_fn(result)
            if out_val is not None:
                span.set_attribute(output_attr, _truncate(str(out_val)))
        from opentelemetry.trace import Status, StatusCode
        span.set_status(Status(StatusCode.OK))
        return result
    except Exception as e:
        from opentelemetry.trace import Status, StatusCode
        span.set_status(Status(StatusCode.ERROR, str(e)))
        span.record_exception(e)
        raise
    finally:
        span.end()
