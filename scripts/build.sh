#!/usr/bin/env bash
# docker compose build with the same -f and --env-file chain as up.sh.
# Detail: ./scripts/docker-compose.sh
#
# Usage:
#   ./scripts/build.sh <dev|prod> [env options] [stack options]
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/docker-compose.sh" build "$@"
