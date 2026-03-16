# Observability: OpenTelemetry, Jaeger, Prometheus, Grafana

This directory holds configuration for the **OpenTelemetry Collector** and **Prometheus**; the observability stack (Jaeger, Grafana) is defined in Docker. Image versions and options (Grafana password, ports) are parameterized in **observability/.env**.

## Create observability/.env

From the repository root:

```bash
./scripts/create-env-observability.sh
```

Or copy `observability/.env.example` to `observability/.env`. Use `--force` to overwrite. In the `.env` you can change image versions (`OTEL_COLLECTOR_IMAGE`, `JAEGER_IMAGE`, `PROMETHEUS_IMAGE`, `GRAFANA_IMAGE`), Grafana password, and ports.

## Running the stack with observability

From the repository root (create env files first with `scripts/create-env-all.sh` or the per-component scripts):

```bash
cd docker
docker compose -f docker-compose.yml -f compose.obs.yml --env-file ../db/.env --env-file ../observability/.env up -d
```

If you rely on default values in the compose file for observability, you can omit `../observability/.env`; image tags and ports will use the defaults in `compose.obs.yml`.

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

## Components

- **otel-collector-config.yaml**: Pipelines for *traces* (OTLP → batch → logging + Jaeger) and *metrics* (OTLP → batch → logging + Prometheus exporter on `:8889`).
- **prometheus.yml**: Scrape jobs for Prometheus (backend `/actuator/prometheus`, otel-collector `:8889`).

For compose details (images, volumes, networks), see `docker/compose.obs.yml`.
