#!/usr/bin/env bash
# Optional local Path B: Testcontainers PostgreSQL (pgvector image) + same init SQL as Java tests.
# Prerequisites: Docker daemon running; Python 3.11+ with pip.
# Does not start rag-service. For full-stack HTTP tests against Compose, use run-integration-tests.sh instead.
# CI uses Path A only (GitHub Actions Postgres service + pytest HTTP); do not enable TC there.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
export INTEGRATION_USE_TESTCONTAINERS=1
python -m pip install -q -r tests/integration/requirements.txt
exec python -m pytest tests/integration/test_tc_postgres_smoke.py -v --tb=short
