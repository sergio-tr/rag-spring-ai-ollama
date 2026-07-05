#!/usr/bin/env bash
# Canonical pinned PostgreSQL image - must match .github/ci/postgres-service-image.env, docker-compose, and CI services.
# shellcheck shell=bash
_LOCAL_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
if [[ -f "${_LOCAL_ROOT}/.github/local/.env.local" ]]; then
  # shellcheck source=/dev/null
  source "${_LOCAL_ROOT}/.github/local/.env.local"
fi
# Image is never overridable via .env.local.
export RAG_PLATFORM_POSTGRES_IMAGE="pgvector/pgvector:0.8.2-pg16-bookworm"

export RAG_CI_POSTGRES_CONTAINER="${RAG_CI_POSTGRES_CONTAINER:-rag-ci-postgres}"
export RAG_LOCAL_BACKEND_PORT="${BACKEND_PORT:-9000}"

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

resolve_local_postgres_port() {
  if [[ -n "${RAG_LOCAL_POSTGRES_PORT:-}" ]]; then
    echo "${RAG_LOCAL_POSTGRES_PORT}"
    return
  fi
  local requested="${POSTGRES_PORT:-5432}"
  if ! is_tcp_port_busy "${requested}"; then
    echo "${requested}"
    return
  fi
  local candidate
  for candidate in 5433 5434 5435 5436 5437; do
    if ! is_tcp_port_busy "${candidate}"; then
      echo "[common] host port ${requested} busy; using ${candidate} for ${RAG_CI_POSTGRES_CONTAINER}" >&2
      echo "${candidate}"
      return
    fi
  done
  echo "${requested}"
}

export RAG_LOCAL_POSTGRES_PORT="$(resolve_local_postgres_port)"
