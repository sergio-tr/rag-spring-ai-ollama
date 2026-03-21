#!/usr/bin/env bash
# Integration tests with the stack running (repo root).
# Optional: export INTEGRATION_CHECK_OBS=1 if you started compose.obs.yml
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"
python -m pip install -q -r tests/integration/requirements.txt
exec python -m pytest tests/integration -v --tb=short
