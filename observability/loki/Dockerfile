# Logs: Loki. Base image tag from observability/.env (LOKI_BASE_IMAGE).
ARG LOKI_BASE_IMAGE=grafana/loki:2.9.6
FROM ${LOKI_BASE_IMAGE}

# Bake config template into image — avoids Docker Desktop WSL file bind-mount failures.
COPY config.yml.template /config.yml.template
