#!/usr/bin/env bash
# For the same Compose project as up: use the same mode and flags as when starting.
#
# Usage:
#   ./scripts/down.sh              # prod (default), same as before
#   ./scripts/down.sh prod [--all] [--obs] [--gpu|--ollama] [--logs] [--infra] [--volumes]
#   ./scripts/down.sh dev  [--all] [--rag] [--obs] [--classifier] ... [--volumes]
#
# Examples:
#   ./scripts/down.sh dev --all              # stop backend-dev, ollama, obs, etc.
#   ./scripts/down.sh prod --obs --gpu
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
case "${1:-}" in
  dev|prod)
    exec "$SCRIPT_DIR/docker-compose.sh" down "$@"
    ;;
  *)
    exec "$SCRIPT_DIR/docker-compose.sh" down prod "$@"
    ;;
esac
