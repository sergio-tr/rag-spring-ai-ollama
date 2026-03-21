# Stack integration tests

These tests validate **HTTP integration** between components while services are running (typically via Docker Compose). **Observability and cross-service behaviour** are covered here so manual checks in docs are not the main source of truth.

## Location

- Code: `tests/integration/`
- Dependencies: `tests/integration/requirements.txt` (pytest + httpx)

## When to run

1. Start the stack (with observability for full coverage):

   ```bash
   cd docker
   docker compose -f docker-compose.yml -f compose.obs.yml \
     --env-file ../db/.env \
     --env-file ../classifier-service/.env \
     --env-file ../rag-service/.env \
     --env-file ../observability/.env \
     up -d
   ```

2. Ensure **`rag-service/.env`** sets **`OLLAMA_BASE_URL`** to Ollama **as seen from the backend container**:

   - `http://host.docker.internal:11434` (Docker Desktop on Windows/macOS), or
   - `http://ollama:11434` if you use `compose.ollama.yml` or `compose.ollama-gpu.yml`.

3. Run tests:

   ```bash
   pip install -r tests/integration/requirements.txt
   pytest tests/integration -v
   ```

   Or:

   ```bash
   ./scripts/run-integration-tests.sh
   ```

## Observability tests (`INTEGRATION_CHECK_OBS`)

Observability checks (OTEL collector, Jaeger, Prometheus, Grafana, OTLP HTTP port, trace exports) live in **`TestObservabilityStack`**.

| Mode | Behaviour |
|------|-----------|
| **`auto` (default)** | If the OTEL collector metrics URL (`INTEGRATION_OTEL_METRICS_URL`, default `http://127.0.0.1:8889/metrics`) responds, observability tests **run**. If the stack is not up, those tests are **skipped** (not failed). |
| **`1` / `true` / `require`** | Observability tests **must** run; if the stack is unreachable, the run **fails** (use in CI when Compose with `compose.obs.yml` is guaranteed). |
| **`0` / `false` / `skip`** | Observability tests are **always skipped** (only core classifier/backend checks run). |

Examples:

```bash
# Default: auto-detect observability stack
pytest tests/integration -v

# CI with observability stack required
INTEGRATION_CHECK_OBS=1 pytest tests/integration -v

# Fast run without Jaeger/Prometheus (only classifier + backend)
INTEGRATION_CHECK_OBS=0 pytest tests/integration -v
```

## Environment variables (URLs)

| Variable | Default host URL |
|----------|-------------------|
| `INTEGRATION_CLASSIFIER_URL` | `http://127.0.0.1:8000` |
| `INTEGRATION_BACKEND_URL` | `http://127.0.0.1:9000` |
| `INTEGRATION_PROMETHEUS_URL` | `http://127.0.0.1:9090` |
| `INTEGRATION_GRAFANA_URL` | `http://127.0.0.1:3000` |
| `INTEGRATION_JAEGER_URL` | `http://127.0.0.1:16686` |
| `INTEGRATION_OTEL_METRICS_URL` | `http://127.0.0.1:8889/metrics` |
| `INTEGRATION_OTLP_HTTP_URL` | `http://127.0.0.1:4318` |

| Variable | Purpose |
|----------|---------|
| `INTEGRATION_EXPECT_RAG_SERVICE_NAME` | Jaeger service name for the Java backend (default `rag-backend`). |
| `INTEGRATION_EXPECT_CLASSIFIER_SERVICE_NAME` | Jaeger service name for the Python classifier (default `classifier-service`). |

## What is checked

### Core stack (always)

- **Classifier**: `GET /health`, `GET /models`, `POST /classify` (including **400** on empty query), **200** classify when model is loaded.
- **Backend**: `GET /actuator/health` (`UP`), `GET /actuator/info` if exposed, `GET /actuator/prometheus` exposes Micrometer metrics, `GET /api/v4/query` (**200**).
- **Cross-service**: classifier and backend reachable in sequence; RAG query triggers backend → classifier path.

### Observability (when stack reachable or `INTEGRATION_CHECK_OBS=1`)

- **Prometheus**: `/-/healthy`, instant query `up`, **`up{job="backend"}==1`** after scrape (with retries).
- **Grafana**: `/api/health`.
- **Jaeger**: UI reachable; **service list includes `rag-backend`** after a query; **includes `classifier-service`** after `POST /classify` (when model loaded).
- **OTEL collector**: self-metrics on `:8889/metrics`; OTLP HTTP port **4318** accepts connections (no connection refused).
- **Metrics pipeline**: Prometheus query finds `rag_query_generate_seconds_count` or `http_server_requests_seconds_count` for the backend job.
- **Backend scrape**: `/actuator/prometheus` contains `rag_*` or `http_server_*`.

## Notes

- These tests do not replace unit tests or business E2E tests.
- Full RAG quality requires Ollama models aligned with `SPRING_AI_OLLAMA_*` in the backend.
- **`POST /classify`** (classifier) tests that need the model are **skipped** if `/health` reports `model != loaded`.
- Jaeger service names depend on `OTEL_SERVICE_NAME` in Compose (`compose.obs.yml`).
