#!/usr/bin/env bash
set -euo pipefail

DB_CONTAINER_NAME="${DB_CONTAINER_NAME:-postgres}"
OUTPUT_DIR="${OUTPUT_DIR:-backups}"
TIMESTAMP="$(date +'%Y%m%d-%H%M%S')"
FILENAME="${OUTPUT_DIR}/db-backup-${TIMESTAMP}.sql"

mkdir -p "${OUTPUT_DIR}"

echo "Creating database backup from container '${DB_CONTAINER_NAME}' into '${FILENAME}'..."
docker exec "${DB_CONTAINER_NAME}" pg_dump -U "${POSTGRES_USER:-postgres}" "${POSTGRES_DB:-vectordb}" > "${FILENAME}"
echo "Backup completed."

