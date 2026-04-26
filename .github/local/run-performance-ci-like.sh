#!/usr/bin/env bash
# Local CI parity for .github/workflows/reusable-ci-core.yml job "performance".
#
# Runs:
# - Postgres (pgvector image, same as CI)
# - Postgres bootstrap (extensions + testdb + test-init.sql)
# - rag-service Spring Boot (profile=e2e) on :9000
# - Gatling smoke (ActuatorHealthSimulation)
# - Infra probe (tests/performance/infra_probe.py)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RAG_SERVICE="${REPO_ROOT}/rag-service"

POSTGRES_CONTAINER="${RAG_CI_POSTGRES_CONTAINER:-rag-ci-pg}"
POSTGRES_IMAGE="${RAG_PLATFORM_POSTGRES_IMAGE:-pgvector/pgvector:0.8.2-pg16-bookworm}"
POSTGRES_PORT="${RAG_LOCAL_POSTGRES_PORT:-5432}"
STOP_AFTER="${RAG_CI_STOP_CONTAINER:-0}"
CI_NETWORK="${RAG_CI_NETWORK:-rag-ci}"
BACKEND_CONTAINER="${RAG_CI_BACKEND_CONTAINER:-rag-ci-backend}"
MAVEN_CACHE_VOLUME="${RAG_MAVEN_CACHE_VOLUME:-rag-m2-cache}"

JAVA_IMAGE="${RAG_JAVA_IMAGE:-eclipse-temurin:21-jdk}"
PYTHON_IMAGE="${RAG_PYTHON_IMAGE:-python:3.11-slim}"
GRADLE_CACHE_VOLUME="${RAG_GRADLE_CACHE_VOLUME:-rag-gradle-cache}"
PIP_CACHE_VOLUME="${RAG_PIP_CACHE_VOLUME:-rag-pip-cache}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --stop-after) STOP_AFTER=1; shift ;;
    -h|--help)
      echo "Usage: $0 [--stop-after]"
      exit 0
      ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

log() { echo "[ci-like] $*"; }
require() { command -v "$1" >/dev/null 2>&1 || { echo "error: missing required command: $1" >&2; exit 1; }; }

require docker
require curl

docker info >/dev/null 2>&1 || { echo "error: Docker is not running." >&2; exit 1; }

wait_for_pg() {
  local i=0
  while [[ $i -lt 30 ]]; do
    if docker exec "${POSTGRES_CONTAINER}" pg_isready -U postgres -d vectordb >/dev/null 2>&1; then
      return 0
    fi
    sleep 2
    i=$((i + 1))
  done
  return 1
}

start_postgres() {
  if docker ps -a --format '{{.Names}}' | grep -qx "${POSTGRES_CONTAINER}"; then
    if docker ps --format '{{.Names}}' | grep -qx "${POSTGRES_CONTAINER}"; then
      log "Using existing running Postgres container: ${POSTGRES_CONTAINER}"
    else
      log "Starting existing Postgres container: ${POSTGRES_CONTAINER}"
      docker start "${POSTGRES_CONTAINER}" >/dev/null
    fi
  else
    log "Creating Postgres container ${POSTGRES_CONTAINER} (${POSTGRES_IMAGE})..."
    docker run -d --name "${POSTGRES_CONTAINER}" \
      -e POSTGRES_USER=postgres \
      -e POSTGRES_PASSWORD=postgres \
      -e POSTGRES_DB=vectordb \
      -p "${POSTGRES_PORT}:5432" \
      "${POSTGRES_IMAGE}" >/dev/null
  fi

  log "Waiting for Postgres to become ready..."
  if ! wait_for_pg; then
    echo "error: Postgres did not become ready on localhost:${POSTGRES_PORT}" >&2
    exit 1
  fi
}

ensure_network() {
  docker network inspect "${CI_NETWORK}" >/dev/null 2>&1 || docker network create "${CI_NETWORK}" >/dev/null
  docker network connect "${CI_NETWORK}" "${POSTGRES_CONTAINER}" >/dev/null 2>&1 || true
}

