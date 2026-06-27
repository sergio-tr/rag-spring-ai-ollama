#!/usr/bin/env bash
# Local parity: backend job from reusable-ci-core (Postgres + mvn verify + javadoc).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

STOP_AFTER="${RAG_CI_STOP_CONTAINER:-0}"
PREPARE_ONLY=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --stop-after) STOP_AFTER=1; shift ;;
    --prepare-only) PREPARE_ONLY=1; shift ;;
    -h|--help)
      echo "Usage: $0 [--stop-after] [--prepare-only]"
      exit 0
      ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

RAG_SERVICE="$(cd "${SCRIPT_DIR}/../../rag-service" && pwd)"
REPO_ROOT="$(cd "${RAG_SERVICE}/.." && pwd)"
TEST_INIT="${RAG_SERVICE}/src/test/resources/test-init.sql"
CI_EXT="${SCRIPT_DIR}/ci-postgres-extensions.sql"
CONTAINER_NAME="${RAG_CI_POSTGRES_CONTAINER}"
IMAGE="${RAG_PLATFORM_POSTGRES_IMAGE}"

if [[ ! -f "${TEST_INIT}" || ! -f "${CI_EXT}" ]]; then
  echo "error: missing test-init or ci-postgres-extensions.sql"
  exit 1
fi
if ! docker info >/dev/null 2>&1; then
  echo "error: Docker is not running."
  exit 1
fi

wait_for_pg() {
  local i=0
  # Max ~60s (30 * 2s) per CI contract
  while [[ $i -lt 30 ]]; do
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
    mapped_port="$(docker port "${CONTAINER_NAME}" 5432/tcp 2>/dev/null | head -1 | sed 's/.*://')"
    if [[ -n "${mapped_port}" ]]; then
      export RAG_LOCAL_POSTGRES_PORT="${mapped_port}"
      echo "Using published Postgres port ${RAG_LOCAL_POSTGRES_PORT} for ${CONTAINER_NAME}"
    fi
  else
    docker start "${CONTAINER_NAME}"
  fi
else
  echo "Creating container ${CONTAINER_NAME} (${IMAGE})..."
  docker run -d --name "${CONTAINER_NAME}" \
    -e POSTGRES_USER=postgres \
    -e POSTGRES_PASSWORD=postgres \
    -e POSTGRES_DB=vectordb \
    -p "${RAG_LOCAL_POSTGRES_PORT}:5432" \
    "${IMAGE}"
fi

wait_for_pg

docker cp "${CI_EXT}" "${CONTAINER_NAME}:/tmp/ci-extensions.sql"
docker exec "${CONTAINER_NAME}" psql -U postgres -d vectordb -v ON_ERROR_STOP=1 -f /tmp/ci-extensions.sql

if ! docker exec "${CONTAINER_NAME}" psql -U postgres -d postgres -tAc \
  "SELECT 1 FROM pg_database WHERE datname = 'testdb'" | grep -q 1; then
  docker exec "${CONTAINER_NAME}" psql -U postgres -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE testdb;"
fi
docker cp "${TEST_INIT}" "${CONTAINER_NAME}:/tmp/test-init.sql"
docker exec "${CONTAINER_NAME}" psql -U postgres -d testdb -v ON_ERROR_STOP=1 -f /tmp/test-init.sql

if [[ "${PREPARE_ONLY}" -eq 1 ]]; then
  echo "Prepare-only: done."
  exit 0
fi

export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:${RAG_LOCAL_POSTGRES_PORT}/vectordb"
export SPRING_DATASOURCE_USERNAME="postgres"
export SPRING_DATASOURCE_PASSWORD="postgres"
export RAG_JWT_SECRET="test-secret-key-for-jwt-signing-must-be-long-enough-32"
export RAG_TEST_USE_TESTCONTAINERS_DATASOURCE="false"
export INTEGRATION_JDBC_URL="jdbc:postgresql://localhost:${RAG_LOCAL_POSTGRES_PORT}/testdb"

cd "${RAG_SERVICE}"
chmod +x mvnw 2>/dev/null || true
./mvnw -B clean verify
./mvnw -B -q javadoc:javadoc

if [[ "${STOP_AFTER}" -eq 1 ]]; then
  docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
fi
