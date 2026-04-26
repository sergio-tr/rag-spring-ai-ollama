# Observability (OTEL, Jaeger, Prometheus, Grafana, Loki/Promtail)

Stack configuration and Dockerfiles. **Ports and credentials are defined in one place:** `observability/.env` (template: `.env.example`).

**See also:** [RUNBOOK.md](RUNBOOK.md) (traces, classifier down, readiness). [Grafana observability guide](../docs/operations/grafana-observability-guide.md) — dashboards, traces, and operator walkthrough aligned with this stack.

**Target architecture (frozen model):** [Platform & Ops](../docs/architecture/target-architecture.md).

## Production / VM (where to read what)

**Rule:** Observability **procedures, ports, and dashboards** stay in **this file** and the linked Grafana/Jaeger/Loki guide. **Deploy, SSH, GitHub Actions gates, and VM env layout** belong under **`docs/operations/`** — do not duplicate them here.

| Topic | Canonical doc |
| --- | --- |
| VM deploy, `compose.prod.yml`, rollback, `deploy.yml` secrets | [Runbook — Docker VM](../docs/operations/runbook-docker-vm.md), [Deploy workflow audit](../docs/operations/deploy-workflow-audit.md) |
| Optional observability on the same host | This README (Compose overlays: `compose.obs.yml`, `compose.prod-obs.yml` if used) + [docker/README.md](../docker/README.md) |
| Operator walkthrough (metrics → traces → logs) | [Grafana / Jaeger / Loki operator guide](../docs/operations/grafana-observability-guide.md) |

**Minimum post-deploy check (obs):** With `docker,infra` and collector reachable, issue a RAG request and confirm a trace in Jaeger and movement on the RAG Overview dashboard — see **Telemetry validation checklist** below.

## Create `observability/.env`

From the repo root:

```bash
./docker/scripts/create-env-observability.sh
```

After pulling updates, if `.env.example` gains new variables, copy them manually into `.env` or regenerate with `--force` (overwrites).

## Variables (summary)

| Group | Variables | Purpose |
| --- | --- | --- |
| Images | `OTEL_COLLECTOR_BASE_IMAGE`, `JAEGER_BASE_IMAGE`, `PROMETHEUS_BASE_IMAGE`, `GRAFANA_BASE_IMAGE` | Docker build-args |
| Grafana | `GRAFANA_ADMIN_PASSWORD` | Admin UI |
| Collector | `OTEL_COLLECTOR_LOG_LEVEL` | Verbosity of the `logging` exporter |
| **Docker network (internal)** | `OBS_INTERNAL_*` | Ports between containers (backend actuator, OTLP, Prometheus scrape of the collector, Prometheus/Grafana/Jaeger UI, Loki, Promtail). Must stay consistent. |
| **Host** | `OTEL_*_PORT`, `JAEGER_*_PORT`, `PROMETHEUS_PORT`, `GRAFANA_PORT`, `LOKI_HOST_PORT`, `PROMTAIL_HOST_PORT`, `NODE_EXPORTER_HOST_PORT`, `CADVISOR_HOST_PORT` | Host:container port mapping |
| PostgreSQL (collector) | `OBS_PG_ENDPOINT`, `OBS_PG_EXPORTER_*`, `OBS_PG_DB` | Collector `postgresql` receiver (align with `db/init`) |
| Jaeger (traces) | `OBS_OTEL_EXPORT_JAEGER_ENDPOINT` | Collector OTLP gRPC destination (e.g. `jaeger:4317`) |

`docker/compose.obs.yml` plus **`docker-compose.yml` profiles** (`observability`, `logs`, `infra`, `cadvisor`) should be run with:

```bash
--env-file ../observability/.env
```

(along with any other `--env-file` you use).

## Start with Compose

```bash
cd docker
docker compose -f docker-compose.yml -f compose.obs.yml \
  --env-file ../db/.env \
  --env-file ../classifier-service/.env \
  --env-file ../rag-service/.env \
  --env-file ../observability/.env \
  up -d
```

Optional: `./docker/scripts/set-env.sh` or `./docker/scripts/up.sh dev --env obs` / `./docker/scripts/up.sh prod --obs` to create `observability/.env` and start the observability stack.

## URLs on the host (replace with your `.env` ports)

| Component | Typical URL |
| --- | --- |
| Grafana | `http://localhost:${GRAFANA_PORT}` |
| Jaeger | `http://localhost:${JAEGER_UI_PORT}` |
| Prometheus | `http://localhost:${PROMETHEUS_PORT}` |

## How ports are applied

