#!/usr/bin/env bash
# Local CI parity for .github/workflows/reusable-ci-core.yml job "e2e_fullstack".
#
# Runs:
# - Postgres (pgvector image, same as CI)
# - Postgres bootstrap (extensions + testdb + test-init.sql)
# - rag-service Spring Boot (profile=e2e) on :9000
# - webapp Next.js build + Playwright @fullstack tests (chromium)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/../.." && pwd)"
RAG_SERVICE="${REPO_ROOT}/rag-service"
WEBAPP_DIR="${REPO_ROOT}/webapp"

POSTGRES_CONTAINER="${RAG_CI_POSTGRES_CONTAINER:-rag-ci-pg}"
POSTGRES_IMAGE="${RAG_PLATFORM_POSTGRES_IMAGE:-pgvector/pgvector:0.8.2-pg16-bookworm}"
POSTGRES_PORT="${RAG_LOCAL_POSTGRES_PORT:-5432}"
STOP_AFTER="${RAG_CI_STOP_CONTAINER:-0}"
CI_NETWORK="${RAG_CI_NETWORK:-rag-ci}"
BACKEND_CONTAINER="${RAG_CI_BACKEND_CONTAINER:-rag-ci-backend}"
PROXY_CONTAINER="${RAG_CI_PROXY_CONTAINER:-rag-ci-proxy}"
MAVEN_CACHE_VOLUME="${RAG_MAVEN_CACHE_VOLUME:-rag-m2-cache}"
BACKEND_HOST_PORT="${RAG_CI_BACKEND_HOST_PORT:-9000}"
WEBAPP_HOST_PORT="${RAG_CI_WEBAPP_HOST_PORT:-3000}"
REVERSE_PROXY_HTTPS_PORT="${REVERSE_PROXY_HTTPS_PORT:-8443}"
HOST_GATEWAY_IP="${RAG_CI_HOST_GATEWAY_IP:-}"

# Playwright runs in a Linux container for parity (browser deps included).
PLAYWRIGHT_IMAGE="${RAG_PLAYWRIGHT_IMAGE:-mcr.microsoft.com/playwright:v1.59.1-jammy}"
NPM_CACHE_VOLUME="${RAG_WEBAPP_NPM_CACHE_VOLUME:-rag-webapp-npm-cache}"

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
is_tcp_port_busy() {
  local port="$1"
  if command -v ss >/dev/null 2>&1; then
    ss -ltn "( sport = :${port} )" | tail -n +2 | grep -q .
    return
  fi
  if command -v lsof >/dev/null 2>&1; then
    lsof -nP -iTCP:"${port}" -sTCP:LISTEN >/dev/null 2>&1
    return
  fi
  return 1
}

check_host_port_available() {
  local name="$1"
  local port="$2"
  if is_tcp_port_busy "${port}"; then
    echo "error: ${name} host port ${port} is already in use." >&2
    echo "Choose a free port and rerun, for example:" >&2
    echo "  RAG_CI_WEBAPP_HOST_PORT=3100 RAG_CI_BACKEND_HOST_PORT=9100 $0" >&2
    exit 1
  fi
}

resolve_host_gateway_ip() {
  if [[ -n "${HOST_GATEWAY_IP}" ]]; then
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
  # Docker Desktop on WSL can resolve host-gateway to IPv6 only; nginx then marks upstreams down.
  echo "192.168.65.254"
}

require docker
require curl
require npm

if [[ ! -f "${REPO_ROOT}/.github/local/ci-postgres-extensions.sql" ]]; then
  echo "error: missing .github/local/ci-postgres-extensions.sql" >&2
  exit 1
fi
if [[ ! -f "${RAG_SERVICE}/src/test/resources/test-init.sql" ]]; then
  echo "error: missing rag-service/src/test/resources/test-init.sql" >&2
  exit 1
fi

