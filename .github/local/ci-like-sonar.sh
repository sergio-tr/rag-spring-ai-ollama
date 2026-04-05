#!/usr/bin/env bash
# Replicate .github/workflows/sonar.yml locally: build, coverage (Java / Python / Vitest), SonarCloud scan.
#
# Prerequisites:
#   - JDK 21, Maven wrapper (rag-service/mvnw)
#   - Python 3.11 + pip (classifier-service)
#   - Node.js (see webapp/package.json engines)
#   - PostgreSQL 16 with pgvector reachable at SPRING_DATASOURCE_URL (default: localhost:5432/vectordb)
#   - psql + pg_isready in PATH for DB bootstrap, OR Docker (pgvector image as client via --network host)
#   - Docker (for sonarsource/sonar-scanner-cli)
#   - SONAR_TOKEN from SonarCloud → My Account → Security
#   - Full git history (not shallow): git fetch --unshallow
#
# Usage (from repo root):
#   export SONAR_TOKEN=your_token
#   ./scripts/sonar-local.sh
#
# Skip DB bootstrap if you already applied ci-postgres-extensions.sql + testdb (e.g. ci-like-verify):
#   SKIP_POSTGRES_PREP=1 ./scripts/sonar-local.sh
#
# Force native client only (fail if psql missing, even when Docker is installed):
#   USE_DOCKER_PG_CLIENT=0 ./scripts/sonar-local.sh
#
# Disable auto-pick of /usr/lib/jvm/java-21-* when default java is older:
#   SKIP_AUTO_JDK21=1 ./scripts/sonar-local.sh
#
# Optional — publish analysis for the current branch (matches local branch name in SonarCloud):
#   SONAR_BRANCH_NAME=$(git branch --show-current) ./scripts/sonar-local.sh

set -euo pipefail

# Script lives in .github/local/ — repo root is two levels up.
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT"

if [[ -z "${SONAR_TOKEN:-}" ]]; then
  echo "ERROR: Set SONAR_TOKEN (SonarCloud → Account → Security → Generate token)." >&2
  exit 1
fi

if git rev-parse --is-shallow-repository 2>/dev/null | grep -q true; then
  echo "WARN: Shallow clone. SonarCloud needs full history for blame/new code. Run: git fetch --unshallow" >&2
fi

export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://localhost:5432/vectordb}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-postgres}"
export SPRING_DATASOURCE_PASSWORD="${SPRING_DATASOURCE_PASSWORD:-postgres}"
export RAG_JWT_SECRET="${RAG_JWT_SECRET:-test-secret-key-for-jwt-signing-must-be-long-enough-32}"
export RAG_TEST_USE_TESTCONTAINERS_DATASOURCE="${RAG_TEST_USE_TESTCONTAINERS_DATASOURCE:-false}"
export INTEGRATION_JDBC_URL="${INTEGRATION_JDBC_URL:-jdbc:postgresql://localhost:5432/testdb}"
export MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED="${MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED:-false}"

