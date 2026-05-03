#!/usr/bin/env bash
# Delegates to the canonical operator entry point: docker/scripts/up.sh (repo root).
# Do not duplicate compose -f chains here; see docker/README.md and docker/scripts/README.md.
set -euo pipefail
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
exec "$REPO_ROOT/docker/scripts/up.sh" "$@"