- **Compose (`compose.obs.yml`)**: reads `observability/.env` and passes variables to services (host mappings, backend `SERVER_PORT`, `OTEL_EXPORTER_OTLP_ENDPOINT`, collector env, Prometheus, Grafana).
- **OTEL Collector** (`otel-collector/config.yaml`): `${env:...}` substitution from Compose-injected variables.
- **Prometheus** (`prometheus/prometheus.yml.template`): entrypoint replaces placeholders and starts Prometheus with `--web.listen-address` matching `OBS_INTERNAL_PROMETHEUS`.
- **Grafana** (`grafana/docker-entrypoint.sh` + `datasources.yml.template`): generates datasources with URLs like `http://prometheus:PORT` from `.env`.
- **Loki / Promtail** (`*.yml.template` + profile **`logs`** in `docker-compose.yml`): `sed` at startup with `.env` values.

## Layout

| Folder | Contents |
| --- | --- |
| `grafana/` | Dockerfile + entrypoint; `provisioning/` (JSON dashboards + `datasources.yml.template`) |
| `jaeger/` | Dockerfile |
| `otel-collector/` | Dockerfile; `config.yaml` |
| `prometheus/` | Dockerfile; `prometheus.yml.template`; `docker-entrypoint.sh` |
| `loki/` | `config.yml.template` |
| `promtail/` | `config.yml.template` |

## OTEL in backend and classifier

With `compose.obs.yml`, backend and classifier receive `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:<OBS_INTERNAL_OTEL_OTLP_HTTP>` (from `.env`).

**Spring Boot backend:** packaged service uses `SPRING_PROFILES_ACTIVE=docker,infra` (see `docker/compose.obs.yml`). Profile **`infra`** (in `rag-service/.../application-infra.properties`) turns OTLP export on and sets collector URLs; profile **`docker`** alone keeps OTLP off. **`backend-dev`** with observability uses `dev,infra` (`compose.rag-dev-obs.yml`).

## Conventions (spans, metrics, OTLP)

### 1. Spans

- **Service names**
  - Backend (Spring): `rag-backend`
  - Classifier (Python): `classifier-service`

- **General rules**
  - Domain span names use the `rag.` prefix:
    - `rag.query.generate`
    - `rag.query.classify`
    - `rag.query.expand`
    - `rag.documents.load`
    - `rag.documents.search`
    - `rag.evaluation.run`
  - Technical spans (HTTP server/client) follow Spring/OTEL defaults but should link to domain spans.

- **Recommended attributes on domain spans**
  - `rag.query.id` (when present)
  - `rag.query.type` (`QueryType` value)
  - `rag.top_k`
  - `rag.model.chat` (chat LLM)
  - `rag.model.embedding` (embedding model)
  - `rag.dataset.id` (when using a specific dataset)
  - `rag.evaluation.id` (when applicable)

### 2. Metrics

- **Main timers/histograms (Prometheus)**
  - `rag_query_generate_seconds` (histogram):
    - labels: `method`, `status`
    - typical dashboards:
      - `rate(rag_query_generate_seconds_count{job="backend"}[5m])`
      - `histogram_quantile(0.95, rate(rag_query_generate_seconds_bucket{job="backend"}[5m]))`
  - `rag_classifier_calls_total` (counter):
    - labels: `status` (`success` when a query type string is returned, `null_result` when the client yields null/blank — bounded cardinality, **no per-model UUID label**)
  - `rag_retrieval_documents_total` (counter):
    - labels: `operation` (`retrieve` / `retrieveWithMetadataFilters` / `createContext`), `bucket` (`0`, `1_4`, `5_19`, `20_plus`) — document count **bands**, not raw counts as label values
  - Generic HTTP metrics:
    - `http_server_requests_seconds_*` (Spring Boot)

- **Label cardinality (do / do not)**
  - **Do** use bounded enums: `operation`, `bucket`, `status`, `method`, coarse `status` codes.
  - **Do not** use raw user text, per-request UUIDs, `queryId`, or project/document IDs as Prometheus label values on application counters/histograms.

- **Dashboard relationship**
  - `RAG Overview` dashboard:
    - "RAG query rate": `rag_query_generate_seconds_*`
    - "RAG query duration": `histogram_quantile` on `rag_query_generate_seconds_bucket`
    - "Classifier calls": `rag_classifier_calls_total`
    - "Retrieval document count (bucketed)": `rag_retrieval_documents_total`
    - "HTTP request rate": `http_server_requests_seconds_count`

### 3. Collector and Prometheus

- **OTLP HTTP (Spring Boot backend)**
  - `OTEL_EXPORTER_OTLP_ENDPOINT` is the **base** URL (`http://otel-collector:4318` in Compose).
  - Spring `management.otlp.tracing.endpoint` must include the **full HTTP path** for traces: `.../v1/traces` (OpenTelemetry Java `OtlpHttpSpanExporter` requirement).
  - Metrics use `management.otlp.metrics.export.url` with path `.../v1/metrics`.
  - The Python classifier builds `.../v1/traces` explicitly in `app/telemetry.py`.

