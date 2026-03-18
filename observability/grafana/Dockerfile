# Grafana. Base image from observability/.env (GRAFANA_BASE_IMAGE).
# Provisioning at runtime: datasources with ports from .env (mount /mnt/repo-provisioning).
ARG BASE_IMAGE=grafana/grafana:11.2.0
FROM ${BASE_IMAGE}
USER root
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh
USER grafana
ENTRYPOINT ["/docker-entrypoint.sh"]
