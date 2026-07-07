#!/usr/bin/env bash
# Full verification: Maven verify + classifier pytest + webapp (npm lint/typecheck/Vitest/build/typedoc)
# + Docker stack (base+obs+logs+GPU) including the Next.js webapp image + pytest integration + Playwright API smoke.
# Run from repository root. Requires: Docker (Maven runs in the same image definition as the backend), Python 3.11+, Node.js 22 + npm (webapp; same major as CI).
#
# Step 1 builds the rag-service Dockerfile "build" stage (same JDK as docker-compose backend via RAG_JAVA_JDK_BASE_IMAGE),
# then runs ./mvnw verify with the working tree mounted - no extra ad-hoc JDK image, no JDK on the host unless MAVEN_ON_HOST=1.
#
# Usage:
#   ./tests/full-stack-verify.sh
#   SKIP_DOCKER=1 ./tests/full-stack-verify.sh              # skip Compose; integration/API need a reachable backend on :9000
#   SKIP_INTEGRATION=1 ./tests/full-stack-verify.sh         # no pytest integration
#   SKIP_WEBAPP=1 ./tests/full-stack-verify.sh              # skip webapp npm steps and Playwright API
#   SKIP_WEBAPP_PLAYWRIGHT=1 ./tests/full-stack-verify.sh   # skip only npm run test:api (after stack or reachable backend)
#   MAVEN_ON_HOST=1 ./tests/full-stack-verify.sh            # run mvnw on the host (needs JDK 21)
#
# Optional: RAG_BUILD_IMAGE_TAG (default rag-service-build:local), RAG_JAVA_JDK_BASE_IMAGE via rag-service/.env
# Playwright API: API_BASE_URL (default http://127.0.0.1:9000), same defaults as webapp/e2e/api and .github/workflows/system-checks.yml
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

RAG_BUILD_IMAGE_TAG="${RAG_BUILD_IMAGE_TAG:-rag-service-build:local}"

load_rag_jdk_base_from_env_file() {
  local f="$ROOT_DIR/rag-service/.env"
  [[ -f "$f" ]] || return 0
  local line
  line="$(grep -E '^[[:space:]]*RAG_JAVA_JDK_BASE_IMAGE=' "$f" | head -1 || true)"
  [[ -n "$line" ]] || return 0
  export RAG_JAVA_JDK_BASE_IMAGE="${line#*=}"
  RAG_JAVA_JDK_BASE_IMAGE="${RAG_JAVA_JDK_BASE_IMAGE%\"}"
  RAG_JAVA_JDK_BASE_IMAGE="${RAG_JAVA_JDK_BASE_IMAGE#\"}"
  RAG_JAVA_JDK_BASE_IMAGE="${RAG_JAVA_JDK_BASE_IMAGE%%[[:space:]]}"
}

# Only when MAVEN_ON_HOST=1 or Docker is unavailable.
ensure_java_home() {
  local jh
  if [[ -n "${JAVA_HOME:-}" ]]; then
    jh="${JAVA_HOME%/}"
    if [[ -x "${jh}/bin/java" ]] || [[ -f "${jh}/bin/java.exe" ]]; then
      export JAVA_HOME="$jh"
      return 0
    fi
  fi
  if ! command -v java >/dev/null 2>&1; then
    echo "Error: 'java' is not in PATH. Install JDK 21 and add it to PATH, or set JAVA_HOME." >&2
    return 1
  fi
  jh="$(java -XshowSettings:properties -version 2>&1 | tr -d '\r' | sed -n 's/.*java\.home = //p' | head -1 | sed 's/[[:space:]]*$//')"
  if [[ -z "$jh" ]]; then
    echo "Error: could not detect java.home. Set JAVA_HOME to your JDK 21 installation." >&2
    return 1
  fi
  if command -v cygpath >/dev/null 2>&1 && [[ "$jh" =~ ^[A-Za-z]: ]]; then
    jh="$(cygpath -u "$jh")"
  fi
  export JAVA_HOME="$jh"
  echo "JAVA_HOME (autodetected): $JAVA_HOME"
}

