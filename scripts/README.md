# Scripts

Scripts to generate default `.env` files for each component and to run Docker Compose interactively. Run from the **repository root**.

## Create default .env

| Script | Creates | Purpose |
|--------|---------|---------|
| `create-env-db.sh` | `db/.env` | Postgres (port, user, password, DB), base image (POSTGRES_BASE_IMAGE). Used by main compose. |
| `create-env-observability.sh` | `observability/.env` | Base images (OTEL, Jaeger, Prometheus, Grafana), Grafana password, ports. Used by `compose.obs.yml`. |
| `create-env-rag-service.sh` | `rag-service/.env` | Backend: base images (RAG_JAVA_*), SERVER_PORT, BACKEND_PORT, DB URL, Ollama, classifier URL. For build and local runs. |
| `create-env-classifier-service.sh` | `classifier-service/.env` | Classifier: base image, PORT, MODELS_DIR, DATA_DIR, CLASSIFIER_SERVICE_PORT. For build and local runs. |
| `create-env-ollama.sh` | `ollama/.env` | Ollama (GPU stack): base image (OLLAMA_BASE_IMAGE), OLLAMA_PORT. Used by `compose.gpu.yml`. |
| `create-env-all.sh` | All five above | Runs all five create-env-* scripts. |

Example:

```bash
./scripts/create-env-all.sh
```

If a `.env` already exists, it is not overwritten. Use `--force` to overwrite:

```bash
./scripts/create-env-db.sh --force
```

Default values are in each component's `.env.example`. After creating the `.env` files, edit them to change ports, URLs, or secrets.

## Interactive setup and run: set-env.sh

`set-env.sh` asks whether to create each component's `.env` file (db, observability, rag-service, classifier-service, ollama), then asks which Docker Compose option to run and runs it:

```bash
./scripts/set-env.sh
```

- **Create .env**: for each of the five components you get "Create db/.env? [y/N]" (and similar). Answer `y` to create that `.env` from its `.env.example` (only creates if the file is missing).
- **Run Compose**: then you choose one of:
  1. Main stack only (postgres, classifier, backend)
  2. Main stack + observability (Jaeger, Prometheus, Grafana, OTEL)
  3. Main stack + GPU (Ollama in container with GPU)
  4. Main stack + observability + GPU
  5. Skip (do not run compose)

Options 1–4 run `docker compose` from `docker/` with the appropriate `-f` and `--env-file` arguments. Option 5 exits without running.

## Running Compose manually

From `docker/` (env files are optional; compose uses defaults if a file is missing):

- Main stack: `docker compose --env-file ../db/.env --env-file ../classifier-service/.env --env-file ../rag-service/.env up -d`
- With observability: add `-f compose.obs.yml` and `--env-file ../observability/.env`
- With GPU: add `-f compose.gpu.yml` and `--env-file ../ollama/.env` (Ollama is built from `ollama/Dockerfile`; see `ollama/README.md`)

## Prod local (hardening) — `up` / `down`

**Prod local** starts the stack with `compose.prod.yml` (reverse proxy + hardened ports for internal services).

- Start: `./scripts/up-prod-local.sh [--no-obs] [--gpu]`
- Stop: `./scripts/down.sh [--no-obs] [--gpu] [--volumes]`

Notes:
- By default includes `compose.obs.yml` (internal Jaeger/Prometheus/Grafana/OTEL; no published host ports).
- With `--gpu`, adds `compose.gpu.yml` (requires `ollama/.env` and a GPU-capable runtime).

## Database backup / restore

These scripts target Postgres in a container (default container name `postgres`).

### Backup

Produces a `.sql` file with `pg_dump`:

```bash
./scripts/backup-db.sh
```

Optional variables:

- `DB_CONTAINER_NAME` (default `postgres`)
- `OUTPUT_DIR` (default `backups`)
- `POSTGRES_USER` (default `postgres`)
- `POSTGRES_DB` (default `vectordb`)

The file is saved under `OUTPUT_DIR` with a name like `db-backup-<YYYYMMDD-HHMMSS>.sql`.

### Restore

Restores a `.sql` backup with `psql` (destructive: replaces current schema/data per the dump):

```bash
./scripts/restore-db.sh path/to/db-backup-YYYYMMDD-HHMMSS.sql
```

Optional variables:

- `DB_CONTAINER_NAME` (default `postgres`)
- `POSTGRES_USER` (default `postgres`)
- `POSTGRES_DB` (default `vectordb`)

## Smoke test

After `docker compose up -d`, verify that services respond and that an RAG query goes through the classifier.

For a **repeatable pytest suite** (health + classify + backend + optional observability), see [`tests/integration/README.md`](../tests/integration/README.md).

### Technical E2E (script)

To automate the flow (base + optional observability/GPU):

- `./tests/e2e/e2e-technical-compose.sh` (base stack)
- `./tests/e2e/e2e-technical-compose.sh --obs` (Jaeger/Prometheus/Grafana/OTEL)
- `./tests/e2e/e2e-technical-compose.sh --gpu` (Ollama in container with GPU; host must support GPU)
- `./tests/e2e/e2e-technical-compose.sh --obs --gpu` (both)

By default the script brings the stack up and then tears it down; use `--keep` to leave it running.

### 1. Health checks

- **classifier-service:** `curl -s http://localhost:8000/health` → should return `{"status":"ok","model":"loaded"}` (or `"not_loaded"` if the model failed to load).
- **Backend:** `curl -s http://localhost:9000/actuator/health` (if actuator is enabled) or any endpoint that returns 200.

### 2. Classifier

- **Direct to classifier-service:**  
  `curl -s -X POST http://localhost:8000/classify -H "Content-Type: application/json" -d "{\"query\": \"How many documents are there?\"}"`  
  → should return something like `{"queryType":"COUNT_DOCUMENTS"}` (or another valid type).

### 3. RAG query

- **Backend (classification happens internally):**  
  `curl -s "http://localhost:9000/api/v4/query?question=How%20many%20documents%20are%20there?"`  
  → should return 200 and a response body. If the classifier is unavailable, the backend may still respond using an LLM fallback.

### Optional script (from repo root, stack running)

```bash
# classifier-service health
curl -sf http://localhost:8000/health && echo " classifier-service OK"

# Classify
curl -sf -X POST http://localhost:8000/classify -H "Content-Type: application/json" -d '{"query":"How many documents?"}' && echo " Classify OK"

# Backend query (needs DB data and Ollama for a full answer)
curl -sf -o /dev/null -w "%{http_code}" "http://localhost:9000/api/v4/query?question=test" && echo " Backend query OK"
```

If the DB is empty or Ollama is down, the backend may still return 200 with an error message in the body; the minimum smoke check is HTTP 200.
