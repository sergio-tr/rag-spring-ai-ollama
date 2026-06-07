#!/usr/bin/env bash
# Capture docker ps, health probes, and service logs for closure evidence.
# Usage: ./docker/scripts/capture-runtime-evidence.sh [--obs] [--logs] [--infra] [--mail] [--ollama] [--out DIR]
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
DOCKER_DIR="$ROOT_DIR/docker"
WITH_OBS=false
WITH_LOGS=false
WITH_INFRA=false
WITH_MAIL=false
WITH_OLLAMA=false
OUT_DIR="${ROOT_DIR}/.cursor/context/evidence/docker/agent-run-$(date -u +%Y%m%dT%H%M%SZ)"

while [ $# -gt 0 ]; do
  case "$1" in
    --obs) WITH_OBS=true; shift ;;
    --logs) WITH_LOGS=true; shift ;;
    --infra) WITH_INFRA=true; shift ;;
    --mail) WITH_MAIL=true; shift ;;
    --ollama|--gpu) WITH_OLLAMA=true; shift ;;
    --out)
      shift
      [ $# -lt 1 ] && { echo "Error: --out requires a directory" >&2; exit 2; }
      OUT_DIR="$1"
      shift
      ;;
    -h|--help)
      echo "Usage: $0 [--obs] [--logs] [--infra] [--mail] [--ollama] [--out DIR]"
      exit 0
      ;;
    *) echo "Unknown argument: $1" >&2; exit 2 ;;
  esac
done

mkdir -p "$OUT_DIR/logs" "$OUT_DIR/observability"

COMPOSE_ARGS=(-f "$DOCKER_DIR/docker-compose.yml" -f "$DOCKER_DIR/compose.prod.yml" -f "$DOCKER_DIR/compose.prod-host-ports.yml")
[ "$WITH_OBS" = true ] && COMPOSE_ARGS+=(-f "$DOCKER_DIR/compose.obs.yml" --profile observability)
[ "$WITH_LOGS" = true ] && COMPOSE_ARGS+=(--profile logs)
[ "$WITH_INFRA" = true ] && COMPOSE_ARGS+=(--profile infra)
[ "$WITH_MAIL" = true ] && COMPOSE_ARGS+=(-f "$DOCKER_DIR/compose.prod-mail.yml" --profile dev-mail)
[ "$WITH_OLLAMA" = true ] && COMPOSE_ARGS+=(--profile ollama)

for f in "$ROOT_DIR/db/.env" "$ROOT_DIR/classifier-service/.env" "$ROOT_DIR/rag-service/.env" "$ROOT_DIR/webapp/.env"; do
  [ -f "$f" ] && COMPOSE_ARGS+=(--env-file "$f")
done
if [ "$WITH_OBS" = true ] || [ "$WITH_LOGS" = true ] || [ "$WITH_INFRA" = true ]; then
  [ -f "$ROOT_DIR/observability/.env" ] && COMPOSE_ARGS+=(--env-file "$ROOT_DIR/observability/.env")
fi
[ "$WITH_OLLAMA" = true ] && [ -f "$ROOT_DIR/ollama/.env" ] && COMPOSE_ARGS+=(--env-file "$ROOT_DIR/ollama/.env")

{
  echo "# Runtime evidence capture"
  echo "timestamp_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  echo "out_dir=$OUT_DIR"
  echo "obs=$WITH_OBS logs=$WITH_LOGS infra=$WITH_INFRA mail=$WITH_MAIL ollama=$WITH_OLLAMA"
} >"$OUT_DIR/META.txt"

docker compose "${COMPOSE_ARGS[@]}" ps >"$OUT_DIR/docker-ps.txt" 2>&1 || true

for svc in postgres classifier-service backend webapp reverse-proxy; do
  docker compose "${COMPOSE_ARGS[@]}" logs --tail 200 "$svc" >"$OUT_DIR/logs/${svc}.log" 2>&1 || true
done
[ "$WITH_MAIL" = true ] && docker compose "${COMPOSE_ARGS[@]}" logs --tail 120 mailpit >"$OUT_DIR/logs/mailpit.log" 2>&1 || true
if [ "$WITH_LOGS" = true ]; then
  for svc in loki promtail; do
    docker compose "${COMPOSE_ARGS[@]}" logs --tail 120 "$svc" >"$OUT_DIR/logs/${svc}.log" 2>&1 || true
  done
fi

if [ "$WITH_OBS" = true ]; then
  for svc in prometheus grafana jaeger otel-collector; do
    docker compose "${COMPOSE_ARGS[@]}" logs --tail 120 "$svc" >"$OUT_DIR/logs/${svc}.log" 2>&1 || true
  done
fi

probe() {
  local name="$1"
  local url="$2"
  local out="$OUT_DIR/observability/${name}.txt"
  {
    echo "url=$url"
    echo "timestamp_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
    curl -ksS -o /tmp/capture-runtime-body -w "http_code=%{http_code}\n" --max-time 10 "$url" 2>&1 || echo "curl_exit=$?"
    echo "--- body (first 2k) ---"
    head -c 2048 /tmp/capture-runtime-body 2>/dev/null || true
    echo
  } >"$out"
}

BACKEND_DIRECT="${BACKEND_PORT:-9000}"
PROXY="${REVERSE_PROXY_HTTP_PORT:-80}"
probe "backend-liveness-direct" "http://127.0.0.1:${BACKEND_DIRECT}/actuator/health/liveness"
probe "backend-readiness-direct" "http://127.0.0.1:${BACKEND_DIRECT}/actuator/health/readiness"
probe "backend-health-proxy" "http://127.0.0.1:${PROXY}/actuator/health"
probe "classifier-health-direct" "http://127.0.0.1:${CLASSIFIER_SERVICE_PORT:-8000}/health"
probe "webapp-proxy-login" "http://127.0.0.1:${PROXY}/en/login"
[ "$WITH_MAIL" = true ] && probe "mailpit-ui" "http://127.0.0.1:${MAILPIT_HTTP_PORT:-8025}/"

if [ "$WITH_OBS" = true ]; then
  probe "prometheus" "http://127.0.0.1:${PROMETHEUS_PORT:-9090}/-/healthy"
  probe "grafana" "http://127.0.0.1:${GRAFANA_PORT:-3000}/api/health"
  probe "jaeger-services" "http://127.0.0.1:${JAEGER_UI_PORT:-16686}/api/services"
fi

echo "Evidence written to $OUT_DIR"
