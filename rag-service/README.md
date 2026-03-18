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

Cada componente tiene su propio `.env`: **db/.env** para la base de datos, **rag-service/.env** para el backend, **classifier-service/.env** para el clasificador y **observability/.env** (si usas observabilidad). Crea defaults desde la raíz del repo:

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

With the stack running, see [docs/SMOKE_TEST.md](docs/SMOKE_TEST.md) or run `./scripts/smoke-test.sh`.

## Observability (optional)

Con `docker compose -f docker-compose.yml -f compose.obs.yml --env-file ../db/.env --env-file ../rag-service/.env --env-file ../classifier-service/.env --env-file ../observability/.env up -d` puedes ejecutar OpenTelemetry Collector, Jaeger, Prometheus y Grafana. El backend expone `/actuator/health` y `/actuator/prometheus`. Configura la datasource de Prometheus en Grafana (`http://prometheus:9090`) para los dashboards.

## Modos de ejecucion
Referencia rápida y comandos: [docs/MODOS_EJECUCION.md](../docs/MODOS_EJECUCION.md).

Para el modo “prod local” (reverse proxy + hardening de puertos para servicios internos):

```bash
./scripts/up-prod-local.sh
```

Para parar ese modo:

```bash
./scripts/down.sh
```
