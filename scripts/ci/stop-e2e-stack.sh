#!/usr/bin/env bash
# Stop the demo Lab stack started for P0 Playwright CI.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=e2e-stack-env.defaults.sh
source "${SCRIPT_DIR}/e2e-stack-env.defaults.sh"

COLLECT_LOGS="${E2E_CI_COLLECT_LOGS:-1}"
LOG_DIR="${E2E_CI_LOG_DIR:-/tmp/e2e-p0-stack-logs}"

log() { echo "[stop-e2e-stack] $*"; }

collect_docker_logs() {
  if [[ "${COLLECT_LOGS}" != "1" ]]; then
    return 0
  fi
  mkdir -p "${LOG_DIR}"
  log "collecting docker logs into ${LOG_DIR}"
  local services=(backend-dev webapp reverse-proxy classifier-service postgres)
  for svc in "${services[@]}"; do
    local cid
    cid="$(docker ps -aq --filter "name=${svc}" 2>/dev/null | head -n 1 || true)"
    if [[ -n "${cid}" ]]; then
      docker logs "${cid}" > "${LOG_DIR}/${svc}.log" 2>&1 || true
    fi
  done
  docker compose -f "${ROOT_DIR}/docker/docker-compose.yml" ps > "${LOG_DIR}/compose-ps.txt" 2>&1 || true
}

main() {
  cd "${ROOT_DIR}"
  collect_docker_logs
  log "stopping lab demo stack (base=${RAG_DEMO_BASE_URL})"
  bash ./docker/scripts/lab-demo-down.sh || true
  log "stop complete"
}

main "$@"
