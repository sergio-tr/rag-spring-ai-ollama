#!/usr/bin/env bash
set -euo pipefail

if [ $# -lt 1 ]; then
  echo "Usage: $0 <backup-file.sql>"
  exit 1
fi

BACKUP_FILE="$1"
DB_CONTAINER_NAME="${DB_CONTAINER_NAME:-postgres}"

echo "Restoring database from '${BACKUP_FILE}' into container '${DB_CONTAINER_NAME}'..."
docker exec -i "${DB_CONTAINER_NAME}" psql -U "${POSTGRES_USER:-postgres}" "${POSTGRES_DB:-vectordb}" < "${BACKUP_FILE}"
echo "Restore completed."

