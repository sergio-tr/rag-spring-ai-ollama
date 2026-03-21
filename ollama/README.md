# Ollama (optional)

Ollama can run **on the host** (default backend URL `host.docker.internal:11434`) or **in Docker**:

- **`compose.ollama.yml`** — official image, **CPU** (no NVIDIA required).
- **`compose.ollama-gpu.yml`** — **build** from this folder + **NVIDIA GPU** (NVIDIA Container Toolkit required).

Use **one** of those compose files, not both (same service name `ollama`). Both point the backend at `http://ollama:11434`.

## Layout

- **Dockerfile** — Used only with **`compose.ollama-gpu.yml`**: build from base image (version in `ollama/.env`).
- **`.env`** — Copy from `.env.example` or run `./scripts/create-env-ollama.sh`. Variables: `OLLAMA_BASE_IMAGE`, `OLLAMA_PORT`.

## Running with GPU stack

From repo root:

```bash
./scripts/create-env-ollama.sh   # optional, for custom image/port
cd docker
docker compose -f docker-compose.yml -f compose.ollama-gpu.yml --env-file ../db/.env --env-file ../ollama/.env ... up -d
```

Or use `./scripts/set-env.sh` (or `./scripts/up.sh dev --env ollama` / `./scripts/up.sh prod --gpu`) to prepare `.env`, then start compose. Requires NVIDIA Container Toolkit for GPU.

When Ollama is in Docker, the backend uses `http://ollama:11434`; models are pulled on first use (e.g. `gemma3:4b`, `mxbai-embed-large`).
