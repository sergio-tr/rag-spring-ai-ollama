# RAG Spring AI Ollama

RAG (Retrieval-Augmented Generation) system with Spring Boot, Spring AI, Ollama and PostgreSQL (PgVector). Includes a query-type classifier exposed as an HTTP service (classifier-service).

## Build and run

### Backend (Spring Boot)

```bash
mvn -B package
java -jar target/rag-spring-ai-ollama-*.jar
```

By default the backend listens on port **9000**. Database, Ollama and classifier service URL are configured via environment variables or `application.properties` (see Key variables).

### Classifier service (query classifier)

See [classifier-service/README.md](../classifier-service/README.md) for running the classifier (FastAPI). Environment variables: `PORT`, `MODEL_PATH`, `LABELS_PATH`.

### Backend in Docker (dev hot-reload)

To exercise a **container** environment similar to production but with the repo bind-mounted and automatic reload (Maven compile + Spring Boot DevTools), use the **`backend-dev`** service — **not** the default `backend` service from `docker-compose.yml`. From the repo root:

```bash
./scripts/up.sh dev --rag --gpu --obs --classifier   # example
```

This starts **`backend-dev`** (`docker/compose.rag-dev.yml`, `Dockerfile.dev`): bind-mount `rag-service/` → `/app`, Maven cache in the `rag_m2_cache` volume. If Ollama runs in the same Compose stack, set `SPRING_AI_OLLAMA_BASE_URL=http://ollama:11434` in `rag-service/.env`. Watcher poll interval: `RAG_DEV_POLL_INTERVAL` (default `2` seconds).

**Startup:** while Ollama downloads models (`gemma3:4b`, `mxbai-embed-large`, etc.), **`/actuator/health/readiness`** may return **503** — that is expected. The Docker **healthcheck** uses **`/actuator/health/liveness`** (JVM is up). **`/api/**`** routes stay blocked until the pull finishes (`OLLAMA_PROVISIONING`). Use readiness when you want to verify the app is ready for traffic: `curl -s http://localhost:9000/actuator/health/readiness`.

### With Docker Compose

Each component has its own `.env`: **db/.env** for the database, **rag-service/.env** for the backend, **classifier-service/.env** for the classifier, and **observability/.env** (if you use observability). Create defaults from the repo root:

```bash
./scripts/create-env-all.sh
cd docker
docker compose --env-file ../db/.env --env-file ../rag-service/.env --env-file ../classifier-service/.env up -d
```

The `postgres` and `backend` services load **db/.env** for DB credentials. Port and app defaults are in the compose file. For observability, see [observability/README.md](../observability/README.md); run with `--env-file ../observability/.env` when using `compose.obs.yml`.

## Key variables

| Variable / property              | Description                                    | Example / default        |
|----------------------------------|------------------------------------------------|---------------------------|
| `OLLAMA_BASE_URL`                | Ollama URL                                     | `http://localhost:11434` |
| `SPRING_AI_OLLAMA_CHAT_MODEL` / `SPRING_AI_OLLAMA_EMBEDDING_MODEL` | Models used by Spring AI | e.g. `gemma3:4b`, `mxbai-embed-large` |
| `rag.ollama.auto-pull-enabled`   | Pull missing models via Ollama HTTP API on startup | `true` (set `false` offline / air-gapped) |
| `rag.ollama.pull-read-timeout-ms`| Per-`pull` timeout (large models) | `1800000` (30 min) |
| `SPRING_DATASOURCE_URL`          | PostgreSQL JDBC URL (same DB as in db/.env)    | `jdbc:postgresql://localhost:5432/vectordb` or `postgres:5432` in Compose |
| `SPRING_DATASOURCE_USERNAME`     | DB user (must match db/.env)                   | `postgres`                |
| `SPRING_DATASOURCE_PASSWORD`     | DB password (must match db/.env)               | —                         |
| `rag.classifier.service.url` | Classifier service URL (backend)               | `http://localhost:8000`   |

