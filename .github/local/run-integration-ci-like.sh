#!/usr/bin/env bash
# Local CI parity for .github/workflows/reusable-ci-core.yml job "integration".
# - Uses the same pgvector image and DB bootstrap (extensions + testdb + test-init.sql)
# - Starts rag-service with profile "e2e" on :9000
# - Seeds the ADMIN user used by pytest (admin@e2e.local / e2e)
# - Runs pytest inside a Linux python:3.11 container (parity with GitHub Actions)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/common.sh
source "${SCRIPT_DIR}/lib/common.sh"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RAG_SERVICE="${REPO_ROOT}/rag-service"

POSTGRES_CONTAINER="${RAG_CI_POSTGRES_CONTAINER:-rag-ci-pg}"
POSTGRES_IMAGE="${RAG_PLATFORM_POSTGRES_IMAGE}"
POSTGRES_PORT="${RAG_LOCAL_POSTGRES_PORT}"
CI_NETWORK="${RAG_CI_NETWORK:-rag-ci}"
BACKEND_CONTAINER="${RAG_CI_BACKEND_CONTAINER:-rag-ci-backend}"
CLASSIFIER_CONTAINER="${RAG_CI_CLASSIFIER_CONTAINER:-rag-ci-classifier}"
MAVEN_CACHE_VOLUME="${RAG_MAVEN_CACHE_VOLUME:-rag-m2-cache}"
PIP_CACHE_VOLUME="${RAG_PIP_CACHE_VOLUME:-rag-pip-cache}"

STOP_AFTER="${RAG_CI_STOP_CONTAINER:-0}"

REUSE_POSTGRES="${RAG_CI_REUSE_POSTGRES:-0}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --stop-after) STOP_AFTER=1; shift ;;
    --reuse-postgres) REUSE_POSTGRES=1; shift ;;
    -h|--help)
      echo "Usage: $0 [--stop-after] [--reuse-postgres]"
      echo "  Default: recreate Postgres so Flyway matches current migration checksums."
      echo "  --reuse-postgres / RAG_CI_REUSE_POSTGRES=1: keep existing ${POSTGRES_CONTAINER} (may fail validate)."
      exit 0
      ;;
    *) echo "Unknown option: $1"; exit 1 ;;
  esac
done

log() { echo "[ci-like] $*"; }

require() {
  command -v "$1" >/dev/null 2>&1 || { echo "error: missing required command: $1" >&2; exit 1; }
}

require docker
require curl

PYTEST_LOG_PATH="${PYTEST_LOG_PATH:-/tmp/pytest-integration-ci-like.log}"

# Closure-grade strictness defaults:
# - INTEGRATION_STRICT=1 prevents "all skipped because stack is down" false-green.
# - INTEGRATION_REQUIRE_CLASSIFIER=1 can be set by operators when classifier reachability is required for closure.
export INTEGRATION_STRICT="${INTEGRATION_STRICT:-1}"
export INTEGRATION_FAIL_ON_UNREACHABLE="${INTEGRATION_FAIL_ON_UNREACHABLE:-${INTEGRATION_STRICT}}"
export INTEGRATION_REQUIRE_CLASSIFIER="${INTEGRATION_REQUIRE_CLASSIFIER:-0}"
export INTEGRATION_ADMIN_EMAIL="${INTEGRATION_ADMIN_EMAIL:-admin@e2e.local}"
export INTEGRATION_ADMIN_PASSWORD="${INTEGRATION_ADMIN_PASSWORD:-e2e}"
export INTEGRATION_LOGIN_EMAIL="${INTEGRATION_LOGIN_EMAIL:-dev@local.test}"
export INTEGRATION_LOGIN_PASSWORD="${INTEGRATION_LOGIN_PASSWORD:-dev}"

if [[ ! -f "${REPO_ROOT}/.github/local/ci-postgres-extensions.sql" ]]; then
  echo "error: missing .github/local/ci-postgres-extensions.sql" >&2
  exit 1
fi
if [[ ! -f "${RAG_SERVICE}/src/test/resources/test-init.sql" ]]; then
  echo "error: missing rag-service/src/test/resources/test-init.sql" >&2
  exit 1
