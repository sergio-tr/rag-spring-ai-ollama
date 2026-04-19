# Ollama (optional)

Ollama can run **on the host** (default backend URL `host.docker.internal:11434`) or **in Docker** via **`docker-compose.yml`** service **`ollama`** (**`--profile ollama`**) with build from this folder + **NVIDIA GPU** (NVIDIA Container Toolkit required). There is **no** CPU-only Compose path in this repo.

Set **`OLLAMA_BASE_URL=http://ollama:11434`** (and the same for Spring) in `ollama/.env` / `rag-service/.env` when using the container.

## Layout

- **Dockerfile** — Used with **`--profile ollama`**: build from base image (version in `ollama/.env`).
- **`.env`** — Copy from `.env.example` or run `./docker/scripts/create-env-ollama.sh`. Variables: `OLLAMA_BASE_IMAGE`, `OLLAMA_PORT`, optional `OLLAMA_BASE_URL` for Spring.

## Running with GPU stack

From repo root:

```bash
./docker/scripts/create-env-ollama.sh   # optional, for custom image/port
cd docker
docker compose -f docker-compose.yml -f compose.gpu.yml \
  --profile ollama \
  --env-file ../db/.env --env-file ../ollama/.env \
  --env-file ../classifier-service/.env --env-file ../rag-service/.env --env-file ../webapp/.env \
  up -d
```

Or use `./docker/scripts/set-env.sh` (or `./docker/scripts/up.sh dev --env ollama` / `./docker/scripts/up.sh prod --gpu` / `--ollama`) to prepare `.env`, then start compose. Requires NVIDIA Container Toolkit for GPU.

When Ollama is in Docker, point the backend at `http://ollama:11434`; models are pulled on first use (e.g. `gemma3:4b`, `mxbai-embed-large`).
