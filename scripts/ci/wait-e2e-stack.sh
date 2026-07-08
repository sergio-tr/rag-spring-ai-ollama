#!/usr/bin/env bash
# Wait until the P0 stack passes e2e-stack-preflight (fail-fast with E2E_STACK_NOT_READY).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=e2e-stack-env.defaults.sh
source "${SCRIPT_DIR}/e2e-stack-env.defaults.sh"

MAX_ATTEMPTS="${E2E_STACK_WAIT_MAX_ATTEMPTS:-30}"
SLEEP_SEC="${E2E_STACK_WAIT_SLEEP_SEC:-10}"

log() { echo "[wait-e2e-stack] $*"; }

run_preflight() {
  cd "${ROOT_DIR}/webapp"
  node scripts/e2e-stack-preflight.mjs
}

main() {
  log "waiting for stack at ${PLAYWRIGHT_BASE_URL} (max ${MAX_ATTEMPTS} attempts, ${SLEEP_SEC}s apart)"
  local attempt=1
  while [[ "${attempt}" -le "${MAX_ATTEMPTS}" ]]; do
    log "preflight attempt ${attempt}/${MAX_ATTEMPTS}"
    if run_preflight; then
      log "stack ready"
      return 0
    fi
    local exit_code=$?
    if [[ "${exit_code}" -eq 2 ]]; then
      log "preflight reported E2E_STACK_NOT_READY (exit ${exit_code})"
    fi
    if [[ "${attempt}" -ge "${MAX_ATTEMPTS}" ]]; then
      echo "E2E_STACK_NOT_READY: stack did not become ready after ${MAX_ATTEMPTS} attempts" >&2
      return 2
    fi
    sleep "${SLEEP_SEC}"
    attempt=$((attempt + 1))
  done
}

main "$@"