fi
if [[ ! -f "${RAG_SERVICE}/mvnw" && ! -f "${RAG_SERVICE}/mvnw.cmd" ]]; then
  echo "error: missing rag-service/mvnw (or mvnw.cmd)" >&2
  exit 1
fi

docker info >/dev/null 2>&1 || { echo "error: Docker is not running." >&2; exit 1; }

resolve_host_gateway_ip() {
  if [[ -n "${HOST_GATEWAY_IP:-}" ]]; then
    echo "${HOST_GATEWAY_IP}"
    return
  fi
  local resolved
  resolved="$(
    docker run --rm --add-host=host-gateway.internal:host-gateway alpine:3.20 \
      getent hosts host-gateway.internal 2>/dev/null | awk 'NR == 1 { print $1 }' || true
  )"
  if [[ "${resolved}" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "host-gateway"
    return
  fi
  echo "172.17.0.1"
}

ensure_network() {
  docker network inspect "${CI_NETWORK}" >/dev/null 2>&1 || docker network create "${CI_NETWORK}" >/dev/null
  docker network connect "${CI_NETWORK}" "${POSTGRES_CONTAINER}" >/dev/null 2>&1 || true
}

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
  if [[ "${REUSE_POSTGRES}" != "1" ]]; then
    log "Recreating Postgres container for clean Flyway state (${POSTGRES_CONTAINER})."
    docker rm -f "${POSTGRES_CONTAINER}" >/dev/null 2>&1 || true
  fi
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
    -e RAG_CORS_ALLOWED_ORIGINS="http://127.0.0.1:3000,http://localhost:3000" \
    -e RAG_JWT_SECRET="e2e-ci-jwt-secret-must-be-at-least-32-chars" \
    -e RAG_TEST_USE_TESTCONTAINERS_DATASOURCE=false \
    -e RAG_API_PRODUCT_BASE_PATH=/api/v5 \
    -e RAG_HEALTH_OLLAMA_ENABLED=false \
    -e RAG_HEALTH_CLASSIFIER_ENABLED=false \
    eclipse-temurin:21-jdk bash -lc "./mvnw -B -DskipTests compile spring-boot:run -Dspring-boot.run.profiles=e2e -Dspring-boot.run.jvmArguments=-Dspring.devtools.restart.enabled=false" \
    >/dev/null
}

SPRING_LOG_PATH="${SPRING_LOG_PATH:-/tmp/spring-integration-docker.log}"

wait_for_backend() {
  local readiness_url="http://127.0.0.1:9000/actuator/health/readiness"
  log "Waiting for backend readiness (fail-fast on Flyway): ${readiness_url}"
  for i in $(seq 1 45); do
    docker logs --tail 150 "${BACKEND_CONTAINER}" > "${SPRING_LOG_PATH}" 2>&1 || true
    if grep -qE 'FlywayValidateException|Migration checksum mismatch|Application run failed' "${SPRING_LOG_PATH}" 2>/dev/null; then
      echo "error: Spring failed during startup (see backend log tail)." >&2
      tail -n 120 "${SPRING_LOG_PATH}" >&2 || true
      return 1
    fi
    code="$(curl -s -o /dev/null -w '%{http_code}' "${readiness_url}" 2>/dev/null || true)"
    if [[ "${code}" == "200" ]]; then
      log "Backend ready after ${i} attempt(s)."
      return 0
    fi
    sleep 2
  done
  echo "error: backend readiness never returned 200 (${readiness_url})" >&2
  tail -n 120 "${SPRING_LOG_PATH}" >&2 || true
  return 1
}

wait_for_classifier() {
  log "Waiting for classifier health: http://127.0.0.1:8000/health"
  for _ in $(seq 1 120); do
    if curl -fsS http://127.0.0.1:8000/health >/dev/null 2>&1; then
      log "Classifier healthy."
      return 0
    fi
    sleep 2
  done
  echo "--- classifier log (tail) ---" >&2
  docker logs --tail 200 "${CLASSIFIER_CONTAINER}" >&2 || true
  return 1
}

