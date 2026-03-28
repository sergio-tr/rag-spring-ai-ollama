"""
Base classes for app components: logging and services.
All services and pipeline/registry components use these to avoid repeated boilerplate.
"""
import logging
from typing import Any, Callable, TypeVar

from app.telemetry import run_traced as _run_traced

T = TypeVar("T")


class Loggable:
    """Mixin that provides a logger for any class. Use for components that need logging."""

    @property
    def _logger(self) -> logging.Logger:
        name = f"{type(self).__module__}.{type(self).__qualname__}"
        return logging.getLogger(name)


class BaseService(Loggable):
    """
    Base class for all application services (ClassificationService, ModelRegistryService, etc.).
    Provides a logger and a single place to add common behaviour (e.g. error mapping, metrics).
    """

    def __init__(self) -> None:
        pass

    @property
    def logger(self) -> logging.Logger:
        """Logger for this service instance."""
        return self._logger


class TracedService(BaseService):
    """
    Base class for services that want to run operations inside OpenTelemetry spans.
    Subclasses call self.run_traced(span_name, fn, input_attrs=..., output_attr=..., output_value_fn=...)
    to wrap the implementation in a span with parameters and result/error recorded.
    """

    def run_traced(
        self,
        span_name: str,
        fn: Callable[[], T],
        *,
        input_attrs: dict | None = None,
        output_attr: str | None = None,
        output_value_fn: Callable[[T], Any] | None = None,
    ) -> T:
        """Runs fn() inside a span; records input_attrs and optional output. No-op if tracer not configured."""
        return _run_traced(
            span_name,
            fn,
            input_attrs=input_attrs,
            output_attr=output_attr,
            output_value_fn=output_value_fn,
        )
