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