# If default `java` is < 21 but OpenJDK 21 is installed under /usr/lib/jvm (typical apt layout), use it for this script.
prefer_installed_jdk21() {
  if [[ "${SKIP_AUTO_JDK21:-0}" == "1" ]]; then
    return 0
  fi
  local line major=""
  if command -v java >/dev/null 2>&1; then
    line=$(java -version 2>&1 | head -n 1)
    if [[ ! "$line" =~ version\ \"1\.[0-9]+\. ]] && [[ "$line" =~ version\ \"([0-9]+) ]]; then
      major="${BASH_REMATCH[1]}"
    fi
    if [[ -n "$major" && "$major" -ge 21 ]]; then
      return 0
    fi
  fi
  if [[ -n "${JAVA_HOME:-}" && -x "${JAVA_HOME}/bin/java" ]]; then
    line=$("${JAVA_HOME}/bin/java" -version 2>&1 | head -n 1)
    major=""
    if [[ ! "$line" =~ version\ \"1\.[0-9]+\. ]] && [[ "$line" =~ version\ \"([0-9]+) ]]; then
      major="${BASH_REMATCH[1]}"
    fi
    if [[ -n "$major" && "$major" -ge 21 ]]; then
      export PATH="${JAVA_HOME}/bin:${PATH}"
      echo "Using JAVA_HOME=${JAVA_HOME} (Java ${major}) ahead of default java."
      return 0
    fi
  fi
  local home
  shopt -s nullglob
  local candidates=(/usr/lib/jvm/java-21-* /usr/lib/jvm/temurin-21-jdk-amd64 /usr/lib/jvm/temurin-21-jdk-arm64)
  shopt -u nullglob
  for home in "${candidates[@]}"; do
    [[ -x "${home}/bin/java" ]] || continue
    line=$("${home}/bin/java" -version 2>&1 | head -n 1)
    major=""
    if [[ ! "$line" =~ version\ \"1\.[0-9]+\. ]] && [[ "$line" =~ version\ \"([0-9]+) ]]; then
      major="${BASH_REMATCH[1]}"
    fi
    if [[ -n "$major" && "$major" -ge 21 ]]; then
      export JAVA_HOME="$home"
      export PATH="${JAVA_HOME}/bin:${PATH}"
      echo "Default java was older than 21; using installed JDK at JAVA_HOME=${JAVA_HOME}"
      return 0
    fi
  done
}

# rag-service uses --release 21; fail fast if `java` on PATH is older (common on WSL without JDK 21).
require_jdk21() {
  command -v java >/dev/null 2>&1 || {
    echo "ERROR: java not on PATH. Install JDK 21." >&2
    print_java21_hint
    exit 1
  }
  local line
  line=$(java -version 2>&1 | head -n 1)
  if [[ "$line" =~ version\ \"1\.[0-9]+\. ]]; then
    echo "ERROR: JDK 21+ required; found legacy ${line}" >&2
    print_java21_hint
    exit 1
  fi
  local major=""
  if [[ "$line" =~ version\ \"([0-9]+) ]]; then
    major="${BASH_REMATCH[1]}"
  fi
  if [[ -z "$major" ]]; then
    echo "WARN: Could not parse Java version from: ${line} — continuing." >&2
    return 0
  fi
  if [[ "$major" -lt 21 ]]; then
    echo "ERROR: JDK 21+ required for rag-service; found Java ${major} (${line})." >&2
    print_java21_hint
    exit 1
  fi
}

print_java21_hint() {
  echo "  1) Install JDK 21 (if missing): sudo apt update && sudo apt install -y openjdk-21-jdk" >&2
  echo "  2) Use JDK 21 for this shell (common fix when java still shows 11):" >&2
  echo "       export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64   # or: ls /usr/lib/jvm" >&2
  echo "       export PATH=\"\$JAVA_HOME/bin:\$PATH\"" >&2
  echo "       java -version   # must show 21.x" >&2
  echo "  3) Or switch system default: sudo update-alternatives --config java" >&2
  echo "  4) Or install Temurin 21: https://adoptium.net/" >&2
}

prepare_postgres() {
  if [[ "${SKIP_POSTGRES_PREP:-0}" == "1" ]]; then
    echo "Skipping Postgres bootstrap (SKIP_POSTGRES_PREP=1)."
    return 0
  fi

  command -v docker >/dev/null 2>&1 || {
    echo "ERROR: docker not found. Required for Postgres container." >&2
    exit 1
  }

  local CONTAINER_NAME="${RAG_CI_POSTGRES_CONTAINER:-rag-ci-postgres}"
  local IMAGE="pgvector/pgvector:pg16"

  local SCRIPT_DIR="${ROOT}/.github/local"
  local CI_EXT="${SCRIPT_DIR}/ci-postgres-extensions.sql"
  local TEST_INIT="${ROOT}/rag-service/src/test/resources/test-init.sql"

  if [[ ! -f "${CI_EXT}" ]]; then
    echo "ERROR: Missing ${CI_EXT}" >&2
    exit 1
  fi

  if [[ ! -f "${TEST_INIT}" ]]; then
    echo "ERROR: Missing ${TEST_INIT}" >&2
    exit 1
  fi

  wait_for_pg() {
    local i=0
    while [[ $i -lt 80 ]]; do
      if docker exec "${CONTAINER_NAME}" pg_isready -U postgres -d vectordb >/dev/null 2>&1; then
        return 0
      fi
      sleep 2
      i=$((i + 1))
    done
    echo "ERROR: Postgres did not become ready in time." >&2
    return 1
  }

  if docker ps -a --format '{{.Names}}' | grep -qx "${CONTAINER_NAME}"; then
    if docker ps --format '{{.Names}}' | grep -qx "${CONTAINER_NAME}"; then
      echo "Using existing running container: ${CONTAINER_NAME}"
    else
      echo "Starting existing container: ${CONTAINER_NAME}"
      docker start "${CONTAINER_NAME}"
    fi
  else
    echo "Creating container ${CONTAINER_NAME} (${IMAGE}) on port 5432..."
    docker run -d --name "${CONTAINER_NAME}" \
      -e POSTGRES_USER=postgres \
      -e POSTGRES_PASSWORD=postgres \
      -e POSTGRES_DB=vectordb \
      -p 5432:5432 \
      "${IMAGE}"
  fi

  wait_for_pg

  echo "Applying extensions (vectordb)..."
  docker cp "${CI_EXT}" "${CONTAINER_NAME}:/tmp/ci-extensions.sql"
  docker exec "${CONTAINER_NAME}" \
    psql -U postgres -d vectordb -v ON_ERROR_STOP=1 -f /tmp/ci-extensions.sql

  echo "Ensuring testdb + schema..."
  if ! docker exec "${CONTAINER_NAME}" psql -U postgres -d postgres -tAc \
    "SELECT 1 FROM pg_database WHERE datname = 'testdb'" | grep -q 1; then
    docker exec "${CONTAINER_NAME}" \
      psql -U postgres -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE testdb;"
  fi

  docker cp "${TEST_INIT}" "${CONTAINER_NAME}:/tmp/test-init.sql"
  docker exec "${CONTAINER_NAME}" \
    psql -U postgres -d testdb -v ON_ERROR_STOP=1 -f /tmp/test-init.sql

  echo "Postgres ready (shared CI container: ${CONTAINER_NAME})."
}

echo "==> Backend: mvn verify (JaCoCo)"
prefer_installed_jdk21
require_jdk21
prepare_postgres
chmod +x rag-service/mvnw
(
  cd rag-service
  ./mvnw -B clean verify --no-transfer-progress
)
(
  cd rag-service
  ./mvnw -B dependency:copy-dependencies -DoutputDirectory=target/dependency --no-transfer-progress
)

echo "==> Classifier: pytest + coverage.xml"
(
  cd classifier-service
  python -m pip install -r requirements.txt
  export MODELS_DIR=./models
  export DATA_DIR=./data
  python -m pytest tests/unit tests/regression/test_baseline_lib.py \
    -m "not integration and not slow" \
    -v
  python scripts/patch_coverage_xml_for_sonar.py
)

echo "==> Webapp: Vitest coverage (lcov.info)"
(
  cd webapp
  npm install --no-audit --no-fund
  npm run test:coverage
)

echo "==> SonarCloud scan (Docker scanner)"
command -v docker >/dev/null 2>&1 || {
  echo "ERROR: docker not found. Install Docker or run sonar-scanner-cli manually (see docs/development/sonar-local-analysis.md)." >&2
  exit 1
}

SONAR_SCANNER_OPTS=()
if [[ -n "${SONAR_BRANCH_NAME:-}" ]]; then
  SONAR_SCANNER_OPTS+=("-Dsonar.branch.name=${SONAR_BRANCH_NAME}")
fi

docker run --rm \
  -e SONAR_TOKEN \
  -e SONAR_HOST_URL=https://sonarcloud.io \
  -v "${ROOT}:/usr/src" \
  sonarsource/sonar-scanner-cli \
  -Dsonar.projectBaseDir=/usr/src \
  -Dsonar.host.url=https://sonarcloud.io \
  "${SONAR_SCANNER_OPTS[@]}"

echo "Done. Open https://sonarcloud.io/project/overview?id=sergio-tr_rag-spring-ai-ollama (adjust project key if yours differs)."
