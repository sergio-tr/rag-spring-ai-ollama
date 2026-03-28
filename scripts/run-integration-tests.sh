#!/usr/bin/env bash
# Integration tests with the stack running (repo root).
# Observability: default auto-detects compose.obs.yml (OTEL collector :8889/metrics).
# INTEGRATION_CHECK_OBS=1 to require observability; INTEGRATION_CHECK_OBS=0 to skip it.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
python -m pip install -q -r tests/integration/requirements.txt
exec python -m pytest tests/integration -v --tb=short
