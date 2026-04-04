#!/usr/bin/env bash
# Replicate .github/workflows/ci.yml backend Postgres + env, then run mvn verify.
# Requires: Docker, JDK 21, bash. Run from repository root (or any directory — script locates the repo).

set -euo pipefail

CONTAINER_NAME="${RAG_CI_POSTGRES_CONTAINER:-rag-ci-postgres}"
IMAGE="pgvector/pgvector:pg16"
STOP_AFTER="${RAG_CI_STOP_CONTAINER:-0}"

usage() {
  echo "Usage: $0 [--stop-after] [--prepare-only]"
  echo "  --stop-after     Remove the Postgres container after verify (default: keep running)."
  echo "  --prepare-only   Only start Postgres and run SQL init; skip mvn verify."
}

PREPARE_ONLY=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --stop-after) STOP_AFTER=1; shift ;;
    --prepare-only) PREPARE_ONLY=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $1"; usage; exit 1 ;;
  esac
done

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
RAG_SERVICE="$(cd "${SCRIPT_DIR}/.." && pwd)"
REPO_ROOT="$(cd "${RAG_SERVICE}/.." && pwd)"
TEST_INIT="${RAG_SERVICE}/src/test/resources/test-init.sql"
CI_EXT="${SCRIPT_DIR}/ci-postgres-extensions.sql"

if [[ ! -f "${TEST_INIT}" ]]; then
  echo "error: test-init.sql not found at ${TEST_INIT}"
  exit 1
fi
if [[ ! -f "${CI_EXT}" ]]; then
  echo "error: ci-postgres-extensions.sql not found at ${CI_EXT}"
  exit 1
fi

if ! docker info >/dev/null 2>&1; then
  echo "error: Docker is not running or not available in PATH."
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
  echo "error: Postgres did not become ready in time."
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

echo "Creating extensions on vectordb (same as CI)..."
docker cp "${CI_EXT}" "${CONTAINER_NAME}:/tmp/ci-extensions.sql"
docker exec "${CONTAINER_NAME}" psql -U postgres -d vectordb -v ON_ERROR_STOP=1 -f /tmp/ci-extensions.sql

echo "Ensuring testdb + test-init.sql..."
if ! docker exec "${CONTAINER_NAME}" psql -U postgres -d postgres -tAc \
  "SELECT 1 FROM pg_database WHERE datname = 'testdb'" | grep -q 1; then
  docker exec "${CONTAINER_NAME}" psql -U postgres -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE testdb;"
fi

docker cp "${TEST_INIT}" "${CONTAINER_NAME}:/tmp/test-init.sql"
docker exec "${CONTAINER_NAME}" psql -U postgres -d testdb -v ON_ERROR_STOP=1 -f /tmp/test-init.sql

if [[ "${PREPARE_ONLY}" -eq 1 ]]; then
  echo "Prepare-only: done. Run verify manually with CI env vars (see rag-service/README.md)."
  exit 0
fi

export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:5432/vectordb"
export SPRING_DATASOURCE_USERNAME="postgres"
export SPRING_DATASOURCE_PASSWORD="postgres"
export RAG_JWT_SECRET="test-secret-key-for-jwt-signing-must-be-long-enough-32"
export RAG_TEST_USE_TESTCONTAINERS_DATASOURCE="false"
export INTEGRATION_JDBC_URL="jdbc:postgresql://localhost:5432/testdb"

cd "${RAG_SERVICE}"
chmod +x mvnw 2>/dev/null || true
./mvnw -B clean verify

if [[ "${STOP_AFTER}" -eq 1 ]]; then
  echo "Stopping and removing ${CONTAINER_NAME}..."
  docker rm -f "${CONTAINER_NAME}" >/dev/null
fi