You can use environment variables in `application.properties` with placeholders `${VAR_NAME:default}`. Defaults are in `application.properties`; to override in Docker or locally, define the variables in `.env` or in the environment (e.g. `SPRING_DATASOURCE_URL`, `OLLAMA_BASE_URL`, `RAG_CLASSIFIER_SERVICE_URL`).

## Tests and JaCoCo (`target/site/jacoco/index.html`)

- **`mvn verify`** runs unit/integration tests and **JaCoCo**; the build fails if the **global** bundle (classes included in the report) is below **80% line** coverage (`pom.xml`).
- **`index.html` does not list test classes** — it shows **coverage of production code** (classes/packages). Surefire XML reports under `target/surefire-reports/` are the test execution results; JaCoCo is a separate report.
- **Excluded packages** (see `<excludes>` under `jacoco-maven-plugin` in `pom.xml`) — e.g. `com.uniovi.rag.tool/**`, `configuration`, `model`, large services — **do not appear** in the HTML/XML coverage report. Tests for those packages still run and appear in Surefire, but lines are **not** counted toward JaCoCo.
- **Per-package check (≥80% LINE for every package that appears in the report):** from repo root, after `mvn verify` in `rag-service`:

  ```bash
  python scripts/check-rag-jacoco-packages.py
  ```

- Optional **bounded** retry (not infinite): `scripts/verify-rag-coverage-loop.ps1` (default `-MaxAttempts 50`).

## API response shape (`/api/v4/query`)

Successful responses are JSON: `{ "success": true, "data": { "answer", "queryType", "usedTool", "toolUsed" } }`.  
If the LLM backend (Ollama) cannot be reached, the service returns **503** with `{ "success": false, "error": { "code": "LLM_UNAVAILABLE", ... } }`. While models are still downloading on startup, **503** uses code **`OLLAMA_PROVISIONING`** (retry when `/actuator/health/readiness` is UP).

## Smoke test

With the stack running, see [scripts/README.md](../scripts/README.md) (section **Smoke test**) or run `./scripts/smoke-test.sh`.

## Observability (optional)

With `docker compose -f docker-compose.yml -f compose.obs.yml --env-file ../db/.env --env-file ../rag-service/.env --env-file ../classifier-service/.env --env-file ../observability/.env up -d` you can run OpenTelemetry Collector, Jaeger, Prometheus, and Grafana. The backend exposes `/actuator/health` and `/actuator/prometheus`. Configure the Prometheus datasource in Grafana (`http://prometheus:9090`) for dashboards.

**OTLP in Docker:** the base Compose file sets `SPRING_PROFILES_ACTIVE=docker` so the backend does **not** send OTLP to `localhost:4318` (inside the container that points at the JVM, not the collector). `compose.obs.yml` adds `SPRING_PROFILES_ACTIVE=docker,obs` and `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318` so traces/metrics export to the collector and appear in Jaeger.

## Ollama requirement (Docker without GPU compose)

The default backend image talks to Ollama at `SPRING_AI_OLLAMA_BASE_URL` (from `docker-compose.yml`, usually `http://host.docker.internal:11434`). **Ollama must be reachable** (host or container). On startup, the backend calls Ollama’s HTTP API (`POST /api/pull`) to **download** the chat and embedding models configured in `spring.ai.ollama.chat.model` / `spring.ai.ollama.embedding.model` if they are missing (`rag.ollama.auto-pull-enabled=true` by default). Disable auto-pull in air-gapped environments and provide models manually. Use `compose.ollama.yml` (CPU) or `compose.ollama-gpu.yml` (NVIDIA) so Ollama runs in Docker (`SPRING_AI_OLLAMA_BASE_URL=http://ollama:11434`).

## Execution modes
Quick reference and commands: [docker/README.md](../docker/README.md) (section **Execution modes**).

For **prod-local** mode (reverse proxy + hardened ports for internal services):

```bash
./scripts/up.sh prod
# Optional: add --obs to include OTEL/Jaeger/Prometheus/Grafana (compose.obs.yml)
```

To stop that mode, use the **same** flags as `up` (e.g. `./scripts/down.sh` or `./scripts/down.sh prod --obs`). For **dev** stacks with `backend-dev`, use `./scripts/down.sh dev --all` (or the same flags as `up dev`).
