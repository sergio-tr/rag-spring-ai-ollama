# Database (PostgreSQL + pgvector)

PostgreSQL 16 with the pgvector extension. Used by the backend (rag-service) for vectors and RAG data.

## Schema initialization (db/init)

The **db/init/** directory contains the SQL scripts that run when the container is created for the first time (mounted at `/docker-entrypoint-initdb.d`):

- **init.sql**: creates extensions (vector, hstore, uuid-ossp), `documents` and `vector_store` tables, and indexes for RAG.

**db/legacy/** holds the previous minimal schema version (prev_init.sql) for reference only; it is not run by Docker.

## Variables (db/.env)

| Variable           | Description   | Default  |
|--------------------|---------------|----------|
| `POSTGRES_PORT`    | Exposed port  | `5432`   |
| `POSTGRES_USER`    | User          | `postgres` |
| `POSTGRES_PASSWORD`| Password      | `postgres` |
| `POSTGRES_DB`      | Database name | `vectordb` |

Create `db/.env` from defaults (from repo root):

```bash
./scripts/create-env-db.sh
```

Use `--force` to overwrite. The template is **db/.env.example**.

## Using Docker Compose

The main stack uses **db/.env** only for database-related configuration. The `postgres` service is built from **db/** and loads **db/.env**; the backend also uses **db/.env** for `SPRING_DATASOURCE_*`. Port and URL substitution in the compose file use the same file when you pass it to Compose.

From the repo root:

```bash
./scripts/create-env-db.sh   # creates db/.env if missing
cd docker
docker compose --env-file ../db/.env up -d
```

Ports and other app defaults (e.g. `BACKEND_PORT`, `OLLAMA_BASE_URL`) are defined with defaults in the compose file; override them via the environment when running `docker compose` if needed.

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