docker info >/dev/null 2>&1 || { echo "error: Docker is not running." >&2; exit 1; }
check_host_port_available "backend" "${BACKEND_HOST_PORT}"
check_host_port_available "webapp" "${WEBAPP_HOST_PORT}"

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
  docker run -d --name "${BACKEND_CONTAINER}" --network "${CI_NETWORK}" -p "${BACKEND_HOST_PORT}:9000" \
    -v "${REPO_ROOT}:/repo" \
    -v "${MAVEN_CACHE_VOLUME}:/root/.m2" \
    -w /repo/rag-service \
    -e SPRING_DATASOURCE_URL="jdbc:postgresql://${POSTGRES_CONTAINER}:5432/vectordb" \
    -e SPRING_DATASOURCE_USERNAME=postgres \
    -e SPRING_DATASOURCE_PASSWORD=postgres \
    -e RAG_CORS_ALLOWED_ORIGINS="http://127.0.0.1:${WEBAPP_HOST_PORT},http://localhost:${WEBAPP_HOST_PORT},https://127.0.0.1:8443,https://localhost:8443" \
    -e RAG_JWT_SECRET="e2e-ci-jwt-secret-must-be-at-least-32-chars" \
    -e RAG_TEST_USE_TESTCONTAINERS_DATASOURCE=false \
    -e RAG_API_PRODUCT_BASE_PATH=/api/v5 \
    eclipse-temurin:21-jdk bash -lc "./mvnw -B -DskipTests spring-boot:run -Dspring-boot.run.profiles=e2e" \
    >/dev/null
}

wait_for_backend() {
  local backend_health_url="http://127.0.0.1:${BACKEND_HOST_PORT}/actuator/health"
  log "Waiting for backend health: ${backend_health_url}"
  for _ in $(seq 1 90); do
    if curl -fsS "${backend_health_url}" >/dev/null 2>&1; then
      log "Backend healthy."
      return 0
    fi
    sleep 2
  done
  echo "--- backend log (tail) ---" >&2
  docker logs --tail 200 "${BACKEND_CONTAINER}" >&2 || true
  return 1
}

wait_for_admin_login() {
  local admin_login_url="https://127.0.0.1:8443/api/auth/login"
  log "Waiting for e2e admin login to be available."
  for _ in $(seq 1 45); do
    code="$(
      curl -ksS -o /dev/null -w '%{http_code}' \
        -H 'Content-Type: application/json' \
        -d '{"email":"admin@e2e.local","password":"e2e"}' \
        "${admin_login_url}" || true
    )"
    if [[ "${code}" == "200" ]]; then
      log "Admin login OK."
      return 0
    fi
    sleep 2
  done
  log "Admin login not ready; continuing (admin UI test may fail)."
}

start_proxy() {
  log "Starting reverse-proxy container for HTTPS proxy-mode E2E."
  local host_gateway
  host_gateway="$(resolve_host_gateway_ip)"
  docker rm -f "${PROXY_CONTAINER}" >/dev/null 2>&1 || true
  docker build -t rag-reverse-proxy-local "${REPO_ROOT}/reverse-proxy" >/dev/null
  docker run -d --name "${PROXY_CONTAINER}" \
    --add-host="host-gateway.internal:${host_gateway}" \
    -p 8080:80 -p 8443:443 \
    -e BACKEND_HOST=host-gateway.internal \
    -e BACKEND_INTERNAL_PORT="${BACKEND_HOST_PORT}" \
    -e WEBAPP_HOST=host-gateway.internal \
    -e WEBAPP_INTERNAL_PORT="${WEBAPP_HOST_PORT}" \
    -e REVERSE_PROXY_ENFORCE_HTTPS=1 \
    -e API_CLIENT_MAX_BODY_SIZE=50m \
    -e API_PROXY_CONNECT_TIMEOUT=10s \
    -e API_PROXY_SEND_TIMEOUT=180s \
    -e API_PROXY_READ_TIMEOUT=180s \
    rag-reverse-proxy-local >/dev/null
}

wait_for_proxy() {
  log "Waiting for reverse-proxy HTTPS endpoint."
  for _ in $(seq 1 90); do
    if curl -skf https://127.0.0.1:8443/ >/dev/null 2>&1; then
      log "Proxy healthy."
      return 0
    fi
    sleep 2
  done
  docker logs --tail 200 "${PROXY_CONTAINER}" >&2 || true
  return 1
}

WEBAPP_LOG_PATH="${WEBAPP_LOG_PATH:-/tmp/webapp-e2e.log}"
WEBAPP_PID_PATH="${WEBAPP_PID_PATH:-/tmp/webapp-e2e.pid}"
PROXY_LOG_PATH="${PROXY_LOG_PATH:-/tmp/proxy-e2e.log}"

