# Docker Compose

Orchestration files (`docker-compose.yml`, `compose.*.yml`) and operational documentation for the stack.

**See also:** [Deployment model](../docs/architecture/deployment-model.md), [runbook — Docker VM](../docs/operations/runbook-docker-vm.md).

**Target architecture (frozen model):** [ADR 0006 — Keycloak & HTTPS foundation](../docs/adr/0006-keycloak-identity-and-https-foundation.md).

**Images:** Every `FROM` in this monorepo targets a **Linux** userland (OpenJDK/Eclipse Temurin, Node, Python slim, Ollama CUDA variants, etc.). **Postgres** is built from **`db/Dockerfile`**; **`docker-compose.yml`** passes a **fixed** pgvector pin **`pgvector/pgvector:0.8.2-pg16-bookworm`** as `POSTGRES_BASE_IMAGE` (see [db/README.md](../db/README.md)). Loki, Promtail, node-exporter, and cAdvisor use thin Dockerfiles under **`observability/*/`** with tags from **`observability/.env`**. Compose is validated on **Linux** hosts and in **CI** (`ubuntu-*`); use Linux or WSL2 locally for parity.

**GHCR tags ([`build-images.yml`](../.github/workflows/build-images.yml)):** Each built service is pushed as `ghcr.io/<owner>/rag-spring-ai-ollama-<service>:<github_sha>` and also `:latest`. For **reproducible deploy and rollback**, pin by **commit SHA** tag. Treat **`latest` as non-contractual** in runbooks and thesis evidence.

Typical start from the `docker/` directory (canonical entry point: `./docker/scripts/up.sh` from the repo root):

```bash
docker compose --env-file ../db/.env --env-file ../classifier-service/.env --env-file ../rag-service/.env --env-file ../webapp/.env up -d
```

With observability, add `-f compose.obs.yml`, **`--profile observability`**, and `--env-file ../observability/.env` (see `observability/README.md`).

**Ollama (required for RAG):** set **`OLLAMA_BASE_URL`** and **`SPRING_AI_OLLAMA_BASE_URL`** in **`rag-service/.env`** to wherever the HTTP API listens (host: `http://host.docker.internal:11434`, in-stack service: `http://ollama:11434`, another machine: `http://192.168.x.x:11434`). Compose maps both into **`backend`** and **`backend-dev`**; you do **not** need extra compose files for “remote” vs “local”. If Ollama is unreachable, logs show `ResourceAccessException` on `/api/embed` and `/api/chat`. To run Ollama **in Docker** with NVIDIA GPU, use **`./docker/scripts/up.sh … --gpu`** (or **`--ollama`**) so **`--profile ollama`** starts the **`ollama`** service and point **`OLLAMA_BASE_URL=http://ollama:11434`**. To use **GPU on the host** (or any Ollama outside that container) while still passing **`--gpu`** for other services, add **`--ollama-remote`**: that **skips** the local **`ollama`** container profile but leaves the URL entirely to **`rag-service/.env`**. Classifier GPU uses **`compose.gpu.yml`** when NVIDIA is available. Pull models via `docker exec -it ollama ollama pull <model>` when Ollama runs in Docker.

**Health checks (strict):** the backend container probes **`/actuator/health/readiness`** (HTTP **503** until ready). That group includes PostgreSQL, disk space, **Ollama** (`GET /api/tags` and both configured models present), and the **classifier** (`GET /health` with `model: loaded`). The classifier service only becomes healthy when its default model is loaded. Tune or relax checks via `rag.health.*` in `rag-service` (see `application.properties`).

## Environment files (`.env`)

| Path | Purpose |
| --- | --- |
| `db/.env` | Postgres port, credentials, monitor user; `POSTGRES_BASE_IMAGE` is documentation / manual build only |
| `classifier-service/.env` | Python image, `PORT`, model paths |
| `rag-service/.env` | JDK/JRE images, `SERVER_PORT`, DB URL, `OLLAMA_BASE_URL` / `SPRING_AI_OLLAMA_BASE_URL`, Spring |
| `webapp/.env` | Next.js / `WEBAPP_*` host and container ports |
| `observability/.env` | OTEL/Jaeger/Prometheus/Grafana base images, host ports, Grafana password |
| `ollama/.env` | `OLLAMA_BASE_IMAGE`, `OLLAMA_PORT`, optional `OLLAMA_BASE_URL` for container networking |

**Create or refresh** all of them from templates:

```bash
./docker/scripts/create-env-all.sh
```

Use `--force` to overwrite existing files. The script also runs `sync_env_from_examples.py` to append new keys from each `*.env.example` without clobbering values.

## Compose profiles (`docker-compose.yml`)

Services that are optional are grouped behind **profiles** (pass `--profile <name>` to `docker compose`, or use `./docker/scripts/up.sh` / `docker-compose.sh`, which map flags to profiles).

| Profile | Services / effect |
| --- | --- |
| `observability` | `otel-collector`, `jaeger`, `prometheus`, `grafana` |
| `logs` | `loki`, `promtail` |
| `infra` | `node-exporter` |
| `cadvisor` | `cadvisor` |
| `ollama` | `ollama` (NVIDIA) |
| `rag` | `backend-dev` (from `compose.dev.yml`) |
| `proxy` | dev `reverse-proxy` (from `compose.dev-proxy.yml`) |

Spring OTLP wiring for packaged `backend` / `classifier-service` when using observability is merged from **`compose.obs.yml`** (still required with `--profile observability`). Prod-local UI hardening uses **`compose.prod-obs.yml`** with **`compose.prod.yml`**.

## Dockerfile locations (build contexts)

