#!/usr/bin/env bash
# Full verification: Maven verify + classifier pytest + Docker (base+obs+logs) + integration tests.
# Run from repository root. Requires: Docker (Maven runs in the same image definition as the backend), Python 3.11+.
#
# Step 1 builds the rag-service Dockerfile "build" stage (same JDK as docker-compose backend via RAG_JAVA_JDK_BASE_IMAGE),
# then runs ./mvnw verify with the working tree mounted — no extra ad-hoc JDK image, no JDK on the host unless MAVEN_ON_HOST=1.
#
# Usage:
#   ./scripts/full-stack-verify.sh
#   SKIP_DOCKER=1 ./scripts/full-stack-verify.sh           # tests only (stack must be running)
#   SKIP_INTEGRATION=1 ./scripts/full-stack-verify.sh      # no pytest integration
#   MAVEN_ON_HOST=1 ./scripts/full-stack-verify.sh         # run mvnw on the host (needs JDK 21)
#
# Optional: RAG_BUILD_IMAGE_TAG (default rag-service-build:local), RAG_JAVA_JDK_BASE_IMAGE via rag-service/.env
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

echo "=== 1) RAG service: mvnw verify (JaCoCo >= 80%) ==="
run_maven_verify

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
  echo "=== 4) Docker build & up (compose + obs + logs + Ollama GPU) ==="
  cd "$ROOT_DIR/docker"
  docker compose -f docker-compose.yml -f compose.obs.yml -f compose.logs.yml -f compose.ollama-gpu.yml \
    --env-file ../db/.env --env-file ../classifier-service/.env \
    --env-file ../rag-service/.env --env-file ../observability/.env \
    build

  docker compose -f docker-compose.yml -f compose.obs.yml -f compose.logs.yml -f compose.ollama-gpu.yml \
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
