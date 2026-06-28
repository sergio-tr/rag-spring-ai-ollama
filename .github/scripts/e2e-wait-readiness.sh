#!/usr/bin/env bash
# Wait for Spring readiness with fail-fast on obvious Flyway/startup failures.
set -euo pipefail

LOG_PATH="${1:-/tmp/spring-e2e.log}"
URL="${E2E_READINESS_URL:-http://127.0.0.1:9000/actuator/health/readiness}"
MAX_ATTEMPTS="${E2E_READINESS_MAX_ATTEMPTS:-45}"
SLEEP_SEC="${E2E_READINESS_SLEEP_SEC:-2}"

for i in $(seq 1 "${MAX_ATTEMPTS}"); do
  code="$(curl -s -o /dev/null -w '%{http_code}' "${URL}" 2>/dev/null || true)"
  if [[ "${code}" == "200" ]]; then
    echo "Backend ready (${URL}) after ${i} attempt(s)"
    exit 0
  fi
  if [[ -f "${LOG_PATH}" ]] && grep -qE 'FlywayValidateException|Migration checksum mismatch|Application run failed' "${LOG_PATH}" 2>/dev/null; then
    echo "::error::Spring failed during startup (see log tail). Not waiting full ${MAX_ATTEMPTS} attempts."
    tail -n 120 "${LOG_PATH}" || true
    exit 1
  fi
  sleep "${SLEEP_SEC}"
done

echo "::error::Backend readiness never returned 200 (${URL})"
curl -sS "http://127.0.0.1:9000/actuator/health" 2>/dev/null || true
echo ""
curl -sS "${URL}" 2>/dev/null || true
echo ""
tail -n 120 "${LOG_PATH}" 2>/dev/null || true
exit 1
