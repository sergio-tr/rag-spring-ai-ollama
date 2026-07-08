# Stack integration tests

These tests validate **HTTP integration** between components while services are running (typically via Docker Compose). **Observability and cross-service behaviour** are covered here so manual checks in docs are not the main source of truth.

**Coexistence with Playwright API (`webapp/e2e/api/`):** pytest remains the **deep** contract suite (schemas, lab async jobs, optional classifier/obs). **Playwright API** tests are the canonical **smoke** layer (auth, product paths, serial smoke chain, `GET /projects` with JWT) without duplicating pytest’s field-level assertions. See [`webapp/e2e/api/README.md`](../../webapp/e2e/api/README.md).

## Path A vs Path B (database supply)

| Path | Where | Postgres | pytest |
| --- | --- | --- | --- |
| **A (default, CI)** | GitHub Actions + local Compose | **GHA service** or Compose `postgres` | **HTTP only** (`httpx`). Spring uses `SPRING_DATASOURCE_*` against that DB. |
| **B (optional local)** | Developer machine with Docker | **Testcontainers** (`pgvector/pgvector:0.8.2-pg16-bookworm`) via `INTEGRATION_USE_TESTCONTAINERS=1` | Optional **DB smoke** in `test_tc_postgres_smoke.py` (extensions aligned with Java init SQL). Does **not** replace Path A in CI. |

Do **not** mix Path A and Path B in the same CI job (no Python Testcontainers alongside the GHA Postgres service).

## Layer ownership (avoid triple assertion)

| Concern | Canonical layer |
| --- | --- |
| JDBC, Flyway, `@SpringBootTest` persistence | **Java** (`rag-service` tests with Testcontainers or CI Postgres) |
| HTTP contracts, status codes, JSON shapes, lab job polling | **pytest** (`tests/integration`) |
| Auth + product smoke, `GET /projects`, SYS chains | **Playwright API** (`webapp/e2e/api/`) |
| Browser UX, full flows | **Playwright E2E** (`webapp/e2e/`) |

**Removed from pytest:** `GET {product}/projects` happy path after login - covered by Playwright API (`login.api.spec.ts`, `projects.api.spec.ts`, `system-smoke.chain.spec.ts`).

**Phase 8E (JSON safety vs HTML):** `test_stack_integration.py` asserts authenticated **`GET {product}/lab/status`** shape, **`POST {product}/me/account/export`** **202** payload, and unknown product route **404** with **`application/json`** (via `_assert_json_response_not_html`).

## Location

- Code: `tests/integration/`
- Dependencies: `tests/integration/requirements.txt` (pytest, httpx, optional testcontainers + psycopg)

## CI (GitHub Actions)

The workflow [`.github/workflows/integration.yml`](../../.github/workflows/integration.yml) runs **`pytest tests/integration`** (with `--ignore=tests/integration/test_tc_postgres_smoke.py` so CI stays HTTP-only) against **Spring Boot with profile `e2e`** and **Postgres** (same pattern as Playwright fullstack / Selenium jobs). It sets:

- `INTEGRATION_USE_TESTCONTAINERS=0` (Path A: Postgres from the workflow service; pytest does not start Testcontainers).
- `INTEGRATION_CHECK_OBS=0` (no Jaeger/Prometheus/Grafana assertions in this job).
- `INTEGRATION_BACKEND_URL=http://127.0.0.1:9000` and optional admin credentials for `TestBackendAdminApi`.

**Classifier:** the standard integration job is backend-focused and may leave classifier checks optional. The classifier-required CI lane and the local closure lane set `INTEGRATION_STRICT=1` and `INTEGRATION_REQUIRE_CLASSIFIER=1`, so classifier reachability cannot become a skip-only false green. `INTEGRATION_REQUIRE_CLASSIFIER_MODEL=1` is the stricter mode that also requires `/health` to report a loaded default model (sklearn or Keras per `metadata.json`) before `/classify` assertions. The local closure lane defaults to that stricter mode; CI may keep it at `0` when it only wants classifier-service reachability.

**Observability:** for `TestObservabilityStack`, use a local stack with `compose.obs.yml` and `INTEGRATION_CHECK_OBS=1` (or `auto`).

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
   - `http://ollama:11434` if you use **`docker-compose.yml`** with **`--profile ollama`**.

3. Run tests:

   ```bash
   pip install -r tests/integration/requirements.txt
   pytest tests/integration -v
   ```

   Or from the repo root:

   ```bash
   ./tests/integration/run-integration-tests.sh
   ```

