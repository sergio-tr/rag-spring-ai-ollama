#!/usr/bin/env bash
# Non-interactive local demo smoke for the prod-local Compose path.
# Default mode expects Ollama on the host via rag-service/.env (host.docker.internal:11434).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
DOCKER_DIR="$ROOT_DIR/docker"

WITH_OBS=false
WITH_OBS_PRIVATE=false
WITH_OLLAMA=false
WITH_MAIL=false
SKIP_UP=false
DOWN_AFTER=false
TIMEOUT_SECONDS=180
EVIDENCE_DIR="${DEMO_SMOKE_EVIDENCE_DIR:-}"

usage() {
  local code="${1:-2}"
  echo "Usage: $0 [--obs] [--obs-private] [--ollama] [--mail] [--skip-up] [--down-after] [--timeout <seconds>]" >&2
  echo "  --obs        Include Prometheus/Grafana/Jaeger/OTEL checks." >&2
  echo "  --obs-private Include observability without publishing UI ports; skips localhost UI checks unless URLs are provided." >&2
  echo "  --ollama     Optional in-stack Ollama; requires NVIDIA Container Toolkit." >&2
  echo "  --mail       Start Mailpit and wire backend SMTP (profile dev-mail)." >&2
  echo "  --skip-up    Validate and smoke an already running stack." >&2
  echo "  --down-after Stop the stack after checks." >&2
  exit "$code"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --obs)
      WITH_OBS=true
      shift
      ;;
    --obs-private)
      WITH_OBS=true
      WITH_OBS_PRIVATE=true
      shift
      ;;
    --ollama|--gpu)
      WITH_OLLAMA=true
      shift
      ;;
    --mail)
      WITH_MAIL=true
      shift
      ;;
    --skip-up)
      SKIP_UP=true
      shift
      ;;
    --down-after)
      DOWN_AFTER=true
      shift
      ;;
    --timeout)
      shift
      [ $# -lt 1 ] && usage
      TIMEOUT_SECONDS="$1"
      shift
      ;;
    -h|--help)
      usage 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      ;;
  esac
done

has_nvidia_runtime() {
  docker info --format '{{json .Runtimes}}' 2>/dev/null | grep -q '"nvidia"'
}

if [ "$WITH_OLLAMA" = true ] && ! has_nvidia_runtime; then
  echo "ERROR: --ollama requested, but Docker does not report an NVIDIA runtime." >&2
  echo "Use the official host-Ollama mode, or install NVIDIA Container Toolkit." >&2
  exit 2
fi

COMPOSE_ARGS=(-f "$DOCKER_DIR/docker-compose.yml")
[ "$WITH_OBS" = true ] && COMPOSE_ARGS+=(-f "$DOCKER_DIR/compose.obs.yml")
COMPOSE_ARGS+=(-f "$DOCKER_DIR/compose.prod.yml")
COMPOSE_ARGS+=(-f "$DOCKER_DIR/compose.prod-host-ports.yml")
[ "$WITH_OBS_PRIVATE" = true ] && COMPOSE_ARGS+=(-f "$DOCKER_DIR/compose.prod-obs.yml")

[ "$WITH_OBS" = true ] && COMPOSE_ARGS+=(--profile observability)
[ "$WITH_OLLAMA" = true ] && COMPOSE_ARGS+=(--profile ollama)
[ "$WITH_MAIL" = true ] && COMPOSE_ARGS+=(-f "$DOCKER_DIR/compose.prod-mail.yml" --profile dev-mail)

add_env_file() {
  local f="$1"
  if [ -f "$f" ]; then
    COMPOSE_ARGS+=(--env-file "$f")
  else
    echo "Warning: env file not found: $f" >&2
  fi
}

add_env_file "$ROOT_DIR/db/.env"
add_env_file "$ROOT_DIR/classifier-service/.env"
add_env_file "$ROOT_DIR/rag-service/.env"
add_env_file "$ROOT_DIR/webapp/.env"
[ "$WITH_OBS" = true ] && add_env_file "$ROOT_DIR/observability/.env"
[ "$WITH_OLLAMA" = true ] && add_env_file "$ROOT_DIR/ollama/.env"

init_evidence_dir() {
  if [ -z "$EVIDENCE_DIR" ]; then
    EVIDENCE_DIR="$ROOT_DIR/.cursor/context/evidence/docker/demo-smoke-$(date -u +%Y%m%dT%H%M%SZ)"
  fi
  mkdir -p "$EVIDENCE_DIR/logs"
}

