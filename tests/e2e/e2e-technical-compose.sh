#!/usr/bin/env bash
# E2E technical (without frontend) to validate backend<->classifier<->DB.
# Starts the stack with docker compose, executes the extended smoke test and validates (optional) observability.
#
# Usage (from repo root):
#   ./tests/e2e/e2e-technical-compose.sh [--obs] [--gpu] [--keep]
#
# Notes:
# - The existing smoke test assumes backend on 9000 and classifier on 8000, but respects the host ports
#   via environment variables (BACKEND_PORT / CLASSIFIER_SERVICE_PORT).
# - If you activate --obs, additional checks of Prometheus/Jaeger/Grafana and the /metrics endpoint of the OTEL Collector are made.

set -euo pipefail

# tests/e2e/ -> repo root
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
DOCKER_DIR="${ROOT_DIR}/docker"

WITH_OBS="false"
WITH_GPU="false"
KEEP="false"

for arg in "$@"; do
  case "$arg" in
    --obs) WITH_OBS="true" ;;
    --gpu) WITH_GPU="true" ;;
    --keep) KEEP="true" ;;
    *)
      echo "Uso: $0 [--obs] [--gpu] [--keep]"
      exit 2
      ;;
  esac
done

BACKEND_HOST_PORT="${BACKEND_PORT:-9000}"
CLASSIFIER_HOST_PORT="${CLASSIFIER_SERVICE_PORT:-8000}"

wait_for_http() {
  local url="$1"
  local label="$2"
  local attempts="${3:-30}"
  local sleep_s="${4:-2}"
  echo -n "${label} ... "
  for _ in $(seq 1 "$attempts"); do
    if curl -sf "$url" > /dev/null; then
      echo "OK"
      return 0
    fi
    sleep "$sleep_s"
  done
  echo "FAIL"
  return 1
}

COMPOSE_FILES=(-f "${DOCKER_DIR}/docker-compose.yml")
if [ "$WITH_OBS" = "true" ]; then
  COMPOSE_FILES+=(-f "${DOCKER_DIR}/compose.obs.yml")
fi
if [ "$WITH_GPU" = "true" ]; then
  COMPOSE_FILES+=(-f "${DOCKER_DIR}/compose.gpu.yml")
fi

ENV_ARGS=(
  --env-file "${ROOT_DIR}/db/.env"
  --env-file "${ROOT_DIR}/classifier-service/.env"
  --env-file "${ROOT_DIR}/rag-service/.env"
)
if [ "$WITH_GPU" = "true" ]; then
  ENV_ARGS+=(--env-file "${ROOT_DIR}/ollama/.env")
fi

cd "$DOCKER_DIR"
echo "Levantando stack E2E (obs=${WITH_OBS}, gpu=${WITH_GPU}) ..."
docker compose "${COMPOSE_FILES[@]}" "${ENV_ARGS[@]}" up -d

echo "Esperando salud de servicios..."
wait_for_http "http://localhost:${CLASSIFIER_HOST_PORT}/health" "classifier-service /health"
wait_for_http "http://localhost:${BACKEND_HOST_PORT}/actuator/health" "backend /actuator/health" 60 3 || true

echo "Ejecutando smoke test..."
"${ROOT_DIR}/rag-service/scripts/smoke-test.sh" "http://localhost:${BACKEND_HOST_PORT}" "http://localhost:${CLASSIFIER_HOST_PORT}"

if [ "$WITH_OBS" = "true" ]; then
  PROMETHEUS_PORT="${PROMETHEUS_PORT:-9090}"
  JAEGER_UI_PORT="${JAEGER_UI_PORT:-16686}"
  GRAFANA_PORT="${GRAFANA_PORT:-3000}"
  OTEL_PROMETHEUS_SCRAPE_PORT="${OTEL_PROMETHEUS_SCRAPE_PORT:-8889}"

  echo "Checks de observabilidad (opcional) ..."
  wait_for_http "http://localhost:${JAEGER_UI_PORT}/" "Jaeger UI" || true
  wait_for_http "http://localhost:${PROMETHEUS_PORT}/-/healthy" "Prometheus /-/healthy" || true
  wait_for_http "http://localhost:${GRAFANA_PORT}/api/health" "Grafana /api/health" || true
  wait_for_http "http://localhost:${OTEL_PROMETHEUS_SCRAPE_PORT}/metrics" "OTEL Collector /metrics" || true
fi

if [ "$KEEP" = "false" ]; then
  echo "Bajando stack E2E..."
  docker compose "${COMPOSE_FILES[@]}" "${ENV_ARGS[@]}" down
else
  echo "Manteniendo stack E2E en marcha (keep=true)."
fi

echo "E2E tecnico finalizado."

