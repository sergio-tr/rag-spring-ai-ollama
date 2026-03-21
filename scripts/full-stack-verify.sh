#!/usr/bin/env bash
# Full verification: Maven verify + classifier pytest + Docker (base+obs+logs) + integration tests.
# Run from repository root. Requires: Docker, JDK 21, Python 3.11+.
#
# Usage:
#   ./scripts/full-stack-verify.sh
#   SKIP_DOCKER=1 ./scripts/full-stack-verify.sh           # tests only (stack must be running)
#   SKIP_INTEGRATION=1 ./scripts/full-stack-verify.sh      # no pytest integration
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

ensure_env() {
  local ex="$1" out="$2"
  if [[ ! -f "$out" ]] && [[ -f "$ex" ]]; then
    cp "$ex" "$out"
    echo "Created $out from example."
  fi
}

echo "=== 1) RAG service: mvnw verify (JaCoCo >= 80%) ==="
cd "$ROOT_DIR/rag-service"
./mvnw -B verify

echo ""
echo "=== 2) Classifier: pytest + coverage (>= 80%) ==="
cd "$ROOT_DIR/classifier-service"
python3 -m pip install -q -r requirements.txt
python3 -m pytest tests/ -v --tb=short

if [[ "${SKIP_DOCKER:-0}" != "1" ]]; then
  echo ""
  echo "=== 3) Docker .env files ==="
  ensure_env "$ROOT_DIR/db/.env.example" "$ROOT_DIR/db/.env"
  ensure_env "$ROOT_DIR/rag-service/.env.example" "$ROOT_DIR/rag-service/.env"
  ensure_env "$ROOT_DIR/classifier-service/.env.example" "$ROOT_DIR/classifier-service/.env"
  ensure_env "$ROOT_DIR/observability/.env.example" "$ROOT_DIR/observability/.env"

  echo ""
  echo "=== 4) Docker build & up (compose + obs + logs) ==="
  cd "$ROOT_DIR/docker"
  docker compose -f docker-compose.yml -f compose.obs.yml -f compose.logs.yml \
    --env-file ../db/.env --env-file ../classifier-service/.env \
    --env-file ../rag-service/.env --env-file ../observability/.env \
    build

  docker compose -f docker-compose.yml -f compose.obs.yml -f compose.logs.yml \
    --env-file ../db/.env --env-file ../classifier-service/.env \
    --env-file ../rag-service/.env --env-file ../observability/.env \
    up -d

  echo "Waiting for backend /actuator/health..."
  for i in $(seq 1 40); do
    if curl -sf "http://127.0.0.1:9000/actuator/health" >/dev/null; then
      echo "Backend is up."
      break
    fi
    sleep 3
  done
fi

if [[ "${SKIP_INTEGRATION:-0}" != "1" ]]; then
  echo ""
  echo "=== 5) Integration tests (INTEGRATION_CHECK_OBS=1) ==="
  cd "$ROOT_DIR"
  export INTEGRATION_CHECK_OBS=1
  python3 -m pip install -q -r tests/integration/requirements.txt
  python3 -m pytest tests/integration -v --tb=short
fi

echo ""
echo "=== All verification steps completed. ==="
