#!/usr/bin/env bash
# Local parity for main/master PR: same as dev gate plus performance (Gatling smoke + infra_probe).
set -euo pipefail
D="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
"${D}/run-pr-dev.sh" "$@"

SCRIPT_DIR="${D}"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
cd "${REPO_ROOT}"

CONTAINER_NAME="${RAG_CI_POSTGRES_CONTAINER}"
if ! docker ps --format '{{.Names}}' | grep -qx "${CONTAINER_NAME}"; then
  echo "error: Postgres container not running after run-pr-dev."
  exit 1
fi

stop_spring() {
  if [[ -f /tmp/spring-perf.pid ]]; then
    kill "$(cat /tmp/spring-perf.pid)" 2>/dev/null || true
    rm -f /tmp/spring-perf.pid
  fi
}
trap stop_spring EXIT

CI_EXT="${SCRIPT_DIR}/ci-postgres-extensions.sql"
TEST_INIT="${REPO_ROOT}/rag-service/src/test/resources/test-init.sql"
export SPRING_DATASOURCE_URL="jdbc:postgresql://localhost:${RAG_LOCAL_POSTGRES_PORT}/vectordb"
export SPRING_DATASOURCE_USERNAME=postgres
export SPRING_DATASOURCE_PASSWORD=postgres
export RAG_JWT_SECRET="e2e-ci-jwt-secret-must-be-at-least-32-chars"
export RAG_TEST_USE_TESTCONTAINERS_DATASOURCE=false
export INTEGRATION_JDBC_URL="jdbc:postgresql://localhost:${RAG_LOCAL_POSTGRES_PORT}/testdb"
export RAG_CORS_ALLOWED_ORIGINS="http://127.0.0.1:3000,http://localhost:3000"

PGPASSWORD=postgres psql -h localhost -p "${RAG_LOCAL_POSTGRES_PORT}" -U postgres -d vectordb -v ON_ERROR_STOP=1 -f "${CI_EXT}"
if ! PGPASSWORD=postgres psql -h localhost -p "${RAG_LOCAL_POSTGRES_PORT}" -U postgres -d postgres -tAc \
  "SELECT 1 FROM pg_database WHERE datname = 'testdb'" | grep -q 1; then
  PGPASSWORD=postgres psql -h localhost -p "${RAG_LOCAL_POSTGRES_PORT}" -U postgres -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE testdb;"
fi
PGPASSWORD=postgres psql -h localhost -p "${RAG_LOCAL_POSTGRES_PORT}" -U postgres -d testdb -v ON_ERROR_STOP=1 -f "${TEST_INIT}"

cd "${REPO_ROOT}/rag-service"
chmod +x mvnw
nohup ./mvnw -B -DskipTests spring-boot:run -Dspring-boot.run.profiles=e2e \
  > /tmp/spring-perf.log 2>&1 &
echo $! > /tmp/spring-perf.pid

for i in $(seq 1 90); do
  if curl -fsS "http://127.0.0.1:${RAG_LOCAL_BACKEND_PORT}/actuator/health" >/dev/null 2>&1; then
    break
  fi
  if [[ "$i" -eq 90 ]]; then
    tail -n 200 /tmp/spring-perf.log || true
    exit 1
  fi
  sleep 2
done

echo "==> Gatling smoke (local)"
chmod +x "${REPO_ROOT}/tests/gatling/gradlew"
(
  cd "${REPO_ROOT}/tests/gatling"
  export GATLING_BASE_URL="http://127.0.0.1:${RAG_LOCAL_BACKEND_PORT}"
  export GATLING_HEALTH_USERS=8
  export GATLING_HEALTH_DURATION_SEC=20
  ./gradlew --no-daemon gatlingRun --simulation simulations.ActuatorHealthSimulation
)

echo "==> infra_probe (local)"
python3 -m pip install -q -r "${REPO_ROOT}/tests/performance/requirements.txt"
python3 "${REPO_ROOT}/tests/performance/infra_probe.py" \
  --backend-base-url "http://127.0.0.1:${RAG_LOCAL_BACKEND_PORT}" \
  --repetitions 5 \
  --warmup 1 \
  --concurrency 1 \
  --timeout-s 30 \
  --output-json /tmp/infra-probe-local.json

stop_spring
trap - EXIT