| Area | Dockerfile(s) |
| --- | --- |
| Database | [`db/Dockerfile`](../db/Dockerfile) |
| Backend | [`rag-service/Dockerfile`](../rag-service/Dockerfile), dev: [`rag-service/Dockerfile.dev`](../rag-service/Dockerfile.dev) |
| Classifier | [`classifier-service/Dockerfile`](../classifier-service/Dockerfile), dev: [`classifier-service/Dockerfile.dev`](../classifier-service/Dockerfile.dev) |
| Webapp | [`webapp/Dockerfile`](../webapp/Dockerfile) |
| Reverse proxy | [`reverse-proxy/Dockerfile`](../reverse-proxy/Dockerfile) |
| Ollama | [`ollama/Dockerfile`](../ollama/Dockerfile) |
| Observability | [`observability/otel-collector/Dockerfile`](../observability/otel-collector/Dockerfile), [`jaeger`](../observability/jaeger/Dockerfile), [`prometheus`](../observability/prometheus/Dockerfile), [`grafana`](../observability/grafana/Dockerfile), [`loki`](../observability/loki/Dockerfile), [`promtail`](../observability/promtail/Dockerfile), [`node-exporter`](../observability/node-exporter/Dockerfile), [`cadvisor`](../observability/cadvisor/Dockerfile) |

Compose uses **`build:`** with `context` + `dockerfile` and **`args`** fed from the `.env` files above (see each service in [`docker-compose.yml`](docker-compose.yml)).

## Parameterization policy

- **Pinned upstream bases** live in component `*.env` / `*.env.example` (e.g. `POSTGRES_BASE_IMAGE`, `*_BASE_IMAGE` in `observability/.env.example`). Compose references them as `${VAR:-default}` where appropriate.
- **No `image:`** keys in `docker/*.yml` — thin Dockerfiles wrap official bases; CI checks this via [`compose_guard.py`](scripts/compose_guard.py).
- **GitHub Actions** Postgres **service containers** in workflows are **not** Compose; they still declare a pinned `image:` (see [`reusable-ci-core.yml`](../.github/workflows/reusable-ci-core.yml)) — that is expected and outside `docker/*.yml`.

## CI validation

On pull requests and pushes to **`dev`**, **`main`**, or **`master`** when files under `docker/` change, [`.github/workflows/docker-compose-ci.yml`](../.github/workflows/docker-compose-ci.yml) runs:

1. `./docker/scripts/create-env-all.sh --force`
2. `compose_guard.py` with structural `--only-rules` (see [`docker/scripts/README.md`](scripts/README.md))
3. `docker compose config -q` for representative merges (logs profile, obs + prod-like)

## Maintenance checklist

- [ ] After editing any `docker/*.yml`, run `docker compose … config -q` locally with the same `-f` / `--profile` / `--env-file` chain as your scenario.
- [ ] Run `python3 docker/scripts/compose_guard.py` (full or CI subset) before merging Docker changes.
- [ ] Keep `*.env.example` in sync when adding compose variables; run `create-env-all.sh` or `sync_env_from_examples.py` on developer machines.
- [ ] If you change a Postgres pin, update `db/.env.example` and [`.github/scripts/verify-pinned-postgres-image.sh`](../.github/scripts/verify-pinned-postgres-image.sh) expectations.
- [ ] Re-read [`docs/devops/README.md`](../docs/devops/README.md) when changing workflow gates or GHCR tags.

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
  --profile observability \
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
  -f compose.gpu.yml \
  --profile ollama \
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

- Use `./docker/scripts/up.sh prod --obs` to include `compose.obs.yml` and **`--profile observability`** (OTEL, Jaeger, Prometheus, Grafana).
- `--gpu` or `--ollama`: adds **`--profile ollama`** (requires `ollama/.env` and NVIDIA GPU / Container Toolkit)
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

1. Log in to GHCR when you consume **prebuilt** images from GHCR (optional for pure `build:` workflows):
   - `docker login ghcr.io -u <GITHUB_ACTOR> -p <GHCR_TOKEN>`
2. From the repo folder, Compose in this tree defaults to **`build:`** from local contexts. Typical flows:
   - **Build from sources:** `docker compose -f docker/docker-compose.yml -f docker/compose.prod.yml build` then `up -d` (or `up -d --build`).
   - **Pull prebuilt tags** only if you use an operator override or image-backed workflow that references GHCR; otherwise `docker compose pull` may no-op. The [`.github/workflows/deploy.yml`](../.github/workflows/deploy.yml) step keeps `pull || true` for mixed environments.
3. Verification (smoke test):
   - See [`../docker/scripts/README.md`](../docker/scripts/README.md) (section **Smoke test**) and [`../scripts/README.md`](../scripts/README.md) (layout index), then run the main checks:
     - `curl -s http://<host>:9000/actuator/health`
     - `curl -s http://<host>:8000/health` (if the classifier port is reachable)
     - Authenticated product smoke (get JWT, then call stable product endpoints like `GET {product}/config/schema` or `GET {product}/presets`)

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

The safest rollback for this repo’s Compose stacks is **build-based**: pin base images in **`db/.env`** / **`observability/.env`**, use GHCR images from [`build-images.yml`](../.github/workflows/build-images.yml) by **commit SHA** when you deploy prebuilt artifacts, or revert the Git revision and run `docker compose build` / `up -d` again from sources.

### Quick post-deploy checklist

- [ ] Containers are up and healthy (`docker ps` + healthchecks).
- [ ] Actuator health OK and `/actuator/prometheus` reachable internally.
- [ ] Classifier responds on `/health` and `/classify`.
- [ ] A RAG query returns HTTP 200 on the backend endpoint.
