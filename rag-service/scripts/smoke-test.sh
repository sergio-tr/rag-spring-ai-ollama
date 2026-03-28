#!/usr/bin/env bash
# Smoke test: classifier-service /health, /classify and backend /api/v4/query.
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

echo -n "Backend /api/v4/query ... "
TMP=$(mktemp)
CODE=$(curl -sS -o "$TMP" -w "%{http_code}" "$BACKEND/api/v4/query?question=test") || { echo "FAIL (curl)"; rm -f "$TMP"; exit 1; }
echo "HTTP $CODE"
if [ "$CODE" != "200" ]; then echo "Response:"; cat "$TMP"; rm -f "$TMP"; echo "Expected 200 (is Ollama up and reachable?)"; exit 1; fi
grep -q '"success"[[:space:]]*:[[:space:]]*true' "$TMP" || { echo "Expected JSON envelope with success:true"; cat "$TMP"; rm -f "$TMP"; exit 1; }
rm -f "$TMP"
echo "Smoke test passed."
