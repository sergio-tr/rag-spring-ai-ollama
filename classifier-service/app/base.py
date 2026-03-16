"""
Base classes for app components: logging and services.
All services and pipeline/registry components use these to avoid repeated boilerplate.
"""
import logging
from typing import Any


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
