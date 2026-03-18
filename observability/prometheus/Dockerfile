# Prometheus. Base image from observability/.env (PROMETHEUS_BASE_IMAGE).
ARG BASE_IMAGE=prom/prometheus:v2.52.0
FROM ${BASE_IMAGE}
USER root
COPY prometheus.yml.template /etc/prometheus/prometheus.yml.template
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh
USER nobody
ENTRYPOINT ["/docker-entrypoint.sh"]
