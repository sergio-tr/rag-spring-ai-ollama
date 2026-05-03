#!/usr/bin/env bash
# Smoke test: classifier-service /health, /classify and backend actuator (product API requires JWT).
# Usage: ./smoke-test.sh [BACKEND_URL [CLASSIFIER_URL]]
# Defaults: BACKEND_URL=http://localhost:9000, CLASSIFIER_URL=http://localhost:8000
set -e
BACKEND="${1:-http://localhost:9000}"
CLASSIFIER="${2:-http://localhost:8000}"
echo "Backend: $BACKEND  Classifier: $CLASSIFIER"

echo -n "Classifier /health ... "
curl -sf "$CLASSIFIER/health" > /dev/null && echo "OK" || { echo "FAIL"; exit 1; }

echo -n "Classifier /classify ... "
curl -sf -X POST "$CLASSIFIER/classify" -H "Content-Type: application/json" -d '{"query":"¿Cuántas actas?"}' | grep -q queryType && echo "OK" || { echo "FAIL"; exit 1; }

echo -n "Backend /actuator/health ... "
TMP=$(mktemp)
CODE=$(curl -sS -o "$TMP" -w "%{http_code}" "$BACKEND/actuator/health") || { echo "FAIL (curl)"; rm -f "$TMP"; exit 1; }
echo "HTTP $CODE"
if [ "$CODE" != "200" ]; then echo "Response:"; cat "$TMP"; rm -f "$TMP"; exit 1; fi
grep -q '"status"[[:space:]]*:[[:space:]]*"UP"' "$TMP" 2>/dev/null || grep -q '"status":"UP"' "$TMP" || { echo "Expected UP status"; cat "$TMP"; rm -f "$TMP"; exit 1; }
rm -f "$TMP"
echo "Smoke test passed."