start_classifier() {
  if curl -sf "http://127.0.0.1:8000/health" >/dev/null 2>&1; then
    log "Classifier already reachable on :8000; reusing host service."
    return 0
  fi
  log "Starting classifier container (uvicorn) on :8000."
  docker rm -f "${CLASSIFIER_CONTAINER}" >/dev/null 2>&1 || true
  docker run -d --name "${CLASSIFIER_CONTAINER}" --network "${CI_NETWORK}" -p 8000:8000 \
    -v "${REPO_ROOT}:/repo" \
    -v "${PIP_CACHE_VOLUME}:/root/.cache/pip" \
    -w /repo/classifier-service \
          python:3.11-slim bash -lc "pip install -q -r requirements.txt && uvicorn uvicorn_entry:app --host 0.0.0.0 --port 8000" \
    >/dev/null
}

seed_integration_users() {
  log "Seeding integration users (admin@e2e.local + dev@local.test for pytest JWT flows)."
  docker exec -e PGPASSWORD=postgres "${POSTGRES_CONTAINER}" \
    psql -U postgres -d vectordb -v ON_ERROR_STOP=1 -c "
      INSERT INTO users (id, email, password_hash, name, role, created_at, email_verified, email_verified_at)
      VALUES
        (
          'e2e0ad00-0000-4000-8000-000000000001',
          'admin@e2e.local',
          '{noop}e2e',
          'E2E Admin',
          'ADMIN',
          CURRENT_TIMESTAMP,
          true,
          CURRENT_TIMESTAMP
        ),
        (
          'e2e0ad00-0000-4000-8000-000000000002',
          'dev@local.test',
          '{noop}dev',
          'Dev User',
          'USER',
          CURRENT_TIMESTAMP,
          true,
          CURRENT_TIMESTAMP
        )
      ON CONFLICT (email) DO UPDATE SET
        password_hash = EXCLUDED.password_hash,
        name = EXCLUDED.name,
        role = EXCLUDED.role,
        email_verified = true,
        email_verified_at = COALESCE(users.email_verified_at, EXCLUDED.email_verified_at);
    " >/dev/null
}

run_pytest_linux() {
  log "Running pytest in python:3.11-slim container."
  rm -f "${PYTEST_LOG_PATH}"
  set +e
  docker run --rm \
    -v "${REPO_ROOT}:/repo" \
    -w /repo \
    -v "${PIP_CACHE_VOLUME}:/root/.cache/pip" \
    -e INTEGRATION_USE_TESTCONTAINERS="0" \
    -e INTEGRATION_CHECK_OBS="0" \
    -e INTEGRATION_STRICT="${INTEGRATION_STRICT}" \
    -e INTEGRATION_FAIL_ON_UNREACHABLE="${INTEGRATION_FAIL_ON_UNREACHABLE}" \
    -e INTEGRATION_REQUIRE_CLASSIFIER="${INTEGRATION_REQUIRE_CLASSIFIER}" \
    -e INTEGRATION_BACKEND_URL="http://host.docker.internal:9000" \
    -e INTEGRATION_CLASSIFIER_URL="http://host.docker.internal:8000" \
    -e INTEGRATION_ADMIN_EMAIL="${INTEGRATION_ADMIN_EMAIL}" \
    -e INTEGRATION_ADMIN_PASSWORD="${INTEGRATION_ADMIN_PASSWORD}" \
    -e INTEGRATION_LOGIN_EMAIL="${INTEGRATION_LOGIN_EMAIL}" \
    -e INTEGRATION_LOGIN_PASSWORD="${INTEGRATION_LOGIN_PASSWORD}" \
    -e INTEGRATION_RAG_PRODUCT_BASE_PATH="/api/v5" \
    python:3.11-slim bash -lc \
      "pip install -r tests/integration/requirements.txt >/dev/null && python -m pytest tests/integration -v --tb=short --ignore=tests/integration/test_tc_postgres_smoke.py"
  status=$?
  set -e

  # Capture full output for evidence and post-run validation.
  # (In strict closure mode we fail the run if pytest would otherwise be false-green.)
  if [[ -f "${PYTEST_LOG_PATH}" ]]; then
    : # already captured
  fi

  return "${status}"
}