### Strict closure lane

Use this lane for final validation/evidence capture when backend and classifier are expected to be running already:

```bash
.github/local/run-integration-closure.sh
```

Equivalent manual command:

```bash
INTEGRATION_STRICT=1 \
INTEGRATION_REQUIRE_CLASSIFIER=1 \
INTEGRATION_REQUIRE_CLASSIFIER_MODEL=1 \
INTEGRATION_CHECK_OBS=0 \
python -m pytest tests/integration -v -rs --tb=short --ignore=tests/integration/test_tc_postgres_smoke.py
```

Strict closure semantics:

- `INTEGRATION_BACKEND_URL` is required and preflighted via `/actuator/health`.
- `INTEGRATION_CLASSIFIER_URL` is required and preflighted via `/health`.
- Backend/classifier connection skips become failures.
- A run that collects tests but skips all of them fails.
- If classifier is required, classifier-related reachability skips fail the run.
- If `INTEGRATION_REQUIRE_CLASSIFIER_MODEL=1`, model-not-loaded classifier skips also fail the run; with `0`, the reachability lane may pass while reporting that `/classify` was skipped because the model was not loaded.
- Observability remains optional by default (`INTEGRATION_CHECK_OBS=0`); set `INTEGRATION_CHECK_OBS=1` to require OTEL/Prometheus/Grafana/Jaeger.

### Optional local DB smoke (Path B)

Requires Docker. Validates that `testcontainers-vectordb-init.sql` applies cleanly on `pgvector/pgvector:0.8.2-pg16-bookworm` (same extensions as Java Testcontainers). Does **not** start Spring.

```bash
./tests/integration/run-integration-local.sh
```

Or manually:

```bash
export INTEGRATION_USE_TESTCONTAINERS=1
pip install -r tests/integration/requirements.txt
pytest tests/integration/test_tc_postgres_smoke.py -v
```

To point a **local** Spring process at the same container after extensions are created, start the container by running the smoke test once with `INTEGRATION_TC_PRINT_JDBC=1` (prints a suggested `SPRING_DATASOURCE_URL`), then run `./mvnw spring-boot:run` with profile `e2e` in another terminal - advanced and not automated here.

## Observability tests (`INTEGRATION_CHECK_OBS`)

Observability checks (OTEL collector, Jaeger, Prometheus, Grafana, OTLP HTTP port, trace exports) live in **`TestObservabilityStack`**.

| Mode | Behaviour |
| --- | --- |
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
| --- | --- |
| `INTEGRATION_CLASSIFIER_URL` | `http://127.0.0.1:8000` |
| `INTEGRATION_BACKEND_URL` | `http://127.0.0.1:9000` |
| `INTEGRATION_PROMETHEUS_URL` | `http://127.0.0.1:9090` |
| `INTEGRATION_GRAFANA_URL` | `http://127.0.0.1:3000` |
| `INTEGRATION_JAEGER_URL` | `http://127.0.0.1:16686` |
| `INTEGRATION_OTEL_METRICS_URL` | `http://127.0.0.1:8889/metrics` |
| `INTEGRATION_OTLP_HTTP_URL` | `http://127.0.0.1:4318` |

| Variable | Purpose |
| --- | --- |
| `INTEGRATION_RAG_PRODUCT_BASE_PATH` | Product API prefix; same role as `RAG_API_PRODUCT_BASE_PATH` (must match backend). |
| `INTEGRATION_LOGIN_EMAIL` | Email for `POST {product}/auth/login` in product API tests (default `dev@local.test`). |
| `INTEGRATION_LOGIN_PASSWORD` | Password for that user (default `dev`, matches V16 `{noop}dev`). |
| `INTEGRATION_ADMIN_EMAIL` | If set, enables `TestBackendAdminApi` success paths (e.g. `admin@e2e.local` with profile **e2e**). |
| `INTEGRATION_ADMIN_PASSWORD` | ADMIN password (default `e2e`, matches `E2eAdminUserSeeder`). |
| `INTEGRATION_EXPECT_RAG_SERVICE_NAME` | Jaeger service name for the Java backend (default `rag-backend`). |
| `INTEGRATION_EXPECT_CLASSIFIER_SERVICE_NAME` | Jaeger service name for the Python classifier (default `classifier-service`). |
| `INTEGRATION_USE_TESTCONTAINERS` | `1` / `true` to enable `tc_postgres_container` and `test_tc_postgres_smoke` (local Docker only; CI sets `0`). |
| `INTEGRATION_TC_PRINT_JDBC` | When `1`, the session fixture prints a suggested `SPRING_DATASOURCE_URL` for the running container (Path B advanced). |
| `INTEGRATION_STRICT` | `1` turns service-unreachable skips into failures and activates the all-skipped guard. |
| `INTEGRATION_REQUIRE_CLASSIFIER` | `1` makes classifier reachability required; classifier reachability skips fail in strict mode. |
| `INTEGRATION_REQUIRE_CLASSIFIER_MODEL` | `1` makes `/health -> model=loaded` required before `/classify` assertions. |

