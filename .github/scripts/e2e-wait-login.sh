#!/usr/bin/env bash
# Wait until seed login returns HTTP 200 (fast-fail gate before Playwright).
set -euo pipefail

BACKEND="${E2E_LOGIN_BACKEND_URL:-http://127.0.0.1:9000}"
EMAIL="${E2E_LOGIN_EMAIL:-dev@local.test}"
PASSWORD="${E2E_LOGIN_PASSWORD:-dev}"
MAX_ATTEMPTS="${E2E_LOGIN_MAX_ATTEMPTS:-30}"
PRODUCT_PREFIX="${E2E_PRODUCT_PREFIX:-/api/v5}"

payload="$(printf '{"email":"%s","password":"%s"}' "${EMAIL}" "${PASSWORD}")"

for i in $(seq 1 "${MAX_ATTEMPTS}"); do
  code="$(
    curl -sS -o /dev/null -w '%{http_code}' \
      -H 'Content-Type: application/json' \
      -d "${payload}" \
      "${BACKEND}${PRODUCT_PREFIX}/auth/login" 2>/dev/null || true
  )"
  if [[ "${code}" == "200" ]]; then
    echo "Seed login OK (${EMAIL}) after ${i} attempt(s)"
    exit 0
  fi
  sleep 2
done

echo "::error::Seed login not ready for ${EMAIL}"
exit 1