guard_no_false_green() {
  if [[ ! -f "${PYTEST_LOG_PATH}" ]]; then
    echo "error: pytest log missing at ${PYTEST_LOG_PATH}" >&2
    exit 1
  fi
  local collected
  collected="$(
    grep -Eo 'collected[[:space:]]+[0-9]+[[:space:]]+items' "${PYTEST_LOG_PATH}" \
      | tail -n 1 \
      | grep -Eo '[0-9]+' \
      | tail -n 1 || true
  )"
  if [[ -z "${collected}" ]]; then
    echo "error: could not parse pytest collected count from ${PYTEST_LOG_PATH}" >&2
    exit 2
  fi
  if [[ "${collected}" -le 0 ]]; then
    echo "error: pytest collected ${collected} items (invalid closure)" >&2
    exit 2
  fi

  local summary
  summary="$(grep -E '==.*(passed|failed|skipped).*(in|seconds|ms|s)' "${PYTEST_LOG_PATH}" | tail -n 1 || true)"
  if [[ -z "${summary}" ]]; then
    # Be conservative: if we can't parse, don't claim closure-grade validity.
    echo "error: could not parse pytest summary line from ${PYTEST_LOG_PATH}" >&2
    exit 3
  fi
  local skipped
  skipped="$(echo "${summary}" | grep -Eo '[0-9]+[[:space:]]+skipped' | grep -Eo '[0-9]+' | head -n 1 || true)"
  if [[ -z "${skipped}" ]]; then
    skipped=0
  fi
  if [[ "${skipped}" -eq "${collected}" ]]; then
    echo "error: false-green integration run: skipped == collected == ${collected}" >&2
    exit 4
  fi
}

stop_backend() {
  docker rm -f "${BACKEND_CONTAINER}" >/dev/null 2>&1 || true
}

cleanup() {
  stop_backend
  docker rm -f "${CLASSIFIER_CONTAINER}" >/dev/null 2>&1 || true
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
if [[ "${INTEGRATION_REQUIRE_CLASSIFIER}" = "1" ]]; then
  start_classifier
  wait_for_classifier
fi
seed_integration_users
log "Pytest log: ${PYTEST_LOG_PATH}"
host_gateway="$(resolve_host_gateway_ip)"
log "Pytest container host mapping: host.docker.internal -> ${host_gateway}"
set -o pipefail
docker run --rm \
  --add-host="host.docker.internal:${host_gateway}" \
  -v "${REPO_ROOT}:/repo" \
  -w /repo \
  -v "${PIP_CACHE_VOLUME}:/root/.cache/pip" \
  -e INTEGRATION_USE_TESTCONTAINERS="0" \
  -e INTEGRATION_CHECK_OBS="0" \
  -e INTEGRATION_STRICT="${INTEGRATION_STRICT}" \
  -e INTEGRATION_FAIL_ON_UNREACHABLE="${INTEGRATION_FAIL_ON_UNREACHABLE}" \
  -e INTEGRATION_REQUIRE_CLASSIFIER="${INTEGRATION_REQUIRE_CLASSIFIER}" \
  -e INTEGRATION_BACKEND_URL="http://host.docker.internal:9000" \
  -e INTEGRATION_CLASSIFIER_URL="http://host.docker.internal:8000" \
  -e INTEGRATION_ADMIN_EMAIL="${INTEGRATION_ADMIN_EMAIL}" \
  -e INTEGRATION_ADMIN_PASSWORD="${INTEGRATION_ADMIN_PASSWORD}" \
  -e INTEGRATION_LOGIN_EMAIL="${INTEGRATION_LOGIN_EMAIL}" \
  -e INTEGRATION_LOGIN_PASSWORD="${INTEGRATION_LOGIN_PASSWORD}" \
  -e INTEGRATION_RAG_PRODUCT_BASE_PATH="/api/v5" \
  python:3.11-slim bash -lc \
    "pip install -r tests/integration/requirements.txt >/dev/null && python -m pytest tests/integration -v --tb=short --ignore=tests/integration/test_tc_postgres_smoke.py" \
  2>&1 | tee "${PYTEST_LOG_PATH}"
pytest_status=${PIPESTATUS[0]}
if [[ "${pytest_status}" != "0" ]]; then
  exit "${pytest_status}"
fi
guard_no_false_green

