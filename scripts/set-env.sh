#!/usr/bin/env bash
# Interactive: create .env files for each component, then choose and run a docker-compose option.
# Run from repository root: ./scripts/set-env.sh
set -e
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
cd "$ROOT_DIR"

echo "=== Create environment files ==="
echo "Answer y to create each .env from its .env.example (only creates if file is missing)."
echo ""

prompt_create() {
  local name="$1"
  local script="$2"
  echo -n "Create $name? [y/N] "
  read -r answer
  case "${answer:-n}" in
    y|Y) "$SCRIPT_DIR/$script";;
    *) echo "Skipped $name.";;
  esac
}

prompt_create "db/.env" "create-env-db.sh"
prompt_create "observability/.env" "create-env-observability.sh"
prompt_create "rag-service/.env" "create-env-rag-service.sh"
prompt_create "classifier-service/.env" "create-env-classifier-service.sh"
prompt_create "ollama/.env" "create-env-ollama.sh"

echo ""
echo "=== Run Docker Compose ==="
echo "  1) Main stack only (postgres, classifier, backend)"
echo "  2) Main stack + observability (Jaeger, Prometheus, Grafana, OTEL)"
echo "  3) Main stack + GPU (Ollama in container with GPU)"
echo "  4) Main stack + observability + GPU"
echo "  5) Skip (do not run compose)"
echo ""
echo -n "Choose [1-5] (default 5): "
read -r choice
choice="${choice:-5}"

DOCKER_DIR="$ROOT_DIR/docker"
DB_ENV="$ROOT_DIR/db/.env"
OBS_ENV="$ROOT_DIR/observability/.env"
CLASSIFIER_ENV="$ROOT_DIR/classifier-service/.env"
RAG_ENV="$ROOT_DIR/rag-service/.env"
OLLAMA_ENV="$ROOT_DIR/ollama/.env"

run_compose() {
  local args=("$@")
  (cd "$DOCKER_DIR" && docker compose "${args[@]}")
}

# Build env-file list for main stack and GPU (only include files that exist)
main_env_args() {
  local args=()
  [ -f "$DB_ENV" ] && args+=(--env-file "$DB_ENV")
  [ -f "$CLASSIFIER_ENV" ] && args+=(--env-file "$CLASSIFIER_ENV")
  [ -f "$RAG_ENV" ] && args+=(--env-file "$RAG_ENV")
  [ -f "$OLLAMA_ENV" ] && args+=(--env-file "$OLLAMA_ENV")
  printf '%s\n' "${args[@]}"
}

case "$choice" in
  1)
    if [ ! -f "$DB_ENV" ]; then
      echo "Warning: $DB_ENV not found. Create it first (e.g. ./scripts/create-env-db.sh)." >&2
      exit 1
    fi
    run_compose $(main_env_args) up -d
    ;;
  2)
    if [ ! -f "$DB_ENV" ]; then
      echo "Warning: $DB_ENV not found. Create it first." >&2
      exit 1
    fi
    if [ ! -f "$OBS_ENV" ]; then
      echo "Warning: $OBS_ENV not found. Create it first (e.g. ./scripts/create-env-observability.sh)." >&2
      exit 1
    fi
    run_compose -f docker-compose.yml -f compose.obs.yml $(main_env_args) --env-file "$OBS_ENV" up -d
    ;;
  3)
    if [ ! -f "$DB_ENV" ]; then
      echo "Warning: $DB_ENV not found. Create it first." >&2
      exit 1
    fi
    run_compose -f docker-compose.yml -f compose.gpu.yml $(main_env_args) up -d
    ;;
  4)
    if [ ! -f "$DB_ENV" ]; then
      echo "Warning: $DB_ENV not found. Create it first." >&2
      exit 1
    fi
    if [ ! -f "$OBS_ENV" ]; then
      echo "Warning: $OBS_ENV not found. Create it first." >&2
      exit 1
    fi
    run_compose -f docker-compose.yml -f compose.obs.yml -f compose.gpu.yml $(main_env_args) --env-file "$OBS_ENV" up -d
    ;;
  5|*)
    echo "Skipped. Run compose manually from docker/ with the desired -f and --env-file options."
    exit 0
    ;;
esac

echo "Done."
