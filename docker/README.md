# Docker Compose

Orchestration files (`docker-compose.yml`, `compose.*.yml`) and operational documentation for the stack.

**See also:** [Deployment model](../docs/architecture/deployment-model.md), [runbook — Docker VM](../docs/operations/runbook-docker-vm.md).

**Target architecture (frozen model):** [ADR 0006 — Keycloak & HTTPS foundation](../docs/adr/0006-keycloak-identity-and-https-foundation.md).

**Images:** Every `FROM` in this monorepo targets a **Linux** userland (OpenJDK/Eclipse Temurin, Node, Python slim, Ollama CUDA variants, etc.). **Postgres** is built from **`db/Dockerfile`**; **`docker-compose.yml`** passes a **fixed** pgvector pin **`pgvector/pgvector:0.8.2-pg16-bookworm`** as `POSTGRES_BASE_IMAGE` (see [db/README.md](../db/README.md)). Loki, Promtail, node-exporter, and cAdvisor use thin Dockerfiles under **`observability/*/`** with tags from **`observability/.env`**. Compose is validated on **Linux** hosts and in **CI** (`ubuntu-*`); use Linux or WSL2 locally for parity.

**GHCR tags ([`build-images.yml`](../.github/workflows/build-images.yml)):** The workflow runs on **release published** or **manual dispatch**. Each built service is pushed as `ghcr.io/<owner>/rag-spring-ai-ollama-<service>:<commit_sha>`, `:latest`, and (on a GitHub Release) `:<release_tag>` (e.g. `v1.0.0`). For **reproducible deploy and rollback**, pin by **commit SHA** tag. Treat **`latest` as non-contractual** in runbooks and evidence.

Typical start from the `docker/` directory (canonical entry point: `./docker/scripts/up.sh` from the repo root):

```bash
docker compose --env-file ../db/.env --env-file ../classifier-service/.env --env-file ../rag-service/.env --env-file ../webapp/.env up -d
```

With observability, add `-f compose.obs.yml`, **`--profile observability`**, and `--env-file ../observability/.env` (see `observability/README.md`).

## Official demo start (health + metrics + traces)

The recommended demo mode is **host-Ollama**: run Ollama on the host (or another reachable machine), keep Docker for Postgres, `rag-service`, `classifier-service`, `webapp`, `reverse-proxy`, and optional observability. In `rag-service/.env`, keep or set:

```properties
OLLAMA_BASE_URL=http://host.docker.internal:11434
SPRING_AI_OLLAMA_BASE_URL=http://host.docker.internal:11434
```

Prepare or refresh local env files once, then validate and start non-interactively. This is the official local/demo command set:

```bash
./docker/scripts/create-env-all.sh
./docker/scripts/docker-compose.sh config prod --obs --no-env-prompt
./docker/scripts/up.sh prod --obs --no-env-prompt
```

`prod --obs` exposes the observability UIs on localhost for evidence capture:

- Prometheus: `http://127.0.0.1:${PROMETHEUS_PORT:-9090}`
- Grafana: `http://127.0.0.1:${GRAFANA_PORT:-3000}`
- Jaeger: `http://127.0.0.1:${JAEGER_UI_PORT:-16686}`

For VM/private deployments where those UIs must not publish host ports, add `--obs-private`; that merges `compose.prod-obs.yml` and keeps Jaeger/Prometheus/Grafana internal.

Optional local smoke after the stack is up:

```bash
./docker/scripts/local-demo-smoke.sh --obs --skip-up
```

For an end-to-end authenticated model-registry check, export demo credentials from your local seeded/test environment before running the smoke script:

```bash
DEMO_SMOKE_EMAIL=<local-user-email> DEMO_SMOKE_PASSWORD=<local-user-password> \
  ./docker/scripts/local-demo-smoke.sh --obs --skip-up
```

Do not put real credentials in docs or committed env files.

- **CI-equivalent Compose** (explicit files; same services as [`observability-integration.yml`](../.github/workflows/observability-integration.yml)):

```bash
./docker/scripts/create-env-all.sh --force
cd docker
docker compose -f docker-compose.yml -f compose.obs.yml \
  --profile observability \
  --env-file ../db/.env \
  --env-file ../classifier-service/.env \
  --env-file ../rag-service/.env \
  --env-file ../webapp/.env \
  --env-file ../observability/.env \
  up -d --build
```

The product **continues to work** if you omit `compose.obs.yml` and `--profile observability`: no collector, no Jaeger/Grafana/Prometheus; backend uses profile `docker` only (no OTLP push to `localhost` inside the container). See `compose.obs.yml` and `rag-service` `application-infra.properties`.

