#!/usr/bin/env bash
# Optional: obtain JWT access tokens for Gatling CSV feeders (do not commit output with real secrets).
# Usage:
#   export GATLING_BASE_URL=http://localhost:9000
#   export GATLING_LOGIN_EMAIL=dev@local.test
#   export GATLING_LOGIN_PASSWORD=dev
#   ./tests/gatling/gatling-prepare-tokens.sh 5 > /tmp/tokens.csv
#
# Then add "accessToken" as a column header and paste tokens, or use jq to shape:
#   echo "accessToken"; curl -fsS -X POST "$GATLING_BASE_URL${GATLING_PRODUCT_PREFIX:-/api/v5}/auth/login" ...

set -euo pipefail

BASE="${GATLING_BASE_URL:-http://localhost:9000}"
BASE="${BASE%/}"
PRODUCT_PREFIX="${GATLING_PRODUCT_PREFIX:-/api/v5}"
PRODUCT_PREFIX="${PRODUCT_PREFIX%/}"
EMAIL="${GATLING_LOGIN_EMAIL:-dev@local.test}"
PASS="${GATLING_LOGIN_PASSWORD:-dev}"
COUNT="${1:-3}"

echo "accessToken"
for _ in $(seq 1 "${COUNT}"); do
  tok="$(curl -fsS -X POST "${BASE}${PRODUCT_PREFIX}/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"${EMAIL}\",\"password\":\"${PASS}\"}" \
    | jq -r '.accessToken')"
  echo "${tok}"
done
