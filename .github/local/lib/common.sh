#!/usr/bin/env bash
# Canonical pinned PostgreSQL image — must match docker-compose, CI services, and verify-pinned-postgres-image.sh.
# shellcheck shell=bash
_LOCAL_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
if [[ -f "${_LOCAL_ROOT}/.github/local/.env.local" ]]; then
  # shellcheck source=/dev/null
  source "${_LOCAL_ROOT}/.github/local/.env.local"
fi
# Image is never overridable via .env.local.
export RAG_PLATFORM_POSTGRES_IMAGE="pgvector/pgvector:0.8.2-pg16-bookworm"

export RAG_CI_POSTGRES_CONTAINER="${RAG_CI_POSTGRES_CONTAINER:-rag-ci-postgres}"
export RAG_LOCAL_POSTGRES_PORT="${POSTGRES_PORT:-5432}"
export RAG_LOCAL_BACKEND_PORT="${BACKEND_PORT:-9000}"
