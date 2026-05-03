#!/usr/bin/env bash
# Creates or updates the monitoring role for the OpenTelemetry postgresql receiver (observability/otel-collector).
# Uses POSTGRES_MONITOR_* from db/.env (same values as OBS_PG_EXPORTER_* in observability/.env).
# Runs after 00-extensions.sql (which ensures postgres_exporter exists with the default password).
#
# Note: psql :'var substitution does not apply inside dollar-quoted $$ bodies, so we use -tAc + separate DDL.
set -euo pipefail
MON_USER="${POSTGRES_MONITOR_USER:-postgres_exporter}"
MON_PASS="${POSTGRES_MONITOR_PASSWORD:-postgres_exporter}"
if [[ -z "${MON_USER}" ]]; then
    MON_USER="postgres_exporter"
fi
if [[ -z "${MON_PASS}" ]]; then
    MON_PASS="postgres_exporter"
fi

if ! [[ "${MON_USER}" =~ ^[a-zA-Z_][a-zA-Z0-9_]*$ ]]; then
    echo "Invalid POSTGRES_MONITOR_USER (expected SQL identifier): ${MON_USER}" >&2
    exit 1
fi

pass_escaped="${MON_PASS//\'/\'\'}"

exists=$(psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" -tAc \
    "SELECT 1 FROM pg_roles WHERE rolname = '${MON_USER}'" | tr -d '[:space:]')

if [ "${exists}" = "1" ]; then
    psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" \
        -c "ALTER ROLE \"${MON_USER}\" PASSWORD '${pass_escaped}';"
else
    psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" \
        -c "CREATE ROLE \"${MON_USER}\" WITH LOGIN PASSWORD '${pass_escaped}';"
fi

psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" \
    -c "GRANT pg_monitor TO \"${MON_USER}\";"
