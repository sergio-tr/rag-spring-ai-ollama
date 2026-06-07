# Ollama (optional)

Ollama can run **on the host** (official demo default, backend URL `host.docker.internal:11434`) or **in Docker** via **`docker-compose.yml`** service **`ollama`** (**`--profile ollama`**) with build from this folder + **NVIDIA GPU** (NVIDIA Container Toolkit required). There is **no** CPU-only Compose path in this repo.

Set **`OLLAMA_BASE_URL=http://ollama:11434`** (and the same for Spring) in `ollama/.env` / `rag-service/.env` when using the container.

## Layout

- **Dockerfile** — Used with **`--profile ollama`**: build from base image (version in `ollama/.env`).
- **`.env`** — Copy from `.env.example` or run `./docker/scripts/create-env-ollama.sh`. Variables: `OLLAMA_BASE_IMAGE`, `OLLAMA_PORT`, optional `OLLAMA_BASE_URL` for Spring.

## Recommended demo mode: host-Ollama

Run Ollama on the host and keep Docker for the application stack:

```bash
ollama serve
ollama pull gemma3:4b
ollama pull mxbai-embed-large
```

Then keep `rag-service/.env` pointed at the host:

```properties
OLLAMA_BASE_URL=http://host.docker.internal:11434
SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434
```

Start the Docker stack from the repository root:

```bash
./docker/scripts/up.sh prod --obs --no-env-prompt
```

## Optional mode: in-stack Ollama with GPU

From repo root:

```bash
./docker/scripts/create-env-ollama.sh   # optional, for custom image/port
./docker/scripts/docker-compose.sh config prod --obs --ollama --no-env-prompt
./docker/scripts/up.sh prod --obs --ollama --no-env-prompt
```

Or use `./docker/scripts/set-env.sh` (or `./docker/scripts/up.sh dev --env ollama` / `./docker/scripts/up.sh prod --gpu` / `--ollama`) to prepare `.env`, then start compose. Requires NVIDIA Container Toolkit for GPU. If the NVIDIA runtime is unavailable, the wrapper warns and skips the `ollama` profile instead of starting a broken container.

When Ollama is in Docker, point the backend at `http://ollama:11434`; models are pulled on first use (e.g. `gemma3:4b`, `mxbai-embed-large`).