run_maven_verify() {
  cd "$ROOT_DIR/rag-service"
  if [[ "${MAVEN_ON_HOST:-0}" == "1" ]]; then
    echo "Running Maven on the host (MAVEN_ON_HOST=1)..."
    ensure_java_home
    ./mvnw -B verify
    return
  fi
  if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
    echo "Docker is not available; falling back to host Maven (install JDK 21 or start Docker)." >&2
    ensure_java_home
    ./mvnw -B verify
    return
  fi

  load_rag_jdk_base_from_env_file
  local jdk="${RAG_JAVA_JDK_BASE_IMAGE:-eclipse-temurin:21-jdk}"
  echo "Building rag-service Dockerfile target 'build' (JDK_BASE_IMAGE=$jdk) -> $RAG_BUILD_IMAGE_TAG"
  docker build \
    --target build \
    -t "$RAG_BUILD_IMAGE_TAG" \
    --build-arg "JDK_BASE_IMAGE=$jdk" \
    -f "$ROOT_DIR/rag-service/Dockerfile" \
    "$ROOT_DIR/rag-service"

  echo "Running ./mvnw verify inside that image (workspace mounted from host, rag-m2-cache for ~/.m2)..."
  docker run --rm \
    -v "$ROOT_DIR/rag-service:/app" \
    -v rag-m2-cache:/root/.m2 \
    -w /app \
    "$RAG_BUILD_IMAGE_TAG" \
    bash -lc 'set -e; sed -i "s/\r$//" mvnw 2>/dev/null || true; chmod +x mvnw; ./mvnw -B verify'
}

ensure_env() {
  local ex="$1" out="$2"
  if [[ ! -f "$out" ]] && [[ -f "$ex" ]]; then
    cp "$ex" "$out"
    echo "Created $out from example."
  fi
}

ensure_node_toolchain() {
  if ! command -v node >/dev/null 2>&1 || ! command -v npm >/dev/null 2>&1; then
    echo "Error: 'node' and 'npm' are required for the webapp phase. Install Node.js 22 (see .github/workflows/ci.yml) or set SKIP_WEBAPP=1." >&2
    return 1
  fi
  local major
  major="$(node -p "process.versions.node.split('.')[0]" 2>/dev/null || echo "?")"
  if [[ "$major" != "22" ]]; then
    echo "Warning: Node major version is $major (CI uses 22). Continue if your toolchain is compatible." >&2
  fi
}

run_webapp_static_checks() {
  echo "=== 3) Webapp: npm install, lint, typecheck, Vitest coverage, build, typedoc ==="
  ensure_node_toolchain
  ensure_env "$ROOT_DIR/webapp/.env.example" "$ROOT_DIR/webapp/.env"
  cd "$ROOT_DIR/webapp"
  npm install --no-audit --no-fund
  npm run lint
  npm run typecheck
  npm run test:coverage
  npm run build
  npm run doc
}

backend_actuator_ok() {
  curl -sf "${API_BASE_URL:-http://127.0.0.1:9000}/actuator/health" >/dev/null 2>&1
}

