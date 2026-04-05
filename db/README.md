# Database (PostgreSQL + pgvector)

PostgreSQL 16 with the pgvector extension. Used by the backend (rag-service) for vectors and RAG data.

## Schema initialization (db/init)

The **db/init/** directory contains the SQL scripts that run when the container is created for the first time (mounted at `/docker-entrypoint-initdb.d`):

- **init.sql**: creates extensions (vector, hstore, uuid-ossp), `documents` and `vector_store` tables, and indexes for RAG.

**db/legacy/** holds an alternate reference schema (`prev_init.sql`) for comparison; it is not run by Docker.

## Variables (db/.env)

| Variable | Description | Default |
| --- | --- | --- |
| `POSTGRES_PORT` | Exposed port | `5432` |
| `POSTGRES_USER` | User | `postgres` |
| `POSTGRES_PASSWORD` | Password | `postgres` |
| `POSTGRES_DB` | Database name | `vectordb` |
| `POSTGRES_MONITOR_USER` | Read-only monitoring user (metrics) | `postgres_exporter` |
| `POSTGRES_MONITOR_PASSWORD` | Password for monitoring user | `postgres_exporter` |
| `POSTGRES_BASE_IMAGE` | Base image for db/Dockerfile | `postgres:16` |

Ollama (GPU stack) has its own folder **ollama/** with Dockerfile and `.env`; see `ollama/README.md`.

Create `db/.env` from defaults (from repo root):

```bash
./docker/scripts/create-env-db.sh
```

Use `--force` to overwrite. The template is **db/.env.example**.

## Using Docker Compose

The main stack uses **db/.env**, **classifier-service/.env** and **rag-service/.env** for build-args and runtime variables. The `postgres` service is built from **db/Dockerfile** (base image from `POSTGRES_BASE_IMAGE`); the backend and classifier are built from their own Dockerfiles with args from their `.env` files.

From the repo root:

```bash
./docker/scripts/create-env-db.sh
./docker/scripts/create-env-classifier-service.sh
./docker/scripts/create-env-rag-service.sh
cd docker
docker compose --env-file ../db/.env --env-file ../classifier-service/.env --env-file ../rag-service/.env up -d
```

Or use `./docker/scripts/set-env.sh` to create `.env` files interactively, then `./docker/scripts/up.sh dev|prod` to start the stack. Ports and base images are in the respective `.env` files; override them there or when running `docker compose`.

## Running only the database (local development)

```bash
docker build -t rag-db ./db
docker run -d --name postgres -p 5432:5432 \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=vectordb \
  rag-db
```

Then in the backend (rag-service) use in `.env` or in the environment: `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/vectordb`, with the same user and password as in db/.env.

## Flyway migrations (rag-service)

The **authoritative schema** for the application stack is applied by **Flyway** from `rag-service/src/main/resources/db/migration/` when the Spring Boot app starts (not from `db/init` alone, which bootstraps extensions and legacy tables in the Docker image).

**Before upgrading a shared or production database:** take a **backup** (`pg_dump` or your snapshot process), then deploy a build that includes the new migration(s). Example: **V19** adds nullable `project_id` on `evaluation_run` and `async_task` (see [ADR 0003](../docs/adr/0003-evaluation-async-project-scope-and-dataset-dedup.md) and [DATA_MODEL.md](../docs/architecture/DATA_MODEL.md)).

Local check after pulling: start `rag-service` against your DB (or run `mvn -pl rag-service flyway:validate` if configured) so migrations apply in order.

## PostgreSQL observability (metrics via OpenTelemetry Collector)

The observability stack includes an OpenTelemetry Collector that already exposes Prometheus metrics on `:8889` and is configured to scrape PostgreSQL using the **postgresql receiver**, following the guide\
“How to Use OpenTelemetry with Postgres” from Last9 ([link](https://last9.io/blog/how-to-use-opentelemetry-with-postgres/)).

The `db/init/init.sql` script:

- Ensures the standard extensions for RAG are created.
- Creates a **read-only monitoring user** if it does not exist:
  - `postgres_exporter` with password `postgres_exporter`
  - Grants the `pg_monitor` role, as recommended in the Last9 guide.

The collector configuration in `observability/otel-collector/config.yaml` adds:

- A `postgresql` receiver that connects to the `postgres` service on port 5432 using the `postgres_exporter` user.
- A metrics pipeline that includes both `otlp` (application metrics) and `postgresql` as receivers, and exports everything via the existing Prometheus exporter.

Grafana (under `observability/grafana`) is provisioned with a dashboard\
`postgres-metrics.json` that uses the `prometheus` datasource and shows:

- Active connections (`postgresql.backends`)
- Transaction rate (commits / rollbacks)
- Rows fetched per second
- Blocks read (I/O)

With the observability compose stack and the main stack up, you should see PostgreSQL metrics in Grafana under the “RAG” folder, dashboard “PostgreSQL - Metrics (via OpenTelemetry Collector)”.