**Spring Boot env overrides:** variables such as `RAG_HEALTH_OLLAMA_ENABLED` / `RAG_HEALTH_CLASSIFIER_ENABLED` take precedence over `application-e2e.properties`. CI sets them to `false` so `/actuator/health` stays **UP** without a live Ollama/classifier; avoid exporting them as `true` when running the backend with profile **e2e** unless those services are reachable.

## What is checked

### Core stack (always)

- **Classifier**: `GET /health`, `GET /models`, `POST /classify` (including **400** on empty query), **200** classify when model is loaded.
- **Backend**: `GET /actuator/health` (`UP`), `GET /actuator/info` if exposed, `GET /actuator/prometheus` exposes Micrometer metrics.
- **Product API (JWT)**: `GET {product}/presets` and `GET {product}/config/schema` return **401 or 403** without `Authorization`; with seed login **200** and JSON shapes. Paged **`GET {product}/projects`** is asserted in **Playwright API** tests, not duplicated here.
- **OpenAPI**: `GET /v3/api-docs` returns **200** and includes product paths under `{product}`.
- **Lab (polling)**: `GET {product}/lab/status` requires auth (JSON: `datasets.enabled`, `countsByDatasetKind`, no removed `questionCount`). Canonical **`POST {product}/lab/benchmarks/RAG_PRESET_END_TO_END/runs`** with `datasetId` returns **202** + `asyncTaskId` (needs **ADMIN** JWT for the Flyway V42 reference `SYSTEM_DATASET`; set `INTEGRATION_ADMIN_*` / e2e profile in CI). Then `GET {product}/lab/jobs/{asyncTaskId}` returns job JSON (see [ADR 0003](../../docs/adr/0003-evaluation-async-project-scope-and-dataset-dedup.md)). **SSE** is not asserted here.
- **Auth API**: `POST {product}/auth/login` returns **401** for wrong password or unknown user, **400** for invalid email, **401** for bad refresh token; `POST {product}/auth/register` returns **409** when the seed email already exists.
- **Admin API**: `GET {product}/admin/health` and model/admin endpoints require **401/403** without JWT; seed **USER** JWT → **403**; with `INTEGRATION_ADMIN_EMAIL` (+ password), **ADMIN** JWT → **200** and JSON shapes.
- **Cross-service**: classifier and backend reachable in sequence.

### Observability (when stack reachable or `INTEGRATION_CHECK_OBS=1`)

- **Prometheus**: `/-/healthy`, instant query `up`, **`up{job="backend"}==1`** after scrape (with retries).
- **Grafana**: `/api/health`.
- **Jaeger**: UI reachable; **service list includes `rag-backend`** after a query; **includes `classifier-service`** after `POST /classify` (when model loaded).
- **OTEL collector**: self-metrics on `:8889/metrics`; OTLP HTTP port **4318** accepts connections (no connection refused).
- **Metrics pipeline**: Prometheus query finds `rag_query_generate_seconds_count` or `http_server_requests_seconds_count` for the backend job.
- **Backend scrape**: `/actuator/prometheus` contains `rag_*` or `http_server_*`.

## Data lifecycle (pytest HTTP)

- **Session:** one stack per developer/CI run; Flyway runs when Spring starts (not re-run per pytest test).
- **Between tests:** no shared DB state required for HTTP assertions; avoid parallel writes to the same logical entities unless you use unique names (future parallelism).
- **Path B container:** one Postgres container per pytest session that collects `test_tc_postgres_smoke`; destroyed when the process exits.

## Notes

- These tests do not replace unit tests or business E2E tests.
- Full RAG quality requires Ollama models aligned with `SPRING_AI_OLLAMA_*` in the backend.
- **`POST /classify`** (classifier) tests that need the model are **skipped** if `/health` reports `model != loaded`.
- Jaeger service names depend on `OTEL_SERVICE_NAME` in Compose (`compose.obs.yml`).
