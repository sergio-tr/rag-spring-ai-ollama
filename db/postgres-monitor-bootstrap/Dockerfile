# Postgres one-shot bootstrap helper for observability:
# creates/ensures the POSTGRES_MONITOR_* role exists before OTEL Collector scrapes.
ARG POSTGRES_MONITOR_BOOTSTRAP_BASE_IMAGE=postgres:16-alpine
FROM ${POSTGRES_MONITOR_BOOTSTRAP_BASE_IMAGE}

# No additional dependencies required; the base image provides /bin/sh and psql tooling.
