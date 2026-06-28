#!/usr/bin/env bash
# Canonical entrypoint: demo Lab stack (backend-dev + webapp + classifier + nginx on :8444).
# Run from repository root. Does not modify git state.
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "${HERE}/up.sh" dev --rag --proxy --classifier --no-env-prompt "$@"
