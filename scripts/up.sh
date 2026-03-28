#!/usr/bin/env bash
# Start stacks dev (hybrid) or prod-local. Implementation: ./scripts/docker-compose.sh
#
# Usage:
#   ./scripts/up.sh <dev|prod> [env options] [stack options]
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$SCRIPT_DIR/docker-compose.sh" up "$@"
