#!/usr/bin/env bash
# Post-deploy health checks for prod --server (self-hosted runner).
# 1) Backend liveness inside the container (reliable, no TLS).
# 2) Reverse-proxy HTTPS on localhost with retries (stack warm-up).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
DOCKER_DIR="$ROOT_DIR/docker"

HTTPS_PORT="${PRODUCTION_HTTPS_PORT:-443}"
PROXY_URL="${DEPLOY_HEALTH_URL:-https://127.0.0.1:${HTTPS_PORT}/actuator/health/liveness}"
BACKEND_PORT="${SERVER_PORT:-9000}"
MAX_WAIT_SEC="${DEPLOY_HEALTH_MAX_WAIT_SEC:-180}"
SLEEP_SEC="${DEPLOY_HEALTH_RETRY_SEC:-5}"

COMPOSE=(
  docker compose
  -f "$DOCKER_DIR/docker-compose.yml"
  -f "$DOCKER_DIR/compose.obs.yml"
  -f "$DOCKER_DIR/compose.prod.yml"
  -f "$DOCKER_DIR/compose.prod-server.yml"
  -f "$DOCKER_DIR/compose.prod-obs.yml"
  --profile observability
)

cd "$DOCKER_DIR"

wait_for_running() {
  local service="$1"
  local elapsed=0
  while [ "$elapsed" -lt "$MAX_WAIT_SEC" ]; do
    if "${COMPOSE[@]}" ps --status running "$service" 2>/dev/null | grep -q "$service"; then
      return 0
    fi
    sleep "$SLEEP_SEC"
    elapsed=$((elapsed + SLEEP_SEC))
  done
  echo "::error::Timed out waiting for $service to reach running state (${MAX_WAIT_SEC}s)." >&2
  "${COMPOSE[@]}" ps || true
  return 1
}

echo "Waiting for backend and reverse-proxy containers..."
wait_for_running backend
wait_for_running reverse-proxy

echo "Checking backend liveness (in-container, no TLS)..."
elapsed=0
until "${COMPOSE[@]}" exec -T backend curl -sf --max-time 15 \
  "http://127.0.0.1:${BACKEND_PORT}/actuator/health/liveness" >/dev/null 2>&1; do
  if [ "$elapsed" -ge "$MAX_WAIT_SEC" ]; then
    echo "::error::Backend liveness failed after ${MAX_WAIT_SEC}s." >&2
    "${COMPOSE[@]}" logs --tail 80 backend || true
    exit 1
  fi
  echo "Backend not ready yet (${elapsed}s / ${MAX_WAIT_SEC}s)..."
  sleep "$SLEEP_SEC"
  elapsed=$((elapsed + SLEEP_SEC))
done
echo "Backend liveness: OK"

echo "Checking reverse-proxy HTTPS: $PROXY_URL"
elapsed=0
until curl -fsSk --max-time 20 "$PROXY_URL" >/dev/null 2>&1; do
  if [ "$elapsed" -ge "$MAX_WAIT_SEC" ]; then
    echo "::error::Reverse-proxy HTTPS health failed after ${MAX_WAIT_SEC}s." >&2
    echo "Proxy URL: $PROXY_URL" >&2
    "${COMPOSE[@]}" ps reverse-proxy backend webapp || true
    "${COMPOSE[@]}" logs --tail 80 reverse-proxy || true
    exit 1
  fi
  echo "Reverse-proxy HTTPS not ready (${elapsed}s / ${MAX_WAIT_SEC}s)..."
  sleep "$SLEEP_SEC"
  elapsed=$((elapsed + SLEEP_SEC))
done
echo "Reverse-proxy HTTPS health: OK"
