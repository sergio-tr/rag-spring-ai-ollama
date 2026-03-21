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
| `SPRING_DATASOURCE_URL`          | PostgreSQL JDBC URL (same DB as in db/.env)    | `jdbc:postgresql://localhost:5432/vectordb` or `postgres:5432` in Compose |
| `SPRING_DATASOURCE_USERNAME`     | DB user (must match db/.env)                   | `postgres`                |
| `SPRING_DATASOURCE_PASSWORD`     | DB password (must match db/.env)               | —                         |
| `rag.classifier.service.url` | Classifier service URL (backend)               | `http://localhost:8000`   |

You can use environment variables in `application.properties` with placeholders `${VAR_NAME:default}`. Defaults are in `application.properties`; to override in Docker or locally, define the variables in `.env` or in the environment (e.g. `SPRING_DATASOURCE_URL`, `OLLAMA_BASE_URL`, `RAG_CLASSIFIER_SERVICE_URL`).

## Smoke test

With the stack running, see [scripts/README.md](../scripts/README.md) (section **Smoke test**) or run `./scripts/smoke-test.sh`.

## Observability (optional)

With `docker compose -f docker-compose.yml -f compose.obs.yml --env-file ../db/.env --env-file ../rag-service/.env --env-file ../classifier-service/.env --env-file ../observability/.env up -d` you can run OpenTelemetry Collector, Jaeger, Prometheus, and Grafana. The backend exposes `/actuator/health` and `/actuator/prometheus`. Configure the Prometheus datasource in Grafana (`http://prometheus:9090`) for dashboards.

**OTLP in Docker:** the base Compose file sets `SPRING_PROFILES_ACTIVE=docker` so the backend does **not** send OTLP to `localhost:4318` (inside the container that points at the JVM, not the collector). `compose.obs.yml` adds `SPRING_PROFILES_ACTIVE=docker,obs` and `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318` so traces/metrics export to the collector and appear in Jaeger.

## Ollama requirement (Docker without GPU compose)

The default backend image talks to Ollama at `SPRING_AI_OLLAMA_BASE_URL` (from `docker-compose.yml`, usually `http://host.docker.internal:11434`). **Ollama must be running on the host** with the chat and embedding models pulled, or queries will fail with a generic error. Alternatively use `compose.gpu.yml` so Ollama runs in Docker (`SPRING_AI_OLLAMA_BASE_URL=http://ollama:11434`).

## Execution modes
Quick reference and commands: [docker/README.md](../docker/README.md) (section **Execution modes**).

For **prod-local** mode (reverse proxy + hardened ports for internal services):

```bash
./scripts/up-prod-local.sh
```

To stop that mode:

```bash
./scripts/down.sh
```
