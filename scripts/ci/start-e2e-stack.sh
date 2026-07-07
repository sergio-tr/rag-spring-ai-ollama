#!/usr/bin/env bash
# Start the demo Lab stack for P0 Playwright (backend-dev + webapp + classifier + nginx).
# Run from repository root. Idempotent when the stack is already healthy on the configured ports.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "${SCRIPT_DIR}/../.." && pwd)"
# shellcheck source=e2e-stack-env.defaults.sh
source "${SCRIPT_DIR}/e2e-stack-env.defaults.sh"

SKIP_COMPILE="${E2E_CI_SKIP_COMPILE:-0}"
SKIP_UP="${E2E_CI_SKIP_UP:-0}"

log() { echo "[start-e2e-stack] $*"; }

stack_reachable() {
  curl -ksf --max-time 8 "${PLAYWRIGHT_BASE_URL%/}/actuator/health/liveness" >/dev/null 2>&1
}

seed_e2e_admin_user() {
  local compose_project="${E2E_COMPOSE_PROJECT:-docker}"
  local pg_container
  pg_container="$(
    docker ps --format '{{.Names}}' \
      | grep -E "${compose_project}.*postgres" \
      | head -n 1 || true
  )"
  if [[ -z "${pg_container}" ]]; then
    log "postgres container not found; skipping admin SQL seed"
    return 0
  fi
  log "ensuring admin user ${E2E_ADMIN_EMAIL} in ${pg_container}"
  docker exec -i "${pg_container}" psql -U postgres -d vectordb -v ON_ERROR_STOP=1 <<SQL
INSERT INTO users (id, email, password_hash, name, role, created_at, email_verified, email_verified_at)
VALUES
  ('e2e0ad00-0000-4000-8000-000000000001', '${E2E_ADMIN_EMAIL}', '{noop}${E2E_ADMIN_PASSWORD}', 'E2E Admin', 'ADMIN', CURRENT_TIMESTAMP, true, CURRENT_TIMESTAMP)
ON CONFLICT (email) DO UPDATE SET
  password_hash = EXCLUDED.password_hash,
  name = EXCLUDED.name,
  role = EXCLUDED.role,
  email_verified = true,
  email_verified_at = COALESCE(users.email_verified_at, CURRENT_TIMESTAMP);
SQL
}

main() {
  cd "${ROOT_DIR}"
  log "mode=${E2E_STACK_MODE} base=${PLAYWRIGHT_BASE_URL} https_port=${REVERSE_PROXY_DEV_HTTPS_PORT}"

  if [[ "${SKIP_UP}" == "1" ]] && stack_reachable; then
    log "stack already reachable (--skip-up); seeding admin and exiting"
    seed_e2e_admin_user
    return 0
  fi

  if [[ ! -f db/.env ]] || [[ ! -f rag-service/.env ]]; then
    log "creating .env files from examples"
    bash ./docker/scripts/create-env-all.sh --force
  fi

  local bootstrap_args=(--preset lab)
  if [[ "${SKIP_COMPILE}" == "1" ]]; then
    bootstrap_args+=(--skip-compile)
  fi
  if [[ "${SKIP_UP}" == "1" ]]; then
    bootstrap_args+=(--skip-up)
  fi

  log "bootstrapping via dev-smoke-bootstrap (${bootstrap_args[*]})"
  bash ./docker/scripts/dev-smoke-bootstrap.sh "${bootstrap_args[@]}"

  seed_e2e_admin_user
  log "stack bootstrap finished; run wait-e2e-stack.sh before Playwright"
}

main "$@"
