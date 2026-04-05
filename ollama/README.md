# Ollama (optional)

Ollama can run **on the host** (default backend URL `host.docker.internal:11434`) or **in Docker** via **`compose.ollama-local-gpu.yml`** only: build from this folder + **NVIDIA GPU** (NVIDIA Container Toolkit required). There is **no** CPU-only Compose file in this repo.

The backend uses `http://ollama:11434` when that override is active.

## Layout

- **Dockerfile** — Used with **`compose.ollama-local-gpu.yml`**: build from base image (version in `ollama/.env`).
- **`.env`** — Copy from `.env.example` or run `./docker/scripts/create-env-ollama.sh`. Variables: `OLLAMA_BASE_IMAGE`, `OLLAMA_PORT`.

## Running with GPU stack

From repo root:

```bash
./docker/scripts/create-env-ollama.sh   # optional, for custom image/port
cd docker
docker compose -f docker-compose.yml -f compose.ollama-local-gpu.yml --env-file ../db/.env --env-file ../ollama/.env ... up -d
```

Or use `./docker/scripts/set-env.sh` (or `./docker/scripts/up.sh dev --env ollama` / `./docker/scripts/up.sh prod --gpu` / `--ollama`) to prepare `.env`, then start compose. Requires NVIDIA Container Toolkit for GPU.

When Ollama is in Docker, the backend uses `http://ollama:11434`; models are pulled on first use (e.g. `gemma3:4b`, `mxbai-embed-large`).
