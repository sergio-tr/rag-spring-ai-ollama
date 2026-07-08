#!/usr/bin/env bash
# Reproducible dev stack bootstrap before smoke / E2E:
#   1. clean + compile backend on host (bind-mounted target/)
#   2. start or restart backend-dev
#   3. wait backend liveness + frontend
#   4. verify seed login API
# Aborts with a clear message on NoClassDefFoundError (corrupt target/classes).
#
# Usage (repo root):
#   ./docker/scripts/dev-smoke-bootstrap.sh
#   ./docker/scripts/dev-smoke-bootstrap.sh --skip-up          # stack already up
#   ./docker/scripts/dev-smoke-bootstrap.sh --preset full    # S4 full dev flags
#   ./docker/scripts/dev-smoke-bootstrap.sh --no-restart     # compile only
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"
RAG_DIR="$ROOT_DIR/rag-service"

TIMEOUT_SECONDS="${DEV_SMOKE_TIMEOUT_SECONDS:-180}"
SKIP_UP=false
SKIP_COMPILE=false
NO_CLEAN=false
NO_RESTART=false
PRESET=lab
EXTRA_UP_ARGS=()

usage() {
  local code="${1:-2}"
  cat >&2 <<'EOF'
Usage: ./docker/scripts/dev-smoke-bootstrap.sh [options]

Options:
  --skip-up       Do not call up.sh; assume the dev stack is already running.
  --skip-compile  Skip Maven compile (only safe after a recent clean compile).
  --no-clean      Run mvn compile without clean (faster; riskier after refactors).
  --no-restart    Compile only; do not restart backend-dev.
  --preset lab    up.sh dev --rag --proxy --classifier --no-env-prompt (default)
  --preset full   up.sh dev --rag --proxy --obs --classifier --logs --infra --gpu --ollama-remote --no-env-prompt
  --timeout N     Per-wait timeout in seconds (default: 180)
  -h, --help      Show this help

Environment (optional):
  DEV_SMOKE_BASE_URL          Frontend origin (default: https://127.0.0.1:8444)
  DEV_SMOKE_HEALTH_BASE       Actuator origin (default: same as BASE_URL; with --proxy use reverse-proxy, not :9000)
  DEV_SMOKE_BACKEND_PORT      Direct backend host port when published without --proxy (default: 9000)
  DEV_SMOKE_LOGIN_EMAIL       Seed user (default: dev@local.test)
  DEV_SMOKE_LOGIN_PASSWORD    Seed password (default: dev)
  DEV_SMOKE_API_PREFIX        API prefix (default: /api/v5)
EOF
  exit "$code"
}

while [ $# -gt 0 ]; do
  case "$1" in
    --skip-up) SKIP_UP=true; shift ;;
    --skip-compile) SKIP_COMPILE=true; shift ;;
    --no-clean) NO_CLEAN=true; shift ;;
    --no-restart) NO_RESTART=true; shift ;;
    --preset)
      shift
      [ $# -lt 1 ] && usage
      PRESET="$1"
      shift
      ;;
    --timeout)
      shift
      [ $# -lt 1 ] && usage
      TIMEOUT_SECONDS="$1"
      shift
      ;;
    -h|--help) usage 0 ;;
    *)
      EXTRA_UP_ARGS+=("$1")
      shift
      ;;
  esac
done

BASE_URL="${DEV_SMOKE_BASE_URL:-https://127.0.0.1:8444}"
HEALTH_BASE="${DEV_SMOKE_HEALTH_BASE:-$BASE_URL}"
BACKEND_PORT="${DEV_SMOKE_BACKEND_PORT:-9000}"
LOGIN_EMAIL="${DEV_SMOKE_LOGIN_EMAIL:-dev@local.test}"
LOGIN_PASSWORD="${DEV_SMOKE_LOGIN_PASSWORD:-dev}"
API_PREFIX="${DEV_SMOKE_API_PREFIX:-/api/v5}"

backend_dev_container_id() {
  docker ps -q --filter "name=backend-dev" 2>/dev/null | head -n 1
}

backend_dev_any_id() {
  docker ps -aq --filter "name=backend-dev" 2>/dev/null | head -n 1
}

fail_with_backend_logs() {
  local reason="$1"
  local cid
  cid="$(backend_dev_any_id || true)"
  echo "ERROR: $reason" >&2
  if [ -n "$cid" ]; then
    echo "--- backend-dev logs (last 60 lines) ---" >&2
    docker logs --tail 60 "$cid" 2>&1 >&2 || true
  fi
  exit 1
}

check_no_class_def_errors() {
  local cid logs
  cid="$(backend_dev_any_id || true)"
  [ -z "$cid" ] && return 0
  logs="$(docker logs --tail 300 "$cid" 2>&1 || true)"
  if echo "$logs" | grep -qE 'NoClassDefFoundError|ClassNotFoundException'; then
    echo "ERROR: backend-dev shows NoClassDefFoundError / ClassNotFoundException." >&2
    echo "Cause: partial or corrupt target/classes on the bind-mounted rag-service/ volume." >&2
    echo "Fix: cd rag-service && ./mvnw clean compile && ./docker/scripts/dev-smoke-bootstrap.sh --skip-up" >&2
    echo "--- matching log lines ---" >&2
    echo "$logs" | grep -E 'NoClassDefFoundError|ClassNotFoundException|APPLICATION FAILED|Error starting ApplicationContext' | tail -20 >&2 || true
    exit 1
  fi
}

