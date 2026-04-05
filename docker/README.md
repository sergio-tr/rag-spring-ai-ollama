# Docker Compose

Orchestration files (`docker-compose.yml`, `compose.*.yml`) and operational documentation for the stack.

**See also:** [Deployment model](../docs/architecture/deployment-model.md), [runbook — Docker VM](../docs/operations/runbook-docker-vm.md).

**Target architecture (frozen model):** [ADR 0006 — Keycloak & HTTPS foundation](../docs/adr/0006-keycloak-identity-and-https-foundation.md).

**Images:** Every `FROM` in this monorepo targets a **Linux** userland (OpenJDK/Eclipse Temurin, Node, Python slim, Ollama CUDA variants, etc.). The **Postgres** service uses the pinned image **`pgvector/pgvector:0.8.2-pg16-bookworm`** (see [db/README.md](../db/README.md)). Compose is validated on **Linux** hosts and in **CI** (`ubuntu-*`); use Linux or WSL2 locally for parity.

**GHCR tags ([`build-images.yml`](../.github/workflows/build-images.yml)):** Each built service is pushed as `ghcr.io/<owner>/rag-spring-ai-ollama-<service>:<github_sha>` and also `:latest`. For **reproducible deploy and rollback**, pin by **commit SHA** tag. Treat **`latest` as non-contractual** in runbooks and thesis evidence.

Typical start from the `docker/` directory (canonical entry point: `./docker/scripts/up.sh` from the repo root):

```bash
docker compose --env-file ../db/.env --env-file ../classifier-service/.env --env-file ../rag-service/.env --env-file ../webapp/.env up -d
```

With observability, add `-f compose.obs.yml` and `--env-file ../observability/.env` (see `observability/README.md`).

**Ollama (required for RAG):** the default backend URL is `http://host.docker.internal:11434` (Ollama on the **host**). If Ollama is **not** running on the host, the API will return errors and logs will show `ResourceAccessException` on `/api/embed` and `/api/chat`. To run Ollama **in Docker** with NVIDIA GPU, use **`compose.ollama-local-gpu.yml`** (and **`compose.ollama-local-gpu.dev.yml`** with `compose.dev.yml` when using `backend-dev`). Scripts add these when you pass `--gpu` / `--ollama` and the host has the NVIDIA runtime. For an **external** Ollama URL, use **`compose.ollama-remote.yml`** (`--ollama-remote`). Classifier GPU is in **`compose.gpu.yml`** (merged automatically when NVIDIA is available). Pull models via `docker exec -it ollama ollama pull <model>` when Ollama runs in Docker.

**Health checks (strict):** the backend container probes **`/actuator/health/readiness`** (HTTP **503** until ready). That group includes PostgreSQL, disk space, **Ollama** (`GET /api/tags` and both configured models present), and the **classifier** (`GET /health` with `model: loaded`). The classifier service only becomes healthy when its default model is loaded. Tune or relax checks via `rag.health.*` in `rag-service` (see `application.properties`).

## Execution modes

Quick guide to run the stack with Docker Compose. The **default** `docker-compose.yml` includes **`webapp`** (Next.js) in addition to `postgres`, `classifier-service`, and `backend`.

Scripts and commands assume the repo root, with `.env` files created via:

```bash
./docker/scripts/create-env-all.sh
```

### Base (main stack)

Includes: `postgres`, `classifier-service`, `backend`, **`webapp`**.

Example (aligned with `docker/scripts/docker-compose.sh` env chain):

```bash
cd docker
docker compose \
  --env-file ../db/.env \
  --env-file ../classifier-service/.env \
  --env-file ../rag-service/.env \
  --env-file ../webapp/.env \
  up -d
```

### Base + observability

Also includes: `otel-collector`, `jaeger`, `prometheus`, `grafana` (no host port hardening).

Example:

```bash
cd docker
docker compose \
  -f docker-compose.yml \
  -f compose.obs.yml \
  --env-file ../db/.env \
  --env-file ../rag-service/.env \
  --env-file ../classifier-service/.env \
  --env-file ../webapp/.env \
  --env-file ../observability/.env \
  up -d
```

### Base + Ollama (GPU, in Docker)

Adds **`ollama`** built from `ollama/` with **NVIDIA GPU** (NVIDIA Container Toolkit required). Sets `SPRING_AI_OLLAMA_BASE_URL=http://ollama:11434`. The backend pulls missing models on startup (`rag.ollama.auto-pull-enabled`, default `true`); first start can take several minutes.

Example:

```bash
cd docker
docker compose \
  -f docker-compose.yml \
  -f compose.ollama-local-gpu.yml \
  --env-file ../db/.env \
  --env-file ../rag-service/.env \
  --env-file ../classifier-service/.env \
  --env-file ../webapp/.env \
  --env-file ../ollama/.env \
  up -d
```

### Prod-local (hardening + reverse proxy)

Includes: `reverse-proxy` (HTTP/HTTPS) and `compose.prod.yml` so internal services do not publish host ports.
By default it also includes internal observability (no Jaeger/Prometheus/Grafana/OTEL host ports).

Start / stop:

```bash
./docker/scripts/up.sh prod
./docker/scripts/build.sh prod   # optional: build images with same -f chain as up
./docker/scripts/down.sh         # o: ./docker/scripts/down.sh prod [--all] ...
# Dev (incl. backend-dev): ./docker/scripts/down.sh dev [--all|...] — mismos flags que up dev
```

Options:

- Use `./docker/scripts/up.sh prod --obs` to include `compose.obs.yml` (OTEL, Jaeger, Prometheus, Grafana).
- `--gpu` or `--ollama`: include `compose.ollama-local-gpu.yml` (requires `ollama/.env` and NVIDIA GPU / Container Toolkit)
- `--volumes` (only `down.sh`): also remove named volumes

## Deployment runbook

### Goal

Deploy the project stack (backend + classifier + Postgres + optional observability) on a Linux VM using `docker compose`, with images published to GHCR.

This runbook aligns with the GitHub Actions `deploy.yml` workflow.

### Prerequisites (VM)

1. Docker and Docker Compose installed.
2. SSH access (user allowed to run Docker).
3. Variables and secrets:
   - `GHCR_TOKEN` (token with `read:packages` / `write:packages` as needed).
   - `.env` files outside version control (same layout Compose expects: `db/.env`, `rag-service/.env`, `classifier-service/.env`, `observability/.env` if used).

### Recommended layout on the VM

Keep a copy of the repo (e.g. `/opt/rag-spring-ai-ollama`):

- `docker/` with compose files and overrides
- `db/`, `rag-service/`, `classifier-service/`, `observability/`, `ollama/`
- `.env` (outside the repo or gitignored inside) for:
  - `db/.env`
  - `rag-service/.env`
  - `classifier-service/.env`
  - `observability/.env` (optional)
  - `ollama/.env` (optional)

### Deployment steps

1. Log in to GHCR (for `docker compose pull`):
   - `docker login ghcr.io -u <GITHUB_ACTOR> -p <GHCR_TOKEN>`
2. From the repo folder:
   - `docker compose -f docker/docker-compose.yml -f docker/compose.prod.yml pull`
   - `docker compose -f docker/docker-compose.yml -f docker/compose.prod.yml up -d`
3. Verification (smoke test):
   - See [`../docker/scripts/README.md`](../docker/scripts/README.md) (section **Smoke test**) and [`../scripts/README.md`](../scripts/README.md) (layout index), then run the main checks:
     - `curl -s http://<host>:9000/actuator/health`
     - `curl -s http://<host>:8000/health` (if the classifier port is reachable)
     - `curl -s "http://<host>:<port><legacy-prefix>/query?question=..."` (legacy RAG query path; `<legacy-prefix>` = backend `RAG_API_LEGACY_BASE_PATH`)

> Note: if the backend is not exposed directly (only via reverse proxy), use the reverse-proxy published port (`REVERSE_PROXY_HTTP_PORT` defaults to **80** in `compose.prod.yml`; HTTPS uses `REVERSE_PROXY_HTTPS_PORT`, default **8443** until TLS on **443** is wired).

### Log rotation and volumes

1. Logs:
   - Prefer Docker-level rotation: configure `log-driver` and/or `max-size` / `max-file` in Compose.
2. Persistent volumes (examples):
   - `postgres_data`
   - `prometheus_data`
   - `grafana_data`
   - `ollama_data`
3. Backups:
   - Use `db/scripts/backup-db.sh` / `db/scripts/restore-db.sh` for PostgreSQL.

### Rollback

The safest rollback depends on how images are referenced in compose (`image:` tags vs `build:`):

1. If deployed with versioned images:
   - Re-pull / re-tag the previous version in GHCR (e.g. tag by commit SHA) and run `up -d` again.
2. If using `build:`:
   - Revert the commit on the VM and re-run compose, or rebuild with previous sources.

### Quick post-deploy checklist

- [ ] Containers are up and healthy (`docker ps` + healthchecks).
- [ ] Actuator health OK and `/actuator/prometheus` reachable internally.
- [ ] Classifier responds on `/health` and `/classify`.
- [ ] A RAG query returns HTTP 200 on the backend endpoint.