**Ollama (required for RAG):** set **`OLLAMA_BASE_URL`** and **`SPRING_AI_OLLAMA_BASE_URL`** in **`rag-service/.env`** to wherever the HTTP API listens (host: `http://host.docker.internal:11434`, in-stack service: `http://ollama:11434`, another machine: `http://192.168.x.x:11434`). Compose maps both into **`backend`** and **`backend-dev`**. If Ollama is unreachable, logs show `ResourceAccessException` on `/api/embed` and `/api/chat`; Docker liveness can still be green because model readiness is checked by Actuator readiness and product operations, not by the container healthcheck.

**Optional in-stack Ollama:** requires NVIDIA Container Toolkit. Set `OLLAMA_BASE_URL=http://ollama:11434` and `SPRING_AI_OLLAMA_BASE_URL=http://ollama:11434` in `rag-service/.env`, then run:

```bash
./docker/scripts/docker-compose.sh config prod --obs --ollama --no-env-prompt
./docker/scripts/up.sh prod --obs --ollama --no-env-prompt
```

`--gpu` and `--ollama` are aliases for the in-stack Ollama path. If NVIDIA runtime is unavailable, the wrapper warns and does **not** activate the `ollama` profile. To use GPU on the host (or any Ollama outside the Compose stack) while still passing `--gpu` for other services, add `--ollama-remote`; that skips the local `ollama` profile and leaves the URL entirely to `rag-service/.env`. **Classifier GPU** requires explicit `--classifier-gpu` (merges `compose.gpu.yml`); the default demo stack uses CPU classifier even when an NVIDIA runtime is detected. Pull models via `ollama pull <model>` on the host, or `docker exec -it ollama ollama pull <model>` when Ollama runs in Docker.

**Prod-local host ports:** `compose.prod-host-ports.yml` publishes backend `9000` and classifier `8000` for integration/API smoke while the browser entry remains reverse-proxy `80`.

**Docker health checks:** `backend` container health uses `/actuator/health/liveness` so the stack can start before host Ollama models are ready; use `/actuator/health/readiness` and `local-demo-smoke.sh` for model readiness. `classifier-service` probes `/health` via curl. Use `/actuator/prometheus`, model-registry checks, and a real chat/LAB request for demo evidence.

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

## Dev accounts (seeded users)

When running the backend with Spring profile **`dev`** (e.g. `./docker/scripts/up.sh dev --rag ...`), the backend seeds two users so you can use the UI immediately without manual DB edits:

- **Admin**: `admin@dev.local` / `dev`
- **User** (non-admin): `user@dev.local` / `dev`

Override these defaults via `rag-service/.env`:

- `RAG_DEV_SEED_ADMIN_EMAIL`, `RAG_DEV_SEED_ADMIN_PASSWORD`, `RAG_DEV_SEED_ADMIN_NAME`
- `RAG_DEV_SEED_USER_EMAIL`, `RAG_DEV_SEED_USER_PASSWORD`, `RAG_DEV_SEED_USER_NAME`

These accounts are **dev-only**; they are not created when running with profile `prod`.

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

When files under `docker/` change, [`.github/workflows/docker-compose-ci.yml`](../.github/workflows/docker-compose-ci.yml) runs on **pull requests** to **`dev`**, **`main`**, or **`master`**, and on **pushes** to **`main`** or **`master` only** (not on `push` to `dev`, to avoid duplicate runs when a PR is open). It does **not** run if you only change application code outside `docker/`.

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

Optional path for machines with **NVIDIA Container Toolkit**. Adds **`ollama`** built from `ollama/` with **NVIDIA GPU**. Before using this path, set `OLLAMA_BASE_URL=http://ollama:11434` and `SPRING_AI_OLLAMA_BASE_URL=http://ollama:11434` in `rag-service/.env`. The backend may pull missing models on startup (`rag.ollama.auto-pull-enabled`, default `true`); first start can take several minutes.

Example:

```bash
./docker/scripts/docker-compose.sh config prod --ollama --no-env-prompt
./docker/scripts/up.sh prod --ollama --no-env-prompt
```

### Prod-local (hardening + reverse proxy)

Includes: `reverse-proxy` (HTTP/HTTPS) and `compose.prod.yml` so internal services do not publish host ports.
Observability is opt-in with `--obs`; plain `prod` does not start Jaeger/Prometheus/Grafana/OTEL.

