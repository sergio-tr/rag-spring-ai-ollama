#!/bin/sh
# Substitutes placeholders according to environment variables (defined in observability/.env via Compose).
set -e
B="${OBS_INTERNAL_BACKEND_ACTUATOR:-9000}"
O="${OBS_INTERNAL_OTEL_PROM_EXPORT:-8889}"
P="${OBS_INTERNAL_PROMETHEUS:-9090}"
sed \
  -e "s/__OBS_INTERNAL_BACKEND_ACTUATOR__/${B}/g" \
  -e "s/__OBS_INTERNAL_OTEL_PROM_EXPORT__/${O}/g" \
  -e "s/__OBS_INTERNAL_PROMETHEUS__/${P}/g" \
  /etc/prometheus/prometheus.yml.template > /tmp/prometheus.yml
LISTEN="${OBS_INTERNAL_PROMETHEUS:-9090}"
exec /bin/prometheus \
  --config.file=/tmp/prometheus.yml \
  --storage.tsdb.path=/prometheus \
  --web.enable-lifecycle \
  --web.listen-address=0.0.0.0:${LISTEN}