run_webapp_playwright_api() {
  if [[ "${SKIP_WEBAPP:-0}" == "1" ]] || [[ "${SKIP_WEBAPP_PLAYWRIGHT:-0}" == "1" ]]; then
    return 0
  fi
  echo ""
  echo "=== 7) Webapp: Playwright API tests (npm run test:api) ==="
  if [[ "${SKIP_DOCKER:-0}" == "1" ]] && ! backend_actuator_ok; then
    echo "Skipping Playwright API: SKIP_DOCKER=1 and no backend at ${API_BASE_URL:-http://127.0.0.1:9000} (start the stack or unset SKIP_DOCKER)."
    return 0
  fi
  if ! backend_actuator_ok; then
    echo "Error: backend not reachable at ${API_BASE_URL:-http://127.0.0.1:9000}/actuator/health (needed for test:api)." >&2
    return 1
  fi
  ensure_node_toolchain
  cd "$ROOT_DIR/webapp"
  # API project uses @playwright/test request fixture; browser install is lightweight and idempotent.
  npx playwright install chromium
  export PLAYWRIGHT_SKIP_WEBSERVER=1
  export API_BASE_URL="${API_BASE_URL:-http://127.0.0.1:9000}"
  export INTEGRATION_LOGIN_EMAIL="${INTEGRATION_LOGIN_EMAIL:-dev@local.test}"
  export INTEGRATION_LOGIN_PASSWORD="${INTEGRATION_LOGIN_PASSWORD:-dev}"
  export CLASSIFIER_URL="${CLASSIFIER_URL:-}"
  npm run test:api
}

echo "=== 1) RAG service: mvnw verify (JaCoCo >= 80%) ==="
run_maven_verify

echo ""
echo "=== 2) Classifier: pytest + coverage (>= 80%) ==="
cd "$ROOT_DIR/classifier-service"
python3 -m pip install -q -r requirements.txt
python3 -m pytest tests/ -v --tb=short

if [[ "${SKIP_WEBAPP:-0}" != "1" ]]; then
  echo ""
  run_webapp_static_checks
fi

if [[ "${SKIP_DOCKER:-0}" != "1" ]]; then
  echo ""
  echo "=== 4) Docker .env files ==="
  ensure_env "$ROOT_DIR/db/.env.example" "$ROOT_DIR/db/.env"
  ensure_env "$ROOT_DIR/rag-service/.env.example" "$ROOT_DIR/rag-service/.env"
  ensure_env "$ROOT_DIR/classifier-service/.env.example" "$ROOT_DIR/classifier-service/.env"
  ensure_env "$ROOT_DIR/observability/.env.example" "$ROOT_DIR/observability/.env"
  ensure_env "$ROOT_DIR/webapp/.env.example" "$ROOT_DIR/webapp/.env"

  echo ""
  echo "=== 5) Docker build & up (docker-compose.yml + obs + profiles observability/logs + GPU classifier) ==="
  cd "$ROOT_DIR/docker"
  docker compose -f docker-compose.yml -f compose.obs.yml -f compose.gpu.yml \
    --profile observability --profile logs \
    --env-file ../db/.env --env-file ../classifier-service/.env \
    --env-file ../rag-service/.env --env-file ../webapp/.env --env-file ../observability/.env \
    build

  docker compose -f docker-compose.yml -f compose.obs.yml -f compose.gpu.yml \
    --profile observability --profile logs \
    --env-file ../db/.env --env-file ../classifier-service/.env \
    --env-file ../rag-service/.env --env-file ../webapp/.env --env-file ../observability/.env \
    up -d

  BACKEND_WAIT_BASE="${API_BASE_URL:-http://127.0.0.1:9000}"
  BACKEND_WAIT_BASE="${BACKEND_WAIT_BASE%/}"
  echo "Waiting for backend /actuator/health at ${BACKEND_WAIT_BASE}..."
  for i in $(seq 1 40); do
    if curl -sf "${BACKEND_WAIT_BASE}/actuator/health" >/dev/null; then
      echo "Backend is up."
      break
    fi
    sleep 3
  done
fi

if [[ "${SKIP_INTEGRATION:-0}" != "1" ]]; then
  echo ""
  echo "=== 6) Integration tests (INTEGRATION_CHECK_OBS=1) ==="
  cd "$ROOT_DIR"
  export INTEGRATION_CHECK_OBS=1
  python3 -m pip install -q -r tests/integration/requirements.txt
  python3 -m pytest tests/integration -v --tb=short
fi

run_webapp_playwright_api

echo ""
echo "=== All verification steps completed. ==="
