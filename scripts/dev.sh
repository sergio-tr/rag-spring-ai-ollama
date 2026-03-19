#!/usr/bin/env bash
# Start development infrastructure (Docker services that rarely change).
# Backend, classifier and frontend are meant to run locally with hot-reload.
#
# Usage (from repo root):
#   ./scripts/dev.sh [--gpu] [--obs] [--classifier] [--down] [--volumes]
#
# Options:
#   --gpu         Include Ollama in Docker (GPU runtime; requires NVIDIA Container Toolkit)
#   --obs         Include observability stack (OTEL, Prometheus, Grafana, Jaeger)
#   --classifier  Also start classifier-service in Docker (with hot-reload via compose.dev.yml)
#   --down        Stop the dev infrastructure instead of starting it
#   --volumes     When used with --down, also remove named volumes (clears DB data)
#
# Default (no flags): only postgres in Docker.
#   Backend → run locally:    cd rag-service && ../mvnw spring-boot:run -Dspring-boot.run.profiles=dev
#   Classifier → run locally: cd classifier-service && uvicorn main:app --reload --reload-dir app
#   Frontend → run locally:   cd frontend && npm run dev

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DOCKER_DIR="$ROOT_DIR/docker"

WITH_GPU=false
WITH_OBS=false
WITH_CLASSIFIER=false
ACTION=up
WITH_VOLUMES=false

for arg in "$@"; do
  case "$arg" in
    --gpu)        WITH_GPU=true ;;
    --obs)        WITH_OBS=true ;;
    --classifier) WITH_CLASSIFIER=true ;;
    --down)       ACTION=down ;;
    --volumes)    WITH_VOLUMES=true ;;
    *)
      echo "Unknown argument: $arg" >&2
      echo "Usage: $0 [--gpu] [--obs] [--classifier] [--down] [--volumes]" >&2
      exit 1
      ;;
  esac
done

# --- Compose files ---
COMPOSE_FILES=(-f "docker-compose.yml")

if [ "$WITH_CLASSIFIER" = true ]; then
  COMPOSE_FILES+=(-f "compose.dev.yml")
fi
if [ "$WITH_OBS" = true ]; then
  COMPOSE_FILES+=(-f "compose.obs.yml")
fi
if [ "$WITH_GPU" = true ]; then
  COMPOSE_FILES+=(-f "compose.gpu.yml")
fi

# --- Env files ---
ENV_ARGS=()
add_env_file() {
  local f="$1"
  if [ -f "$f" ]; then
    ENV_ARGS+=(--env-file "$f")
  else
    echo "Warning: env file not found: $f (run scripts/create-env-all.sh first)" >&2
  fi
}

add_env_file "$ROOT_DIR/db/.env"

if [ "$WITH_CLASSIFIER" = true ]; then
  add_env_file "$ROOT_DIR/classifier-service/.env"
fi
if [ "$WITH_OBS" = true ]; then
  add_env_file "$ROOT_DIR/observability/.env"
fi
if [ "$WITH_GPU" = true ]; then
  add_env_file "$ROOT_DIR/ollama/.env"
fi

cd "$DOCKER_DIR"

if [ "$ACTION" = down ]; then
  DOWN_ARGS=("${COMPOSE_FILES[@]}" "${ENV_ARGS[@]}" down)
  [ "$WITH_VOLUMES" = true ] && DOWN_ARGS+=(-v)
  docker compose "${DOWN_ARGS[@]}"
  echo "Dev infrastructure stopped."
  exit 0
fi

# --- Services to start (only infra; backend/classifier/frontend run locally) ---
SERVICES=(postgres)
[ "$WITH_GPU" = true ]        && SERVICES+=(ollama)
[ "$WITH_OBS" = true ]        && SERVICES+=(otel-collector jaeger prometheus grafana)
[ "$WITH_CLASSIFIER" = true ] && SERVICES+=(classifier-service)

docker compose "${COMPOSE_FILES[@]}" "${ENV_ARGS[@]}" up -d "${SERVICES[@]}"

echo ""
echo "Dev infrastructure started."
echo ""
echo "Services in Docker:"
for s in "${SERVICES[@]}"; do
  echo "  • $s"
done
echo ""
echo "Run locally (with hot-reload):"
if [ "$WITH_CLASSIFIER" = false ]; then
  echo "  Classifier:  cd classifier-service && uvicorn main:app --reload --reload-dir app --port 8000"
fi
echo "  Backend:     cd rag-service && ../mvnw spring-boot:run -Dspring-boot.run.profiles=dev"
echo "  Frontend:    cd frontend && npm run dev"
echo ""
echo "Postgres available at:  localhost:${POSTGRES_PORT:-5432}"
[ "$WITH_GPU" = true ]  && echo "Ollama available at:    localhost:${OLLAMA_PORT:-11434}"
[ "$WITH_OBS" = true ]  && echo "Grafana available at:   localhost:${GRAFANA_PORT:-3000}"
[ "$WITH_OBS" = true ]  && echo "Jaeger available at:    localhost:${JAEGER_UI_PORT:-16686}"
