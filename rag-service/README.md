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

Each component has its own `.env`: **db/.env** for the database, **observability/.env** for the observability stack. Create defaults from repo root:

```bash
./scripts/create-env-all.sh    # creates db/.env, observability/.env
cd docker
docker compose --env-file ../db/.env up -d
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

With the stack running, see [docs/SMOKE_TEST.md](docs/SMOKE_TEST.md) or run `./scripts/smoke-test.sh`.

## Observability (optional)

With `docker compose -f docker-compose.yml -f compose.obs.yml --env-file ../db/.env --env-file ../observability/.env up -d` you can run OpenTelemetry Collector, Jaeger, Prometheus and Grafana. The backend exposes `/actuator/health` and `/actuator/prometheus`. Configure the Prometheus datasource in Grafana (`http://prometheus:9090`) for dashboards.
