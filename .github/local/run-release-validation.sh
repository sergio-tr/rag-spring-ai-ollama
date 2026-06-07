#!/usr/bin/env bash
# Release validation wrapper: full closure lane, including fullstack, performance, and Docker guards.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==> release validation: local closure CI"
"${SCRIPT_DIR}/run-closure-ci-local.sh" "$@"

echo "==> release validation: completed"
