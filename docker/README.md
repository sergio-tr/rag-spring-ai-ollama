# Docker Compose

Orchestration files (`docker-compose.yml`, `compose.*.yml`) and operational documentation for the stack.

Typical start from here:

```bash
docker compose --env-file ../db/.env --env-file ../classifier-service/.env --env-file ../rag-service/.env up -d
```

With observability, add `-f compose.obs.yml` and `--env-file ../observability/.env` (see `observability/README.md`).

## Execution modes

Quick guide to run the stack with Docker Compose (no frontend).

Scripts and commands assume the repo root, with `.env` files created via:

```bash
./scripts/create-env-all.sh
```

### Base (main stack)

Includes: `postgres`, `classifier-service`, `backend`.

Example:

```bash
cd docker
docker compose \
  --env-file ../db/.env \
  --env-file ../classifier-service/.env \
  --env-file ../rag-service/.env \
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
  --env-file ../observability/.env \
  up -d
```

### Base + GPU

Includes `ollama` in a container and points the backend at it.

Example:

```bash
cd docker
docker compose \
  -f docker-compose.yml \
  -f compose.gpu.yml \
  --env-file ../db/.env \
  --env-file ../rag-service/.env \
  --env-file ../classifier-service/.env \
  --env-file ../ollama/.env \
  up -d
```

### Prod-local (hardening + reverse proxy)

Includes: `reverse-proxy` (HTTP/HTTPS) and `compose.prod.yml` so internal services do not publish host ports.
By default it also includes internal observability (no Jaeger/Prometheus/Grafana/OTEL host ports).

Start / stop:

```bash
./scripts/up-prod-local.sh
./scripts/down.sh
```

Options:

- `--no-obs`: do not include `compose.obs.yml`
- `--gpu`: include `compose.gpu.yml` (requires `ollama/.env` and a GPU-capable environment)
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
   - See [`../scripts/README.md`](../scripts/README.md) (section **Smoke test**) and run the main checks:
     - `curl -s http://<host>:9000/actuator/health`
     - `curl -s http://<host>:8000/health` (if the classifier port is reachable)
     - `curl -s "http://<host>:<port>/api/v4/query?question=..."` (backend endpoint)

> Note: if the backend is not exposed directly (only via reverse proxy), use the reverse-proxy published port (e.g. `REVERSE_PROXY_HTTP_PORT` / `REVERSE_PROXY_HTTPS_PORT`).

### Log rotation and volumes

1. Logs:
   - Prefer Docker-level rotation: configure `log-driver` and/or `max-size` / `max-file` in Compose.
2. Persistent volumes (examples):
   - `postgres_data`
   - `prometheus_data`
   - `grafana_data`
   - `ollama_data`
3. Backups:
   - Use `scripts/backup-db.sh` / `scripts/restore-db.sh` for PostgreSQL.

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
