#!/usr/bin/env bash
# Start dev (hybrid) or prod-local stacks. Runs docker-compose.sh from the same directory.
# Usage (repository root): ./docker/scripts/up.sh <dev|prod> [env options] [stack options]
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$HERE/docker-compose.sh" up "$@"
