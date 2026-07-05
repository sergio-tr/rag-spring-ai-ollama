# Logs: Promtail. Base image tag from observability/.env (PROMTAIL_BASE_IMAGE).
ARG PROMTAIL_BASE_IMAGE=grafana/promtail:2.9.6
FROM ${PROMTAIL_BASE_IMAGE}

# Bake config template into image — avoids Docker Desktop WSL file bind-mount failures.
COPY config.yml.template /config.yml.template
