#!/usr/bin/env bash
# Start "prod local" stack: prod hardening + reverse proxy.
#
# Default includes observability (internal ports) and hardening.
# Optional:
#   --no-obs   : do not include compose.obs.yml
#   --gpu      : include compose.gpu.yml (requires ollama/.env and GPU runtime)
#
# Run from repository root:
#   ./scripts/up-prod-local.sh [--no-obs] [--gpu]
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DOCKER_DIR="$ROOT_DIR/docker"

WITH_OBS=true
WITH_GPU=false

for arg in "$@"; do
  case "$arg" in
    --no-obs) WITH_OBS=false ;;
    --gpu) WITH_GPU=true ;;
    *)
      echo "Unknown argument: $arg" >&2
      echo "Usage: $0 [--no-obs] [--gpu]" >&2
      exit 1
      ;;
  esac
done

# Compose files (apply prod override last to keep hardened ports).
COMPOSE_FILES=(-f "docker-compose.yml" -f "compose.prod.yml")
if [ "$WITH_OBS" = true ]; then
  COMPOSE_FILES=(-f "docker-compose.yml" -f "compose.obs.yml" -f "compose.prod.yml")
fi
if [ "$WITH_GPU" = true ]; then
  if [ "$WITH_OBS" = true ]; then
    COMPOSE_FILES=(-f "docker-compose.yml" -f "compose.obs.yml" -f "compose.gpu.yml" -f "compose.prod.yml")
  else
    COMPOSE_FILES=(-f "docker-compose.yml" -f "compose.gpu.yml" -f "compose.prod.yml")
  fi
fi

ENV_ARGS=()
add_env_file() {
  local f="$1"
  if [ -f "$f" ]; then
    ENV_ARGS+=(--env-file "$f")
  else
    echo "Warning: env file not found: $f" >&2
  fi
}

add_env_file "$ROOT_DIR/db/.env"
add_env_file "$ROOT_DIR/classifier-service/.env"
add_env_file "$ROOT_DIR/rag-service/.env"

if [ "$WITH_OBS" = true ]; then
  add_env_file "$ROOT_DIR/observability/.env"
fi
if [ "$WITH_GPU" = true ]; then
  add_env_file "$ROOT_DIR/ollama/.env"
fi

cd "$DOCKER_DIR"
docker compose "${COMPOSE_FILES[@]}" "${ENV_ARGS[@]}" up -d

echo "Prod local started (obs=$WITH_OBS, gpu=$WITH_GPU)."