prepare_postgres() {
  log "Preparing Postgres (extensions + testdb + test-init.sql)."
  docker cp "${REPO_ROOT}/.github/local/ci-postgres-extensions.sql" "${POSTGRES_CONTAINER}:/tmp/ci-postgres-extensions.sql"
  docker exec -e PGPASSWORD=postgres "${POSTGRES_CONTAINER}" \
    psql -U postgres -d vectordb -v ON_ERROR_STOP=1 -f /tmp/ci-postgres-extensions.sql >/dev/null

  if ! docker exec -e PGPASSWORD=postgres "${POSTGRES_CONTAINER}" \
    psql -U postgres -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='testdb'" | grep -q 1; then
    docker exec -e PGPASSWORD=postgres "${POSTGRES_CONTAINER}" \
      psql -U postgres -d postgres -v ON_ERROR_STOP=1 -c "CREATE DATABASE testdb;" >/dev/null
  fi

  docker cp "${RAG_SERVICE}/src/test/resources/test-init.sql" "${POSTGRES_CONTAINER}:/tmp/test-init.sql"
  docker exec -e PGPASSWORD=postgres "${POSTGRES_CONTAINER}" \
    psql -U postgres -d testdb -v ON_ERROR_STOP=1 -f /tmp/test-init.sql >/dev/null
}

start_backend() {
  log "Starting backend container (Spring Boot, profile=e2e)."
  docker rm -f "${BACKEND_CONTAINER}" >/dev/null 2>&1 || true
  docker run -d --name "${BACKEND_CONTAINER}" --network "${CI_NETWORK}" -p 9000:9000 \
    -v "${REPO_ROOT}:/repo" \
    -v "${MAVEN_CACHE_VOLUME}:/root/.m2" \
    -w /repo/rag-service \
    -e SPRING_DATASOURCE_URL="jdbc:postgresql://${POSTGRES_CONTAINER}:5432/vectordb" \
    -e SPRING_DATASOURCE_USERNAME=postgres \
    -e SPRING_DATASOURCE_PASSWORD=postgres \
    -e RAG_JWT_SECRET="e2e-ci-jwt-secret-must-be-at-least-32-chars" \
    -e RAG_TEST_USE_TESTCONTAINERS_DATASOURCE=false \
    -e RAG_CORS_ALLOWED_ORIGINS="http://127.0.0.1:3000,http://localhost:3000" \
    eclipse-temurin:21-jdk bash -lc "./mvnw -B -DskipTests spring-boot:run -Dspring-boot.run.profiles=e2e" \
    >/dev/null
}

wait_for_backend() {
  log "Waiting for backend health: http://127.0.0.1:9000/actuator/health"
  for _ in $(seq 1 90); do
    if curl -fsS http://127.0.0.1:9000/actuator/health >/dev/null 2>&1; then
      log "Backend healthy."
      return 0
    fi
    sleep 2
  done
  echo "--- backend log (tail) ---" >&2
  docker logs --tail 200 "${BACKEND_CONTAINER}" >&2 || true
  return 1
}

run_gatling_smoke() {
  log "Running Gatling smoke (ActuatorHealthSimulation) in ${JAVA_IMAGE}."
  docker run --rm \
    -v "${REPO_ROOT}:/repo" \
    -w /repo/tests/gatling \
    -v "${GRADLE_CACHE_VOLUME}:/root/.gradle" \
    -e GATLING_BASE_URL="http://host.docker.internal:9000" \
    -e GATLING_HEALTH_USERS="8" \
    -e GATLING_HEALTH_DURATION_SEC="20" \
    "${JAVA_IMAGE}" bash -lc \
      "chmod +x ./gradlew && ./gradlew --no-daemon gatlingRun --simulation simulations.ActuatorHealthSimulation"
}

run_infra_probe() {
  log "Running infra micro-probe in ${PYTHON_IMAGE}."
  docker run --rm \
    -v "${REPO_ROOT}:/repo" \
    -w /repo \
    -v "${PIP_CACHE_VOLUME}:/root/.cache/pip" \
    "${PYTHON_IMAGE}" bash -lc \
      "pip install -r tests/performance/requirements.txt >/dev/null && python tests/performance/infra_probe.py --backend-base-url http://host.docker.internal:9000 --repetitions 5 --warmup 1 --concurrency 1 --timeout-s 30"
}

stop_backend() {
  docker rm -f "${BACKEND_CONTAINER}" >/dev/null 2>&1 || true
}

cleanup() {
  stop_backend
  if [[ "${STOP_AFTER}" = "1" ]]; then
    log "Removing Postgres container: ${POSTGRES_CONTAINER}"
    docker rm -f "${POSTGRES_CONTAINER}" >/dev/null 2>&1 || true
  fi
}
trap cleanup EXIT

log "Repo root: ${REPO_ROOT}"
start_postgres
ensure_network
prepare_postgres
start_backend
wait_for_backend
run_gatling_smoke
run_infra_probe

