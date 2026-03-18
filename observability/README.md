# Observability (OTEL, Jaeger, Prometheus, Grafana, Loki/Promtail)

Configuración y Dockerfiles del stack. **Puertos y credenciales relevantes se definen en un solo sitio:** `observability/.env` (plantilla: `.env.example`).

## Crear `observability/.env`

Desde la raíz del repo:

```bash
./scripts/create-env-observability.sh
```

Tras actualizar el repositorio, si aparecen variables nuevas en `.env.example`, cópialas manualmente a tu `.env` o regenera con `--force` (sobrescribe).

## Variables (resumen)

| Grupo | Variables | Uso |
|-------|-----------|-----|
| Imágenes | `OTEL_COLLECTOR_BASE_IMAGE`, `JAEGER_BASE_IMAGE`, `PROMETHEUS_BASE_IMAGE`, `GRAFANA_BASE_IMAGE` | Build-args en Dockerfiles |
| Grafana | `GRAFANA_ADMIN_PASSWORD` | Admin UI |
| Collector | `OTEL_COLLECTOR_LOG_LEVEL` | Verbosidad del exporter `logging` |
| **Red Docker (internos)** | `OBS_INTERNAL_*` | Puertos entre contenedores (backend actuator, OTLP, Prometheus scrape del collector, Prometheus/Grafana/Jaeger UI, Loki, Promtail). Deben ser coherentes entre sí. |
| **Host** | `OTEL_*_PORT`, `JAEGER_*_PORT`, `PROMETHEUS_PORT`, `GRAFANA_PORT`, `LOKI_HOST_PORT`, `PROMTAIL_HOST_PORT`, `NODE_EXPORTER_HOST_PORT`, `CADVISOR_HOST_PORT` | Mapeo `host:contenedor` |
| PostgreSQL (collector) | `OBS_PG_ENDPOINT`, `OBS_PG_EXPORTER_*`, `OBS_PG_DB` | Receiver `postgresql` del collector (alinear con `db/init`) |
| Jaeger (trazas) | `OBS_OTEL_EXPORT_JAEGER_ENDPOINT` | Destino OTLP gRPC del collector (p. ej. `jaeger:4317`) |

`docker/compose.obs.yml`, `compose.logs.yml` y `compose.infra.yml` deben lanzarse con:

```bash
--env-file ../observability/.env
```

(junto al resto de `--env-file` que uses).

## Arranque con Compose

```bash
cd docker
docker compose -f docker-compose.yml -f compose.obs.yml \
  --env-file ../db/.env \
  --env-file ../classifier-service/.env \
  --env-file ../rag-service/.env \
  --env-file ../observability/.env \
  up -d
```

Opcional: `./scripts/set-env.sh` (opciones 2 o 4) hace lo mismo si existen los `.env`.

## URLs en el host (sustituye por tus puertos del `.env`)

| Componente | URL típica |
|------------|------------|
| Grafana | `http://localhost:${GRAFANA_PORT}` |
| Jaeger | `http://localhost:${JAEGER_UI_PORT}` |
| Prometheus | `http://localhost:${PROMETHEUS_PORT}` |

## Cómo se aplican los puertos

- **Compose (`compose.obs.yml`)**: lee `observability/.env` y pasa variables a los servicios (mapeos host, `SERVER_PORT` del backend, `OTEL_EXPORTER_OTLP_ENDPOINT`, entorno del collector, Prometheus, Grafana).
- **OTEL Collector** (`otel-collector/config.yaml`): sustitución `${env:...}` con las variables que inyecta Compose.
- **Prometheus** (`prometheus/prometheus.yml.template`): el entrypoint del contenedor sustituye placeholders y arranca Prometheus con `--web.listen-address` acorde a `OBS_INTERNAL_PROMETHEUS`.
- **Grafana** (`grafana/docker-entrypoint.sh` + `datasources.yml.template`): genera datasources con URLs `http://prometheus:PUERTO`, etc., según el `.env`.
- **Loki / Promtail** (`*.yml.template` + `compose.logs.yml`): `sed` en el arranque con valores del `.env`.

## Layout

| Carpeta | Contenido |
|---------|-----------|
| `grafana/` | Dockerfile + entrypoint; `provisioning/` (dashboards JSON + `datasources.yml.template`) |
| `jaeger/` | Dockerfile |
| `otel-collector/` | Dockerfile; `config.yaml` |
| `prometheus/` | Dockerfile; `prometheus.yml.template`; `docker-entrypoint.sh` |
| `loki/` | `config.yml.template` |
| `promtail/` | `config.yml.template` |

## OTEL en backend y classifier

Con `compose.obs.yml`, backend y classifier reciben `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:<OBS_INTERNAL_OTEL_OTLP_HTTP>` (valor del `.env`).
