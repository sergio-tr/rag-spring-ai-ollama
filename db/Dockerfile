# Thin wrapper around the official pgvector image so Compose can use `build:` while the pin stays in db/.env.
# Must stay aligned with .github/ci/postgres-service-image.env and verify-pinned-postgres-image.sh.
ARG POSTGRES_BASE_IMAGE=pgvector/pgvector:0.8.2-pg16-bookworm
FROM ${POSTGRES_BASE_IMAGE}