check_crash_loop() {
  local cid status restarts
  cid="$(backend_dev_any_id || true)"
  [ -z "$cid" ] && return 0
  status="$(docker inspect --format '{{.State.Status}}' "$cid" 2>/dev/null || echo unknown)"
  restarts="$(docker inspect --format '{{.RestartCount}}' "$cid" 2>/dev/null || echo 0)"
  if [ "$status" = "restarting" ] || [ "${restarts:-0}" -ge 3 ]; then
    check_no_class_def_errors
    fail_with_backend_logs "backend-dev appears unstable (status=$status, restarts=$restarts). Run clean compile and restart."
  fi
}

verify_application_class() {
  if [ ! -f "$RAG_DIR/target/classes/com/uniovi/Application.class" ]; then
    echo "ERROR: $RAG_DIR/target/classes/com/uniovi/Application.class is missing after compile." >&2
    echo "Run: cd rag-service && ./mvnw clean compile" >&2
    exit 1
  fi
}

step_compile() {
  if [ "$SKIP_COMPILE" = true ]; then
    echo "==> Skipping compile (--skip-compile)"
    verify_application_class
    return 0
  fi
  echo "==> Backend compile on host (rag-service/ → container /app)"
  cd "$RAG_DIR"
  if [ "$NO_CLEAN" = true ]; then
    ./mvnw compile -Dmaven.test.skip=true
  else
    ./mvnw clean compile -Dmaven.test.skip=true
  fi
  verify_application_class
  echo "==> Compile OK ($(wc -c < target/classes/com/uniovi/Application.class | tr -d ' ') bytes Application.class)"
}

up_args_for_preset() {
  case "$PRESET" in
    lab)
      echo dev --rag --proxy --classifier --no-env-prompt
      ;;
    full)
      echo dev --rag --proxy --obs --classifier --logs --infra --gpu --ollama-remote --no-env-prompt
      ;;
    *)
      echo "Unknown preset: $PRESET (use lab or full)" >&2
      exit 2
      ;;
  esac
}

step_up() {
  if [ "$SKIP_UP" = true ]; then
    echo "==> Skipping up.sh (--skip-up)"
    return 0
  fi
  local preset_args shell_args=()
  preset_args="$(up_args_for_preset)"
  # shellcheck disable=SC2206
  shell_args=($preset_args "${EXTRA_UP_ARGS[@]}")
  echo "==> Starting dev stack: ./docker/scripts/up.sh ${shell_args[*]}"
  "$SCRIPT_DIR/up.sh" "${shell_args[@]}"
}

step_restart_backend() {
  if [ "$NO_RESTART" = true ]; then
    echo "==> Skipping backend restart (--no-restart)"
    return 0
  fi
  local cid
  cid="$(backend_dev_container_id || true)"
  if [ -z "$cid" ]; then
    echo "ERROR: backend-dev container is not running. Start the stack first or omit --skip-up." >&2
    exit 1
  fi
  echo "==> Restarting backend-dev ($cid)"
  docker restart "$cid" >/dev/null
}

wait_for_url() {
  local url="$1"
  local label="$2"
  local deadline=$((SECONDS + TIMEOUT_SECONDS))
  echo -n "==> $label ... "
  while [ "$SECONDS" -lt "$deadline" ]; do
    check_crash_loop
    check_no_class_def_errors
    if curl -ksSfL --max-time 8 "$url" >/dev/null 2>&1; then
      echo "OK"
      return 0
    fi
    sleep 2
  done
  echo "FAIL"
  check_no_class_def_errors
  fail_with_backend_logs "$label timed out after ${TIMEOUT_SECONDS}s (url=$url)"
}

verify_login_api() {
  local login_url="${BASE_URL%/}${API_PREFIX}/auth/login"
  echo -n "==> seed login API ... "
  local http_code body
  body="$(mktemp)"
  http_code="$(
    curl -ksS -o "$body" -w '%{http_code}' --max-time 15 \
      -H "Content-Type: application/json" \
      -d "{\"email\":\"${LOGIN_EMAIL}\",\"password\":\"${LOGIN_PASSWORD}\"}" \
      "$login_url" || echo "000"
  )"
  if [ "$http_code" != "200" ]; then
    echo "FAIL (HTTP $http_code)"
    echo "Response: $(head -c 300 "$body")" >&2
    rm -f "$body"
    fail_with_backend_logs "seed login failed (url=$login_url)"
  fi
  if ! grep -q 'accessToken' "$body"; then
    echo "FAIL (no accessToken)"
    rm -f "$body"
    fail_with_backend_logs "seed login response missing accessToken"
  fi
  rm -f "$body"
  echo "OK"
}

main() {
  echo "dev-smoke-bootstrap: preset=$PRESET base=$BASE_URL health=$HEALTH_BASE"
  step_compile
  step_up
  step_restart_backend
  wait_for_url "${HEALTH_BASE%/}/actuator/health/liveness" "backend liveness (${HEALTH_BASE})"
  wait_for_url "${BASE_URL%/}/en/login" "frontend login page (${BASE_URL})"
  verify_login_api
  check_no_class_def_errors
  echo "dev-smoke-bootstrap: PASS - stack ready for smoke / Playwright"
  echo "Next: cd webapp && PLAYWRIGHT_BASE_URL=${BASE_URL} npm run test:e2e"
}

main "$@"