- **Collector (`observability/otel-collector/config.yaml`)**
  - Receivers:
    - `otlp` (backend and classifier send to `OTEL_EXPORTER_OTLP_ENDPOINT`)
    - `postgresql` (metrics for `vectordb` with user `postgres_exporter`)
  - Pipelines:
    - `traces`: `otlp` → `batch` → `logging`, `otlp/jaeger`
    - `metrics`: `otlp`, `postgresql` → `batch` → `logging`, `prometheus`

- **Prometheus (`observability/prometheus/prometheus.yml.template` + ports from `observability/.env`)**
  - Job `backend`:
    - scrapes `backend:9000/actuator/prometheus`
  - Job `otel-collector`:
    - scrapes `otel-collector:8889`

### 4. Quick manual checklist

After issuing authenticated product traffic (e.g. `GET {product}/config/schema`):

1. **Traces**
   - In Grafana (Jaeger datasource) or Jaeger UI:
     - See `rag-backend` and `classifier-service` spans.
     - Check attributes: `rag.query.type`, `rag.top_k`, `rag.model.chat`, `rag.model.embedding`.
2. **Metrics**
   - In Grafana (Prometheus):
     - "RAG query rate" series move with traffic.
     - "RAG query duration" p50/p99 update.
     - "Classifier calls" success/null_result counters move; retrieval bucket counters move when retrieval runs.
3. **DB / collector**
   - Postgres dashboard: metrics from the `postgresql` receiver for `vectordb`.

## Telemetry validation checklist

**Goal:** manually verify that traces and metrics follow the `rag.*` conventions and change when you run RAG queries.

### 0. Preparation

1. Start the stack with observability:
   - `./tests/e2e/e2e-technical-compose.sh --obs --keep`
2. Generate traces/metrics with stable product endpoints (JWT):
   - Login: `curl -sf -X POST "http://localhost:${BACKEND_PORT:-9000}/api/auth/login" -H "Content-Type: application/json" -d '{"email":"dev@local.test","password":"dev"}'`
   - Then: `curl -sf -H "Authorization: Bearer <token>" "http://localhost:${BACKEND_PORT:-9000}/api/v5/config/schema"`

> Note: the response may not be a "perfect" RAG answer if Ollama/data are missing; the point is to generate telemetry for the pipeline.

### 1. Jaeger (traces)

1. Open Jaeger: `http://localhost:${JAEGER_UI_PORT:-16686}`
2. Find traces for service `rag-backend`:
   - Service `rag-backend` should appear.
   - Open a trace produced after the `curl` above.
3. Inside the trace, check domain span names and attributes:
   - `rag.query.classify` (classifier in the pipeline) — expect `rag.query.type`
   - `rag.query.expand` (if expansion is on) — expect `rag.query.expanded`
   - `rag.documents.search` — expect `rag.top_k`, `rag.docs.count`
   - `rag.documents.load`
   - `rag.evaluation.run` (if you hit an evaluation endpoint) — expect `rag.evaluation.id` when applicable
4. Conventions:
   - Domain spans use the `rag.` prefix.
   - Relevant attributes use the `rag.*` namespace.

### 2. Grafana (metrics)

1. Open Grafana: `http://localhost:${GRAFANA_PORT:-3000}`
2. Open the `RAG Overview` dashboard:
   - Panels for RAG query duration/rate and classifier calls.
3. Run 2–3 RAG queries and verify:
   - Panel "RAG query duration (backend)" updates (p50/p95/p99).
   - Panel "Classifier calls (backend)" and "Retrieval document count" show traffic after the `curl`.
4. Cross-check Prometheus (optional):
   - `rate(rag_classifier_calls_total{job="backend"}[5m])`
   - `rate(rag_query_generate_seconds_count{job="backend"}[5m])`

### 3. Quick Prometheus check (optional)

1. Open Prometheus: `http://localhost:${PROMETHEUS_PORT:-9090}`
2. Example queries:
   - `rate(rag_classifier_calls_total{job="backend"}[5m])`
   - `rate(rag_query_generate_seconds_count{job="backend"}[5m])`
3. Expect series to exist and move after RAG traffic.

### 4. Pass / fail

Mark as **OK** if:

- Jaeger shows `rag.*` spans with expected attributes (at least `rag.query.classify` + `rag.query.type`, and `rag.documents.search` with `rag.top_k` / `rag.docs.count` when applicable).
- Grafana `RAG Overview` updates query traffic and classifier activity after a RAG request.

### 5. Cleanup

When finished:

- `./tests/e2e/e2e-technical-compose.sh` without `--keep`, or `docker compose ... down` with the same compose files you used.