Reverse-proxy defaults in this branch:

- API/product prefix: `/api/v5/**` routed to backend
- App/static routes: `/` and `/_next/**` routed to webapp
- API error contract for gateway failures: JSON body (not default HTML) on API locations
- Upload/body defaults: `API_CLIENT_MAX_BODY_SIZE=50m` (keep aligned with Spring multipart limits)
- HTTPS redirect: controlled with `REVERSE_PROXY_ENFORCE_HTTPS` (`0` by default for local/demo smoke evidence; set `1` when TLS redirect is required)

Start / stop:

```bash
./docker/scripts/docker-compose.sh config prod --no-env-prompt
./docker/scripts/up.sh prod --no-env-prompt
./docker/scripts/build.sh prod   # optional: build images with same -f chain as up
./docker/scripts/down.sh         # or: ./docker/scripts/down.sh prod [--all] ...
# Dev (incl. backend-dev): ./docker/scripts/down.sh dev [--all|...] with the same flags as up dev
```

Options:

- Use `./docker/scripts/up.sh prod --obs --no-env-prompt` to include `compose.obs.yml` and **`--profile observability`** (OTEL, Jaeger, Prometheus, Grafana).
- Use `./docker/scripts/up.sh prod --obs --obs-private --no-env-prompt` when you need observability but do not want Jaeger/Prometheus/Grafana published on host ports.
- `--gpu` or `--ollama`: adds **`--profile ollama`** only when the NVIDIA runtime is available (requires `ollama/.env` and NVIDIA Container Toolkit).
- `--volumes` (only `down.sh`): also remove named volumes

## Demo evidence capture

Use the official local/demo stack above, then capture evidence without committing secrets:

```bash
mkdir -p .cursor/context/evidence/docker-observability
docker ps > .cursor/context/evidence/docker-observability/docker-ps.txt
docker compose -f docker/docker-compose.yml -f docker/compose.obs.yml -f docker/compose.prod.yml \
  --profile observability \
  --env-file db/.env \
  --env-file classifier-service/.env \
  --env-file rag-service/.env \
  --env-file webapp/.env \
  --env-file observability/.env \
  logs --no-color backend > .cursor/context/evidence/docker-observability/backend.log
curl -sf http://127.0.0.1:${REVERSE_PROXY_HTTP_PORT:-80}/actuator/health > .cursor/context/evidence/docker-observability/backend-health.json
curl -sf http://127.0.0.1:${REVERSE_PROXY_HTTP_PORT:-80}/actuator/prometheus > .cursor/context/evidence/docker-observability/backend-prometheus.txt
curl -sf http://127.0.0.1:${PROMETHEUS_PORT:-9090}/-/healthy > .cursor/context/evidence/docker-observability/prometheus-health.txt
curl -sf http://127.0.0.1:${GRAFANA_PORT:-3000}/api/health > .cursor/context/evidence/docker-observability/grafana-health.json
curl -sf http://127.0.0.1:${JAEGER_UI_PORT:-16686}/ > .cursor/context/evidence/docker-observability/jaeger-root.html
```

For screenshots, open Grafana and Jaeger at the URLs printed by `up.sh prod --obs`. Generate at least one Chat or Lab request first, then capture the Grafana dashboard and the Jaeger trace detail. Record the trace ID shown in the UI or in backend logs. Do not store real passwords, JWTs, cookies, or OAuth secrets in evidence files.

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
   - See [`../docker/scripts/README.md`](../docker/scripts/README.md) (section **Smoke test**), then run the main checks:
     - `curl -s http://<host>:9000/actuator/health`
     - `curl -s http://<host>:8000/health` (if the classifier port is reachable)
     - Authenticated product smoke (get JWT, then call stable product endpoints like `GET {product}/config/schema` or `GET {product}/presets`)

> Note: if the backend is not exposed directly (only via reverse proxy), use the reverse-proxy published port (`REVERSE_PROXY_HTTP_PORT` defaults to **80** in `compose.prod.yml`; HTTPS uses `REVERSE_PROXY_HTTPS_PORT`, default **8443** until TLS on **443** is wired).

### HTTPS certificate policy (local/prod-like)

- The reverse-proxy image generates a self-signed certificate by default for local/prod-like testing.
- You can provide certificate paths with:
  - `TLS_CERT_PATH`
  - `TLS_KEY_PATH`
- Do not commit certificate files or private keys to this repository.
- For production issuance/renewal with external accounts (ACME, cloud certificates), document operational steps outside this branch scope.

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