start_webapp() {
  log "Building and starting Next.js webapp for proxy mode."
  rm -f "${WEBAPP_PID_PATH}" "${WEBAPP_LOG_PATH}"
  (
    cd "${WEBAPP_DIR}"
    NEXT_PUBLIC_API_BASE_URL="" NEXT_PUBLIC_RAG_API_PREFIX="/api/v5" npm install --no-audit --no-fund
    NEXT_PUBLIC_API_BASE_URL="" NEXT_PUBLIC_RAG_API_PREFIX="/api/v5" npm run build
    nohup env NEXT_PUBLIC_API_BASE_URL="" NEXT_PUBLIC_RAG_API_PREFIX="/api/v5" npm run start -- -H 0.0.0.0 -p "${WEBAPP_HOST_PORT}" > "${WEBAPP_LOG_PATH}" 2>&1 &
    echo $! > "${WEBAPP_PID_PATH}"
  )
}

wait_for_webapp() {
  local webapp_health_url="http://127.0.0.1:${WEBAPP_HOST_PORT}"
  log "Waiting for webapp health: ${webapp_health_url}"
  for _ in $(seq 1 90); do
    if curl -fsS "${webapp_health_url}" >/dev/null 2>&1; then
      log "Webapp healthy."
      return 0
    fi
    sleep 2
  done
  echo "--- webapp log (tail) ---" >&2
  if [[ -f "${WEBAPP_LOG_PATH}" ]]; then
    tail -n 200 "${WEBAPP_LOG_PATH}" >&2 || true
  fi
  return 1
}

seed_admin() {
  # Not explicitly done in the e2e_fullstack job, but required if tests hit /api/admin/**.
  log "Seeding e2e admin user (best-effort)."
  docker exec -e PGPASSWORD=postgres "${POSTGRES_CONTAINER}" \
    psql -U postgres -d vectordb -v ON_ERROR_STOP=1 -c "
      INSERT INTO users (id, email, password_hash, name, role, created_at, email_verified, email_verified_at)
      VALUES (
        'e2e0ad00-0000-4000-8000-000000000001',
        'admin@e2e.local',
        '{noop}e2e',
        'E2E Admin',
        'ADMIN',
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
    " >/dev/null || true
}

run_playwright_fullstack() {
  log "Running Next.js build + Playwright @fullstack in ${PLAYWRIGHT_IMAGE}."

  docker run --rm \
    --network host \
    -v "${REPO_ROOT}:/repo" \
    -w /repo/webapp \
    -v "${NPM_CACHE_VOLUME}:/root/.npm" \
    -e E2E_ALLOW_INSECURE_COOKIES="true" \
    -e E2E_LOGIN_TIMEOUT_MS="12000" \
    -e E2E_ADMIN_ENABLED="1" \
    -e PLAYWRIGHT_SKIP_WEBSERVER="1" \
    -e PLAYWRIGHT_BASE_URL="https://127.0.0.1:${REVERSE_PROXY_HTTPS_PORT}" \
    -e PLAYWRIGHT_WORKERS="1" \
    -e PLAYWRIGHT_IGNORE_HTTPS_ERRORS="1" \
    -e API_BASE_URL="https://127.0.0.1:${REVERSE_PROXY_HTTPS_PORT}" \
    -e NEXT_PUBLIC_API_BASE_URL="" \
    -e NEXT_PUBLIC_RAG_API_PREFIX="/api/v5" \
    "${PLAYWRIGHT_IMAGE}" bash -lc \
      "npm ci --silent --no-audit --no-fund && npm run build && npm run test:e2e:fullstack:ci"
}

stop_backend() {
  docker rm -f "${BACKEND_CONTAINER}" >/dev/null 2>&1 || true
}

stop_proxy() {
  docker logs "${PROXY_CONTAINER}" > "${PROXY_LOG_PATH}" 2>&1 || true
  docker rm -f "${PROXY_CONTAINER}" >/dev/null 2>&1 || true
}

stop_webapp() {
  if [[ -f "${WEBAPP_PID_PATH}" ]]; then
    kill "$(cat "${WEBAPP_PID_PATH}")" 2>/dev/null || true
    rm -f "${WEBAPP_PID_PATH}"
  fi
}

cleanup() {
  stop_proxy
  stop_webapp
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
start_webapp
wait_for_webapp
start_proxy
wait_for_proxy
seed_admin
wait_for_admin_login
run_playwright_fullstack