dump_failure_logs() {
  local reason="${1:-unknown}"
  init_evidence_dir
  echo "Saving failure logs to $EVIDENCE_DIR (reason: $reason)" >&2
  {
    echo "failure_reason=$reason"
    echo "timestamp_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  } >"$EVIDENCE_DIR/failure.txt"
  docker compose "${COMPOSE_ARGS[@]}" ps >"$EVIDENCE_DIR/docker-ps.txt" 2>&1 || true
  for svc in postgres classifier-service backend webapp reverse-proxy prometheus grafana jaeger; do
    docker compose "${COMPOSE_ARGS[@]}" logs --tail 150 "$svc" >"$EVIDENCE_DIR/logs/${svc}.log" 2>&1 || true
  done
}

wait_for_url() {
  local url="$1"
  local label="$2"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  echo -n "$label ... "
  while [ "$SECONDS" -lt "$deadline" ]; do
    if curl -ksSfL --max-time 8 "$url" >/dev/null; then
      echo "OK"
      return 0
    fi
    sleep 2
  done
  echo "FAIL"
  dump_failure_logs "$label"
  return 1
}

check_classifier_health() {
  local host_port="${CLASSIFIER_SERVICE_PORT:-8000}"
  echo -n "classifier-service /health (host :${host_port}) ... "
  if curl -ksSfL --max-time 8 "http://127.0.0.1:${host_port}/health" >/dev/null; then
    echo "OK"
    return 0
  fi
  echo "FAIL"
  dump_failure_logs "classifier-service /health"
  return 1
}

check_model_registry() {
  local email="${DEMO_SMOKE_EMAIL:-}"
  local password="${DEMO_SMOKE_PASSWORD:-}"
  local base_url="$1"
  local product_path="${RAG_API_PRODUCT_BASE_PATH:-/api/v5}"

  if [ -z "$email" ] || [ -z "$password" ]; then
    echo "model-registry authenticated check ... SKIP (set DEMO_SMOKE_EMAIL and DEMO_SMOKE_PASSWORD)"
    return 0
  fi

  echo -n "model-registry authenticated check ... "
  local token
  token="$(
    curl -ksSfL --max-time 15 \
      -H "Content-Type: application/json" \
      -d "{\"email\":\"${email}\",\"password\":\"${password}\"}" \
      "${base_url}${product_path}/auth/login" |
      python -c 'import json,sys; print(json.load(sys.stdin).get("accessToken",""))'
  )"
  if [ -z "$token" ]; then
    echo "FAIL"
    return 1
  fi
  curl -ksSfL --max-time 15 -H "Authorization: Bearer ${token}" "${base_url}${product_path}/model-registry" >/dev/null
  echo "OK"
}

UP_FLAGS=(prod --no-env-prompt)
[ "$WITH_OBS" = true ] && UP_FLAGS+=(--obs)
[ "$WITH_OBS_PRIVATE" = true ] && UP_FLAGS+=(--obs-private)
[ "$WITH_OLLAMA" = true ] && UP_FLAGS+=(--ollama)
[ "$WITH_MAIL" = true ] && UP_FLAGS+=(--mail)

DOWN_FLAGS=(prod)
[ "$WITH_OBS" = true ] && DOWN_FLAGS+=(--obs)
[ "$WITH_OBS_PRIVATE" = true ] && DOWN_FLAGS+=(--obs-private)
[ "$WITH_OLLAMA" = true ] && DOWN_FLAGS+=(--ollama)
[ "$WITH_MAIL" = true ] && DOWN_FLAGS+=(--mail)

echo "Compose config validation ..."
"$SCRIPT_DIR/docker-compose.sh" config "${UP_FLAGS[@]}"

if [ "$SKIP_UP" = false ]; then
  echo "Starting prod-local demo stack ..."
  "$SCRIPT_DIR/up.sh" "${UP_FLAGS[@]}"
fi

echo "Compose services ..."
docker compose "${COMPOSE_ARGS[@]}" ps

BASE_URL="${DEMO_SMOKE_BASE_URL:-http://127.0.0.1:${REVERSE_PROXY_HTTP_PORT:-80}}"
PROMETHEUS_URL="${DEMO_SMOKE_PROMETHEUS_URL:-http://127.0.0.1:${PROMETHEUS_PORT:-9090}}"
GRAFANA_URL="${DEMO_SMOKE_GRAFANA_URL:-http://127.0.0.1:${GRAFANA_PORT:-3000}}"
JAEGER_URL="${DEMO_SMOKE_JAEGER_URL:-http://127.0.0.1:${JAEGER_UI_PORT:-16686}}"
OLLAMA_URL="${DEMO_SMOKE_OLLAMA_URL:-}"
REQUIRED_OLLAMA_MODELS="${DEMO_SMOKE_REQUIRED_OLLAMA_MODELS:-gemma3:4b,mistral:7b,llama3.1:8b,mxbai-embed-large:latest,nomic-embed-text:latest,qwen3-embedding:latest}"

