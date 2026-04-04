#!/usr/bin/env python3
"""Backward-compatible entry: use infra_probe.py (same behaviour)."""

from __future__ import annotations

import warnings

from infra_probe import main

if __name__ == "__main__":
    warnings.warn(
        "actuator_latency_baseline.py is an alias for infra_probe.py; prefer infra_probe.py",
        DeprecationWarning,
        stacklevel=2,
    )
    raise SystemExit(main())
