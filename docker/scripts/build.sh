#!/usr/bin/env bash
# docker compose build with the same -f and --env-file chain as up.sh.
# Usage (repository root): ./docker/scripts/build.sh <dev|prod> [env options] [stack options]
set -euo pipefail
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
exec "$HERE/docker-compose.sh" build "$@"
