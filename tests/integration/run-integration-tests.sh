#!/usr/bin/env bash
# Integration tests with the stack running (repo root).
# Observability: default auto-detects compose.obs.yml (OTEL collector :8889/metrics).
# INTEGRATION_CHECK_OBS=1 to require observability; INTEGRATION_CHECK_OBS=0 to skip it.
# Path A (default): HTTP-only pytest; Postgres comes from Compose or from CI service - not Testcontainers Python.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
export INTEGRATION_USE_TESTCONTAINERS="${INTEGRATION_USE_TESTCONTAINERS:-0}"
python -m pip install -q -r tests/integration/requirements.txt
exec python -m pytest tests/integration -v --tb=short --ignore=tests/integration/test_tc_postgres_smoke.py
