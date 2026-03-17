# Ollama (optional)

Ollama runs as a container only when using the **GPU stack** (`compose.gpu.yml`). It provides the LLM and embedding models used by the RAG backend.

## Layout

- **Dockerfile** — Build from base image (version in `.env`).
- **.env** — Copy from `.env.example` or run `./scripts/create-env-ollama.sh`. Variables: `OLLAMA_BASE_IMAGE`, `OLLAMA_PORT`.

## Running with GPU stack

From repo root:

```bash
./scripts/create-env-ollama.sh   # optional, for custom image/port
cd docker
docker compose -f docker-compose.yml -f compose.gpu.yml --env-file ../db/.env --env-file ../ollama/.env ... up -d
```

Or use `./scripts/set-env.sh` and choose option 3 or 4. Requires NVIDIA Container Toolkit for GPU.

When the GPU stack is up, the backend uses `http://ollama:11434`; models are pulled on first use (e.g. `gemma3:4b`, `mxbai-embed-large`).
