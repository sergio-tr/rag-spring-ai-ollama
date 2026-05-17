#!/usr/bin/env bash
# Local parity: e2e_fullstack job (Postgres + Spring e2e + Playwright @fullstack).
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"

REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${REPO_ROOT}"

CONTAINER_NAME="${RAG_CI_POSTGRES_CONTAINER}"
CI_EXT="${SCRIPT_DIR}/ci-postgres-extensions.sql"
TEST_INIT="${REPO_ROOT}/rag-service/src/test/resources/test-init.sql"

stop_spring() {
  if [[ -f /tmp/spring-e2e.pid ]]; then
    kill "$(cat /tmp/spring-e2e.pid)" 2>/dev/null || true
    rm -f /tmp/spring-e2e.pid
  fi
}
trap stop_spring EXIT

if ! docker ps --format '{{.Names}}' | grep -qx "${CONTAINER_NAME}"; then
  echo "error: Postgres container ${CONTAINER_NAME} not running."
  exit 1
fi

export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:${RAG_LOCAL_POSTGRES_PORT}/vectordb"
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=postgres
export RAG_CORS_ALLOWED_ORIGINS="http://127.0.0.1:3000,http://localhost:3000"
export RAG_JWT_SECRET="e2e-ci-jwt-secret-must-be-at-least-32-chars"
export RAG_TEST_USE_TESTCONTAINERS_DATASOURCE=false
export INTEGRATION_JDBC_URL="jdbc:postgresql://localhost:${RAG_LOCAL_POSTGRES_PORT}/testdb"

PGPASSWORD=postgres psql -h localhost -p "${RAG_LOCAL_POSTGRES_PORT}" -U postgres -d vectordb -v ON_ERROR_STOP=1 -f "${CI_EXT}"
if ! PGPASSWORD=postgres psql -h localhost -p "${RAG_LOCAL_POSTGRES_PORT}" -U postgres -d postgres -tAc \
  "SELECT 1 FROM pg_database WHERE datname = 'testdb'" | grep -q 1; then
  PGPASSWORD=postgres psql -h localhost -p "${RAG_LOCAL_POSTGRES_PORT}" -U postgres -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE testdb;"
fi
PGPASSWORD=postgres psql -h localhost -p "${RAG_LOCAL_POSTGRES_PORT}" -U postgres -d testdb -v ON_ERROR_STOP=1 -f "${TEST_INIT}"

cd "${REPO_ROOT}/rag-service"
chmod +x mvnw
nohup ./mvnw -B -DskipTests spring-boot:run -Dspring-boot.run.profiles=e2e \
  > /tmp/spring-e2e.log 2>&1 &
echo $! > /tmp/spring-e2e.pid

for i in $(seq 1 90); do
  if curl -fsS "http://127.0.0.1:${RAG_LOCAL_BACKEND_PORT}/actuator/health" >/dev/null 2>&1; then
    echo "Backend healthy"
    break
  fi
  if [[ "$i" -eq 90 ]]; then
    tail -n 200 /tmp/spring-e2e.log || true
    exit 1
  fi
  sleep 2
done

cd "${REPO_ROOT}/webapp"
npm install --no-audit --no-fund
npm run build
npx playwright install --with-deps chromium
export NEXT_PUBLIC_API_BASE_URL="http://127.0.0.1:${RAG_LOCAL_BACKEND_PORT}"
export NEXT_PUBLIC_RAG_API_PREFIX=/api/v5
export E2E_ALLOW_INSECURE_COOKIES=true
export PLAYWRIGHT_BASE_URL=http://127.0.0.1:3000
npm run test:e2e:fullstack:ci-fast

stop_spring
trap - EXIT