check_ollama_models() {
  echo -n "backend -> Ollama /api/tags required models ... "
  local payload
  if [ -n "$OLLAMA_URL" ]; then
    payload="$(curl -sSfL --max-time 10 "${OLLAMA_URL}/api/tags")" || {
      echo "FAIL"
      echo "Ollama not reachable at ${OLLAMA_URL}. Start host Ollama or set DEMO_SMOKE_OLLAMA_URL." >&2
      return 1
    }
  elif ! payload="$(docker compose "${COMPOSE_ARGS[@]}" exec -T backend sh -c 'set -eu; base="${SPRING_AI_OLLAMA_BASE_URL:-${OLLAMA_BASE_URL:-http://host.docker.internal:11434}}"; curl -sSfL --max-time 10 "${base%/}/api/tags"')"; then
    echo "FAIL"
    echo "Ollama is not reachable from the backend container. Start host Ollama or set OLLAMA_BASE_URL / SPRING_AI_OLLAMA_BASE_URL in rag-service/.env." >&2
    return 1
  fi
  if ! REQUIRED_OLLAMA_MODELS="$REQUIRED_OLLAMA_MODELS" python3 -c '
import json, os, sys
payload = json.load(sys.stdin)
available = {m.get("name", "") for m in payload.get("models", [])}
required = [m.strip() for m in os.environ.get("REQUIRED_OLLAMA_MODELS", "").split(",") if m.strip()]
missing = [m for m in required if m not in available]
if missing:
    print("missing: " + ", ".join(missing), file=sys.stderr)
    sys.exit(1)
' <<<"$payload"; then
    echo "FAIL"
    return 1
  fi
  echo "OK"
}

BACKEND_DIRECT_PORT="${BACKEND_PORT:-9000}"

wait_for_url "${BASE_URL}/en/login" "webapp via reverse-proxy"
if [ "$WITH_MAIL" = true ]; then
  wait_for_url "http://127.0.0.1:${MAILPIT_HTTP_PORT:-8025}/" "Mailpit UI"
fi
check_ollama_models
wait_for_url "http://127.0.0.1:${BACKEND_DIRECT_PORT}/actuator/health/liveness" "backend liveness (host :${BACKEND_DIRECT_PORT})"
wait_for_url "http://127.0.0.1:${BACKEND_DIRECT_PORT}/actuator/health/readiness" "backend readiness (host :${BACKEND_DIRECT_PORT})"
wait_for_url "${BASE_URL}/actuator/health" "backend actuator health via proxy"
check_classifier_health
wait_for_url "${BASE_URL}/actuator/prometheus" "backend actuator prometheus via proxy"
check_model_registry "$BASE_URL"

init_evidence_dir
CAPTURE_FLAGS=()
[ "$WITH_OBS" = true ] && CAPTURE_FLAGS+=(--obs)
[ "$WITH_MAIL" = true ] && CAPTURE_FLAGS+=(--mail)
[ "$WITH_OLLAMA" = true ] && CAPTURE_FLAGS+=(--ollama)
"$SCRIPT_DIR/capture-runtime-evidence.sh" "${CAPTURE_FLAGS[@]}" --out "$EVIDENCE_DIR" || true
echo "Smoke evidence: $EVIDENCE_DIR"

if [ "$WITH_OBS" = true ]; then
  if [ "$WITH_OBS_PRIVATE" = true ] &&
    [ -z "${DEMO_SMOKE_PROMETHEUS_URL:-}" ] &&
    [ -z "${DEMO_SMOKE_GRAFANA_URL:-}" ] &&
    [ -z "${DEMO_SMOKE_JAEGER_URL:-}" ]; then
    echo "Observability UI checks ... SKIP (--obs-private; provide DEMO_SMOKE_*_URL to check forwarded ports)"
  else
    wait_for_url "${PROMETHEUS_URL}/-/healthy" "Prometheus health"
    wait_for_url "${GRAFANA_URL}/api/health" "Grafana health"
    wait_for_url "${JAEGER_URL}/" "Jaeger UI"
  fi
fi

if [ "$DOWN_AFTER" = true ]; then
  echo "Stopping prod-local demo stack ..."
  "$SCRIPT_DIR/docker-compose.sh" down "${DOWN_FLAGS[@]}"
else
  echo "Smoke completed. Stack left running."
fi
