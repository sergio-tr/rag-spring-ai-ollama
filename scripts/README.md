# Scripts

Scripts to generate default `.env` files for each component and to run Docker Compose interactively. Run from the **repository root**.

## Create default .env

| Script | Creates | Purpose |
|--------|---------|---------|
| `create-env-db.sh` | `db/.env` | PostgreSQL (port, user, password, database). Used by `docker-compose.yml` for postgres and backend. |
| `create-env-observability.sh` | `observability/.env` | Image versions (OTEL, Jaeger, Prometheus, Grafana), Grafana password, ports. Used by `compose.obs.yml`. |
| `create-env-rag-service.sh` | `rag-service/.env` | Backend: server port, DB URL, Ollama, classifier URL. For local runs. |
| `create-env-classifier-service.sh` | `classifier-service/.env` | Classifier: port, MODELS_DIR, DATA_DIR, etc. For local runs. |
| `create-env-all.sh` | All four above | Runs all four create-env-* scripts. |

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

`set-env.sh` asks whether to create each component's `.env` file (db, observability, rag-service, classifier-service), then asks which Docker Compose option to run and runs it:

```bash
./scripts/set-env.sh
```

- **Create .env**: for each of the four components you get "Create db/.env? [y/N]" (and similar). Answer `y` to create that `.env` from its `.env.example` (only creates if the file is missing).
- **Run Compose**: then you choose one of:
  1. Main stack only (postgres, classifier, backend)
  2. Main stack + observability (Jaeger, Prometheus, Grafana, OTEL)
  3. Main stack + GPU (Ollama in container with GPU)
  4. Main stack + observability + GPU
  5. Skip (do not run compose)

Options 1–4 run `docker compose` from `docker/` with the appropriate `-f` and `--env-file` arguments. Option 5 exits without running.

## Running Compose manually

From `docker/`:

- Main stack: `docker compose --env-file ../db/.env up -d`
- With observability: `docker compose -f docker-compose.yml -f compose.obs.yml --env-file ../db/.env --env-file ../observability/.env up -d`
- With GPU: `docker compose -f docker-compose.yml -f compose.gpu.yml --env-file ../db/.env up -d`
