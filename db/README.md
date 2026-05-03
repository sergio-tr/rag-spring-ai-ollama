# Database (PostgreSQL + pgvector)

PostgreSQL 16 with the pgvector extension. [docker-compose.yml](../docker/docker-compose.yml) builds **`postgres`** from [Dockerfile](./Dockerfile) with a **fixed** `POSTGRES_BASE_IMAGE` build-arg (`pgvector/pgvector:0.8.2-pg16-bookworm`, same pin as CI and [.github/ci/postgres-service-image.env](../.github/ci/postgres-service-image.env)). The value **`POSTGRES_BASE_IMAGE` in `db/.env`** is for documentation and manual `docker build` only. The backend (rag-service) uses this database for vectors and RAG data.

## Schema bootstrap (db/init)

The **db/init/** directory contains scripts run on **first container init** (mounted at `/docker-entrypoint-initdb.d`), in sorted filename order:

- **00-extensions.sql**: creates extensions (`vector`, `hstore`, `uuid-ossp`), ensures the **`postgres_exporter`** login exists (default password, idempotent), and grants **`pg_monitor`** so the OTEL collector can scrape even before Flyway runs.
- **01-monitor-user.sh**: aligns the monitoring role with **`POSTGRES_MONITOR_*`** from **db/.env** (create or `ALTER ROLE` password) and grants **`pg_monitor`** for the OpenTelemetry Collector `postgresql` receiver.

**db/legacy/** holds an alternate reference schema (`prev_init.sql`) for comparison; it is not run by Docker.

## Variables (db/.env)

| Variable | Description | Default |
| --- | --- | --- |
| `POSTGRES_BASE_IMAGE` | Pin for manual `docker build` / docs (Compose `postgres` build uses the fixed pin in **docker-compose.yml**) | `pgvector/pgvector:0.8.2-pg16-bookworm` |
| `POSTGRES_PORT` | Exposed port | `5432` |
| `POSTGRES_USER` | User | `postgres` |
| `POSTGRES_PASSWORD` | Password | `postgres` |
| `POSTGRES_DB` | Database name | `vectordb` |
| `POSTGRES_MONITOR_USER` | Read-only monitoring user (metrics) | `postgres_exporter` |
| `POSTGRES_MONITOR_PASSWORD` | Password for monitoring user | `postgres_exporter` |

Ollama (GPU stack) has its own folder **ollama/** with Dockerfile and `.env`; see `ollama/README.md`.

Create `db/.env` from defaults (from repo root):

```bash
./docker/scripts/create-env-db.sh
```

Use `--force` to overwrite. The template is **db/.env.example**.

## Troubleshooting: `extension "vector" is not available`

If Postgres or Flyway logs show **`extension "vector" is not available`** or **`vector.control: No such file or directory`**, the **running server** does not include pgvector. Typical causes:

1. **Stale image or volume**: the `postgres` container is still an old build or a non-compose Postgres. Run **`cd docker && docker compose … build --no-cache postgres`** then **`up -d postgres`**. If the data directory was created with a non-pgvector image, you may need a **new volume** (`docker compose … down -v` — **destructive**).
2. **`SPRING_DATASOURCE_URL`** (or host port mapping) targets **another** PostgreSQL on your machine (local install, other compose project) without pgvector. Point the backend at the compose **`postgres`** service or install pgvector there.
3. **Custom / manual `docker build`** of **`db/Dockerfile`** with a non-pgvector **`POSTGRES_BASE_IMAGE`** — use **`pgvector/pgvector:0.8.2-pg16-bookworm`** or install pgvector per the [pgvector install docs](https://github.com/pgvector/pgvector#installation).

## Using Docker Compose

The main stack uses **db/.env**, **classifier-service/.env** and **rag-service/.env**. The `postgres` service is built from **`db/Dockerfile`** in [docker/docker-compose.yml](../docker/docker-compose.yml) with a **pinned** pgvector base (see the introduction above). The backend and classifier are built from their own Dockerfiles.

From the repo root:

```bash
./docker/scripts/create-env-db.sh
./docker/scripts/create-env-classifier-service.sh
./docker/scripts/create-env-rag-service.sh
cd docker
docker compose --env-file ../db/.env --env-file ../classifier-service/.env --env-file ../rag-service/.env up -d
```

Or use `./docker/scripts/set-env.sh` to create `.env` files interactively, then `./docker/scripts/up.sh dev|prod` to start the stack.

## Running only the database (local development)

```bash
docker run -d --name postgres -p 5432:5432 \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -e POSTGRES_DB=vectordb \
  -v "$(pwd)/db/init:/docker-entrypoint-initdb.d:ro" \
  pgvector/pgvector:0.8.2-pg16-bookworm
```

Then in the backend (rag-service) use: `SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/vectordb`, with the same user and password as in db/.env.

## Flyway migrations (rag-service)

The **authoritative application schema** is applied by **Flyway** from `rag-service/src/main/resources/db/migration/` when the Spring Boot app starts. **`db/init` does not own application tables** — only extensions and the monitoring role (see **00-extensions.sql** and **01-monitor-user.sh**).

**Extensions:** `V1__init_schema.sql` begins with `CREATE EXTENSION IF NOT EXISTS` for **`vector`**, **`hstore`**, and **`uuid-ossp`** so Flyway succeeds on databases that were **not** initialized via `db/init` (e.g. plain `CREATE DATABASE`). If your Flyway history already contains an applied **V1** from an older checksum, run **`./mvnw flyway:repair -pl rag-service`** (or the equivalent repair for your environment) after upgrading the repo.

**Monitoring role:** `V34__postgres_exporter_monitoring_role.sql` creates **`postgres_exporter`** + **`pg_monitor`** if missing (default password matches **db/.env.example**), so existing volumes without `db/init` scripts still work with the OTEL PostgreSQL receiver. **`V35__postgres_exporter_password_align.sql`** sets that role’s password to the same default (fixes collector auth when **OBS_PG_EXPORTER_*** in **observability/.env** and the DB role were out of sync). If you use a **non-default** exporter password, set **`POSTGRES_MONITOR_PASSWORD`** and **`OBS_PG_EXPORTER_PASSWORD`** identically and adjust **`V35`** (or run **`ALTER ROLE`** yourself) so Flyway does not overwrite your secret.

**Before upgrading a shared or production database:** take a **backup** (`pg_dump` or your snapshot process), then deploy a build that includes the new migration(s). Example: **V19** adds nullable `project_id` on `evaluation_run` and `async_task` (see [ADR 0003](../docs/adr/0003-evaluation-async-project-scope-and-dataset-dedup.md) and [DATA_MODEL.md](../docs/architecture/DATA_MODEL.md)).

Local check after pulling: start `rag-service` against your DB (or run `mvn -pl rag-service flyway:validate` if configured) so migrations apply in order.

## PostgreSQL observability (metrics via OpenTelemetry Collector)

The observability stack includes an OpenTelemetry Collector that exposes Prometheus metrics on `:8889` and is configured to scrape PostgreSQL using the **postgresql receiver**, following the guide “How to Use OpenTelemetry with Postgres” from Last9 ([link](https://last9.io/blog/how-to-use-opentelemetry-with-postgres/)).

**Credentials:** the canonical values are **`POSTGRES_MONITOR_USER` / `POSTGRES_MONITOR_PASSWORD`** in **db/.env** (applied by **`01-monitor-user.sh`** on first init). [docker-compose.yml](../docker/docker-compose.yml) sets the collector’s **`OBS_PG_EXPORTER_*`** from those keys only (nested fallbacks were removed because they broke password alignment on some Compose versions). **`observability/.env`** lines **`OBS_PG_EXPORTER_*`** are documentation defaults for non-compose runs; under Compose, **`db/.env`** wins. Always pass **`--env-file ../db/.env`** (or use **`docker/scripts/docker-compose.sh`**, which does) so interpolation sees **`POSTGRES_MONITOR_*`**.

**Existing data volume (init scripts do not re-run):** with Compose **profile `observability`**, service **`postgres-monitor-bootstrap`** runs once per `up` (after Postgres is healthy) and executes [**db/scripts/ensure-postgres-monitor-role.sh**](../db/scripts/ensure-postgres-monitor-role.sh) over the network so **`postgres_exporter`** exists and its password matches **`POSTGRES_MONITOR_*`** before **`otel-collector`** starts. Without that profile, create the role manually, start **rag-service** so Flyway **V34** applies, or run the `psql` block below.

**Existing data volume (manual SQL, no observability profile):** if Postgres logs **`Role "postgres_exporter" does not exist`**, create the role once (same as **V34** / **00-extensions.sql**), then align the password with your env files:

```bash
cd docker && docker compose --env-file ../db/.env -f docker-compose.yml exec -T postgres \
  psql -U postgres -d vectordb -v ON_ERROR_STOP=1 -c "DO \$\$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'postgres_exporter') THEN CREATE ROLE postgres_exporter LOGIN PASSWORD 'postgres_exporter'; END IF; END \$\$;" \
  -c "GRANT pg_monitor TO postgres_exporter;"
```

If the role already exists but the collector reports **`password authentication failed`**, set **`POSTGRES_MONITOR_PASSWORD`** in **db/.env**, then either **`docker compose --profile observability up -d --force-recreate postgres-monitor-bootstrap otel-collector`**, or run **`ALTER ROLE`** once so the DB matches **db/.env**:

```bash
cd docker && docker compose --env-file ../db/.env -f docker-compose.yml exec -T postgres \
  psql -U postgres -d vectordb -c "ALTER ROLE postgres_exporter PASSWORD 'same-as-env';"
```

Use the real password instead of `same-as-env` (and match **`postgres_exporter`** in the `DO` block if you chose a different initial password).

The collector configuration in `observability/otel-collector/config.yaml` adds:

- A `postgresql` receiver that connects to the `postgres` service on port 5432 using the `postgres_exporter` user.
- A metrics pipeline that includes both `otlp` (application metrics) and `postgresql` as receivers, and exports everything via the existing Prometheus exporter.

Grafana (under `observability/grafana`) is provisioned with a dashboard `postgres-metrics.json` that uses the `prometheus` datasource and shows:

- Active connections (`postgresql.backends`)
- Transaction rate (commits / rollbacks)
- Rows fetched per second
- Blocks read (I/O)

With the observability compose stack and the main stack up, you should see PostgreSQL metrics in Grafana under the “RAG” folder, dashboard “PostgreSQL - Metrics (via OpenTelemetry Collector)”.
