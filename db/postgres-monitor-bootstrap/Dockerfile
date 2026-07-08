# Postgres one-shot bootstrap helper for observability:
# creates/ensures the POSTGRES_MONITOR_* role exists before OTEL Collector scrapes.
ARG POSTGRES_MONITOR_BOOTSTRAP_BASE_IMAGE=postgres:16-alpine
FROM ${POSTGRES_MONITOR_BOOTSTRAP_BASE_IMAGE}

# Bake script into image — avoids Docker Desktop WSL file bind-mount failures on ensure.sh.
COPY scripts/ensure-postgres-monitor-role.sh /ensure.sh
RUN chmod +x /ensure.sh
