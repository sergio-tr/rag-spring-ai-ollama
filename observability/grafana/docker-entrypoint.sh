#!/bin/sh
# Mount: observability/grafana/provisioning → /mnt/repo-provisioning (read-only).
# Generates /tmp/gfprov with replaced datasources and copied dashboards.
set -e
PROM="${OBS_INTERNAL_PROMETHEUS:-9090}"
JAEGER_UI="${OBS_INTERNAL_JAEGER_UI:-16686}"
LOKI="${OBS_INTERNAL_LOKI_HTTP:-3100}"
GFPROV=/tmp/gfprov
rm -rf "$GFPROV"
mkdir -p "$GFPROV/datasources" "$GFPROV/dashboards/json"
if [ -d /mnt/repo-provisioning/dashboards/json ]; then
  cp -r /mnt/repo-provisioning/dashboards/json/. "$GFPROV/dashboards/json/"
fi
if [ -f /mnt/repo-provisioning/dashboards/dashboards.yml ]; then
  sed 's|path: /etc/grafana/provisioning/dashboards/json|path: /tmp/gfprov/dashboards/json|g' \
    /mnt/repo-provisioning/dashboards/dashboards.yml > "$GFPROV/dashboards/dashboards.yml"
fi
sed \
  -e "s/__OBS_INTERNAL_PROMETHEUS__/${PROM}/g" \
  -e "s/__OBS_INTERNAL_JAEGER_UI__/${JAEGER_UI}/g" \
  -e "s/__OBS_INTERNAL_LOKI_HTTP__/${LOKI}/g" \
  /mnt/repo-provisioning/datasources/datasources.yml.template > "$GFPROV/datasources/datasources.yml"
export GF_PATHS_PROVISIONING="$GFPROV"
exec /run.sh
