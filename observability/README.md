# Observability: OpenTelemetry, Jaeger, Prometheus, Grafana

This directory holds configuration and **Dockerfiles** for the observability stack: OpenTelemetry Collector, Jaeger, Prometheus, Grafana. All four services are **built from Dockerfiles** (no pre-built images in compose); base images and ports are in **observability/.env**.

## Create observability/.env

From the repository root:

```bash
./scripts/create-env-observability.sh
```

Or copy `observability/.env.example` to `observability/.env`. Use `--force` to overwrite. In the `.env` you can change **base images** (`OTEL_COLLECTOR_BASE_IMAGE`, `JAEGER_BASE_IMAGE`, `PROMETHEUS_BASE_IMAGE`, `GRAFANA_BASE_IMAGE`), Grafana password (`GRAFANA_ADMIN_PASSWORD`), and **ports** (`OTEL_GRPC_PORT`, `OTEL_HTTP_PORT`, `OTEL_PROMETHEUS_SCRAPE_PORT`, `JAEGER_UI_PORT`, `PROMETHEUS_PORT`, `GRAFANA_PORT`).

## Running the stack with observability

From the repository root (create env files first with `scripts/create-env-all.sh` or the per-component scripts):

```bash
cd docker
docker compose -f docker-compose.yml -f compose.obs.yml --env-file ../db/.env --env-file ../classifier-service/.env --env-file ../rag-service/.env --env-file ../observability/.env up -d
```

Or use `./scripts/set-env.sh` and choose option 2 or 4. If an env file is missing, compose uses defaults from the compose file.

Volume paths in `compose.obs.yml` are relative to the `docker/` directory (`../observability/`).

## UI URLs

| Component   | URL                     | Use                                      |
|-------------|-------------------------|------------------------------------------|
| **Grafana** | http://localhost:3000   | Dashboards and **traces** (default user/password: `admin`/`admin`) |
| **Jaeger**  | http://localhost:16686  | Distributed traces UI (backend + classifier); also available inside Grafana via Jaeger datasource |
| **Prometheus** | http://localhost:9090 | Metric queries                    |

### Viewing traces in Grafana

Grafana is provisioned with a **Jaeger** datasource pointing at the Jaeger instance. To view traces:

1. Open **Explore** (compass icon in the left sidebar), select the **Jaeger** datasource.
2. Choose a **Service** (e.g. `rag-backend`, `classifier-service`) and click **Run query** to see traces for the selected time range.
3. Or open the **Traces (Jaeger)** or **RAG Overview** dashboard; the trace panel lets you run a query there.

Metrics from the backend (Prometheus scrape) and from the OTEL collector (metrics sent via OTLP by backend and classifier) are available in the **Prometheus** datasource. Use the **RAG Overview** and **Classifier & OTEL metrics** dashboards.

## OTEL environment variables (backend and classifier)

So that **rag-backend** (Spring) and **classifier-service** (Python) send traces and metrics to the collector, `compose.obs.yml` sets:

| Variable                    | Typical value                 | Description                          |
|----------------------------|------------------------------|--------------------------------------|
| `OTEL_EXPORTER_OTLP_ENDPOINT` | `http://otel-collector:4318` | Collector base URL (OTLP HTTP)   |
| `OTEL_SERVICE_NAME`        | `rag-backend` / `classifier-service` | Service name in traces   |
| `OTEL_METRICS_EXPORTER`    | `otlp`                       | Export metrics via OTLP           |
| `OTEL_TRACES_EXPORTER`     | `otlp`                       | Export traces via OTLP            |

The collector listens for OTLP HTTP on port **4318** and re-exports metrics on **8889** for Prometheus to scrape.

## Layout

Each component has its own folder with a `Dockerfile` and any config it needs:

| Folder | Contents |
|--------|----------|
| **grafana/** | `Dockerfile`; `provisioning/` (dashboards and datasources) |
| **jaeger/** | `Dockerfile` |
| **otel-collector/** | `Dockerfile`; `config.yaml` — pipelines for *traces* (OTLP → batch → logging + Jaeger) and *metrics* (OTLP + PostgreSQL → batch → logging + Prometheus exporter on `:8889`) |
| **prometheus/** | `Dockerfile`; `prometheus.yml` — scrape jobs (backend `/actuator/prometheus`, otel-collector `:8889`) |

Compose builds each service from its folder (`context: ../observability/<folder>`, `dockerfile: Dockerfile`). For details see `docker/compose.obs.yml`.
