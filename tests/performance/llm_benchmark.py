#!/usr/bin/env python3
"""
LLM-oriented micro-benchmark wrapper: same runner as retrieval_benchmark.py with --family llm default.

Emphasizes token/cost lines in the report; HTTP paths are identical for a given scenario.
"""

from __future__ import annotations


from retrieval_benchmark import main

if __name__ == "__main__":
    raise SystemExit(main(default_family="llm"))
