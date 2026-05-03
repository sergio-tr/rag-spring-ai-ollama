#!/bin/sh
# Runs from the postgres-monitor-bootstrap Compose service (TCP to postgres:5432).
# Idempotent: creates POSTGRES_MONITOR_USER + GRANT pg_monitor, or ALTER PASSWORD to match db/.env.
# Needed for existing data dirs where docker-entrypoint-initdb.d never re-runs (see db/init/01-monitor-user.sh).
set -eu
MON_USER="${POSTGRES_MONITOR_USER:-postgres_exporter}"
MON_PASS="${POSTGRES_MONITOR_PASSWORD:-postgres_exporter}"
[ -n "$MON_USER" ] || MON_USER="postgres_exporter"
[ -n "$MON_PASS" ] || MON_PASS="postgres_exporter"

if ! echo "$MON_USER" | grep -Eq '^[a-zA-Z_][a-zA-Z0-9_]*$'; then
    echo "Invalid POSTGRES_MONITOR_USER (expected SQL identifier): ${MON_USER}" >&2
    exit 1
fi

pass_escaped=$(printf '%s' "$MON_PASS" | sed "s/'/''/g")
export PGPASSWORD="${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}"

HOST="${POSTGRES_HOST:-postgres}"
PORT="${POSTGRES_PORT:-5432}"

exists=$(psql -v ON_ERROR_STOP=1 -h "$HOST" -p "$PORT" -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -tAc \
    "SELECT 1 FROM pg_roles WHERE rolname = '${MON_USER}'" | tr -d '[:space:]')

if [ "$exists" = "1" ]; then
    psql -v ON_ERROR_STOP=1 -h "$HOST" -p "$PORT" -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
        -c "ALTER ROLE \"${MON_USER}\" PASSWORD '${pass_escaped}';"
else
    psql -v ON_ERROR_STOP=1 -h "$HOST" -p "$PORT" -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
        -c "CREATE ROLE \"${MON_USER}\" WITH LOGIN PASSWORD '${pass_escaped}';"
fi

psql -v ON_ERROR_STOP=1 -h "$HOST" -p "$PORT" -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" \
    -c "GRANT pg_monitor TO \"${MON_USER}\";"

echo "Monitoring role \"${MON_USER}\" is ready for OTEL postgresql receiver."
