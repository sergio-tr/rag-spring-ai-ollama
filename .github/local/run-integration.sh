#!/usr/bin/env bash
# Local parity: stack integration job (Postgres + Spring e2e + pytest).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${REPO_ROOT}"

CONTAINER_NAME="${RAG_CI_POSTGRES_CONTAINER}"
IMAGE="${RAG_PLATFORM_POSTGRES_IMAGE}"
CI_EXT="${SCRIPT_DIR}/ci-postgres-extensions.sql"
TEST_INIT="${REPO_ROOT}/rag-service/src/test/resources/test-init.sql"

stop_spring() {
  if [[ -f /tmp/spring-integration.pid ]]; then
    kill "$(cat /tmp/spring-integration.pid)" 2>/dev/null || true
    rm -f /tmp/spring-integration.pid
  fi
}
trap stop_spring EXIT

if ! docker info >/dev/null 2>&1; then
  echo "error: Docker required."
  exit 1
fi

wait_for_pg() {
  local i=0
  while [[ $i -lt 30 ]]; do
    if docker exec "${CONTAINER_NAME}" pg_isready -U postgres -d vectordb >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
    i=$((i + 1))
  done
  return 1
}

if ! docker ps --format '{{.Names}}' | grep -qx "${CONTAINER_NAME}"; then
  echo "error: Postgres container ${CONTAINER_NAME} not running. Start with run-ci-core.sh first or docker run ${IMAGE}."
  exit 1
fi
wait_for_pg || { echo "error: Postgres not ready"; exit 1; }

export INTEGRATION_USE_TESTCONTAINERS=0
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:${RAG_LOCAL_POSTGRES_PORT}/vectordb"
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=postgres
export RAG_CORS_ALLOWED_ORIGINS="http://127.0.0.1:3000,http://localhost:3000"
export RAG_JWT_SECRET="e2e-ci-jwt-secret-must-be-at-least-32-chars"
export RAG_TEST_USE_TESTCONTAINERS_DATASOURCE=false
export INTEGRATION_JDBC_URL="jdbc:postgresql://localhost:${RAG_LOCAL_POSTGRES_PORT}/testdb"
export INTEGRATION_CHECK_OBS=0
export INTEGRATION_BACKEND_URL="http://127.0.0.1:${RAG_LOCAL_BACKEND_PORT}"
export INTEGRATION_CLASSIFIER_URL="http://127.0.0.1:8000"
export INTEGRATION_ADMIN_EMAIL=admin@e2e.local
export INTEGRATION_ADMIN_PASSWORD=e2e

# Apply extensions + testdb using host psql against published port (same as prepare-postgres action intent).
if command -v psql >/dev/null 2>&1; then
  PGPASSWORD=postgres psql -h localhost -p "${RAG_LOCAL_POSTGRES_PORT}" -U postgres -d vectordb -v ON_ERROR_STOP=1 -f "${CI_EXT}"
  if ! PGPASSWORD=postgres psql -h localhost -p "${RAG_LOCAL_POSTGRES_PORT}" -U postgres -d postgres -tAc \
    "SELECT 1 FROM pg_database WHERE datname = 'testdb'" | grep -q 1; then
    PGPASSWORD=postgres psql -h localhost -p "${RAG_LOCAL_POSTGRES_PORT}" -U postgres -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE testdb;"
  fi
  PGPASSWORD=postgres psql -h localhost -p "${RAG_LOCAL_POSTGRES_PORT}" -U postgres -d testdb -v ON_ERROR_STOP=1 -f "${TEST_INIT}"
else
  echo "error: psql not on PATH; install postgresql-client or use run-ci-core to prepare DB inside container."
  exit 1
fi

cd "${REPO_ROOT}/rag-service"
chmod +x mvnw
nohup ./mvnw -B -DskipTests spring-boot:run -Dspring-boot.run.profiles=e2e \
  > /tmp/spring-integration.log 2>&1 &
echo $! > /tmp/spring-integration.pid

for i in $(seq 1 90); do
  if curl -fsS "http://127.0.0.1:${RAG_LOCAL_BACKEND_PORT}/actuator/health" >/dev/null 2>&1; then
    echo "Backend healthy"
    break
  fi
  if [[ "$i" -eq 90 ]]; then
    tail -n 200 /tmp/spring-integration.log || true
    exit 1
  fi
  sleep 2
done

cd "${REPO_ROOT}"
python3 -m pip install -q -r tests/integration/requirements.txt
python3 -m pytest tests/integration -v --tb=short --ignore=tests/integration/test_tc_postgres_smoke.py

stop_spring
trap - EXIT
