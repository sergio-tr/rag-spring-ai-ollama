#!/usr/bin/env bash
# Tear down the same Compose project as up (use the same mode and flags).
# Usage (repository root):
#   ./docker/scripts/down.sh              # prod default
#   ./docker/scripts/down.sh prod [flags]
#   ./docker/scripts/down.sh dev [flags]
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
case "${1:-}" in
  dev|prod)
    exec "$HERE/docker-compose.sh" down "$@"
    ;;
  *)
    exec "$HERE/docker-compose.sh" down prod "$@"
    ;;
esac
