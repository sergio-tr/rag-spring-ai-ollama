#!/usr/bin/env python3
"""Backward-compatible entry: use retrieval_benchmark.py (schema v1, scenarios)."""

from __future__ import annotations

import warnings

from retrieval_benchmark import main

if __name__ == "__main__":
    warnings.warn(
        "performance_baseline.py is deprecated; use retrieval_benchmark.py",
        DeprecationWarning,
        stacklevel=2,
    )
    raise SystemExit(main())
