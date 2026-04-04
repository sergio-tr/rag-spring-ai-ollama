# Docker and environment scripts

**Location:** `docker/scripts/` — canonical implementations for Compose orchestration and `.env` generation. All command examples assume the **repository root** as the current directory, on **Linux** or **WSL2** (`bash`). **GitHub Actions** uses the same family of commands on `ubuntu-*` runners.

## Full stack verification (tests + coverage + Docker + integration)

Canonical script: [`../../tests/full-stack-verify.sh`](../../tests/full-stack-verify.sh).

Runs **all** automated checks in order:

1. `rag-service`: `./mvnw verify` (JaCoCo line coverage ≥ 80%). Uses the **same image definition as the backend**: `docker build --target build -f rag-service/Dockerfile` (JDK from `RAG_JAVA_JDK_BASE_IMAGE` in `rag-service/.env`), then a container with the repo mounted runs `mvnw verify` (named volume `rag-m2-cache` for `~/.m2`). No JDK on the host unless you force local Maven: `MAVEN_ON_HOST=1`. Build-stage tag: `RAG_BUILD_IMAGE_TAG` (default `rag-service-build:local`).
2. `classifier-service`: `pytest tests/` (coverage ≥ 80% per `pytest.ini`).
3. Docker: `docker compose` with `docker-compose.yml` + `compose.obs.yml` + `compose.logs.yml` + `compose.gpu.yml` (classifier GPU; optional `compose.ollama-local-gpu.yml` when `--gpu` and no `--ollama-remote`).
4. `tests/integration`: `INTEGRATION_CHECK_OBS=1 pytest tests/integration` (requires observability URLs on localhost).

**JaCoCo:** `rag-service/target/site/jacoco/index.html` shows **code coverage**, not a test list. Packages excluded in `rag-service/pom.xml` (e.g. `tool/**`) do not appear in that report. Use the HTML report for **≥80% lines per package**.

| Script | Platform |
|--------|----------|
| [`tests/full-stack-verify.sh`](../../tests/full-stack-verify.sh) | **Linux / WSL2** (canonical) |
| `full-stack-verify.ps1` | Windows PowerShell only if you cannot use WSL2; not used in CI |

Examples:

```bash
chmod +x tests/full-stack-verify.sh
./tests/full-stack-verify.sh
SKIP_DOCKER=1 ./tests/full-stack-verify.sh
MAVEN_ON_HOST=1 ./tests/full-stack-verify.sh   # host mvnw (JDK 21)
```

**Note:** `compose.infra.yml` (node-exporter, cAdvisor) and `compose.gpu.yml` (classifier GPU) are optional; local Ollama with GPU uses `compose.ollama-local-gpu.yml`. They target **Linux** host semantics. The flow above uses **base + observability + Loki/Promtail** as the default “full” stack on Linux runners.

## Create default .env

Scripts in **this directory** (`create-env-*.sh`, `create-env-all.sh`).

| Script | Creates | Purpose |
|--------|---------|---------|
| `create-env-db.sh` | `db/.env` | Postgres (port, user, password, DB), base image (POSTGRES_BASE_IMAGE). Used by main compose. |
| `create-env-observability.sh` | `observability/.env` | Base images (OTEL, Jaeger, Prometheus, Grafana), Grafana password, ports. Used by `compose.obs.yml`. |
| `create-env-rag-service.sh` | `rag-service/.env` | Backend: base images (RAG_JAVA_*), SERVER_PORT, BACKEND_PORT, DB URL, Ollama, classifier URL. For build and local runs. |
| `create-env-classifier-service.sh` | `classifier-service/.env` | Classifier: base image, PORT, MODELS_DIR, DATA_DIR, CLASSIFIER_SERVICE_PORT. For build and local runs. |
| `create-env-ollama.sh` | `ollama/.env` | Ollama in Docker: base image (OLLAMA_BASE_IMAGE), OLLAMA_PORT. Used when starting `ollama` via `compose.ollama-local-gpu.yml` (`--gpu`). |
| `create-env-webapp.sh` | `webapp/.env` | Next.js public env and API base URL for local/Docker runs. |
| `create-env-all.sh` | All of the above | Runs every `create-env-*.sh` in this folder. |

Example:

```bash
./docker/scripts/create-env-all.sh
```

If a `.env` already exists, it is not overwritten. Use `--force` to overwrite:

```bash
./docker/scripts/create-env-db.sh --force
```

Default values are in each component's `.env.example`. After creating the `.env` files, edit them to change ports, URLs, or secrets.

## Interactive .env creation: `set-env.sh`

`set-env.sh` only asks whether to create each component's `.env` (db, observability, rag-service, classifier-service, ollama, **webapp**). It does **not** run Docker Compose — use `./docker/scripts/up.sh dev` or `./docker/scripts/up.sh prod` afterward.

### Unified script: `docker-compose.sh` (build / up / down)

Entry point: [`docker-compose.sh`](docker-compose.sh). Shortcuts: [`up.sh`](up.sh), [`build.sh`](build.sh), [`down.sh`](down.sh) (same directory).

```text
./docker/scripts/docker-compose.sh <build|up|down> <dev|prod> [env options] [stack options]
```

- **`down`**: `<dev|prod>` is optional and defaults to **`prod`** (same behaviour as `./docker/scripts/down.sh` with no mode).
- **`up.sh`** / **`build.sh`** call `docker-compose.sh`; **`down.sh`** forwards to `docker-compose.sh down`, defaulting to `prod` when the first argument is not `dev`/`prod`. **`./docker/scripts/down.sh dev [--all|...]`** uses the same `-f` chain as `up dev` (includes **backend-dev** with `--rag` / `--all`).

**Build** uses `docker compose build` with the **same** `-f` / `--env-file` list as the matching `up` (env setup runs for `build` and `up`, not for `down`):

```bash
./docker/scripts/build.sh dev --all
./docker/scripts/build.sh prod --obs --gpu
./docker/scripts/docker-compose.sh build dev --env db
```

**Stop dev stack** (same compose chain as `up dev`): `./docker/scripts/up.sh dev --down` or `./docker/scripts/docker-compose.sh down dev [same flags as up dev]`.

```bash
./docker/scripts/set-env.sh
```

**Unified up** (`./docker/scripts/up.sh <dev|prod>`) can optionally create `.env` files before `compose up`:

- **`--env <name>`** — run the matching `create-env-*.sh` once (repeatable). Names: `db`, `obs`, `rag`, `classifier`, `ollama`, `webapp`, `all`. Comma-separated values in one `--env` are allowed (e.g. `--env db,rag`).
- **`--no-env-prompt`** — skip the interactive question below.
- **(interactive TTY, no `--env`)** — prompts: `Run interactive .env setup (set-env.sh)? [y/N]`

Env setup runs **before** `compose up`, not before `dev --down`.

**Examples:**

```bash
./docker/scripts/up.sh dev --env db --env rag
./docker/scripts/up.sh prod --env all --no-env-prompt
```

**Hybrid dev infra**: `./docker/scripts/up.sh dev [options]`. **`--all`** enables `--gpu --obs --classifier --logs --infra --rag` (Loki/Promtail + node-exporter/cAdvisor + **backend-dev** en Docker). Use `./docker/scripts/up.sh dev --all --down` to stop and remove volumes (`-v`).

**`--rag` (dev only):** arranca **`backend-dev`** y la **`webapp`** en Docker (además de `classifier-service` si hace falta): volumen `rag-service/`, recompilación en bucle y **Spring Boot DevTools**. **`--proxy`** (solo con `--rag`): publica **nginx** como en prod (`/` → webapp, `/api/*` → backend-dev); HTTP por defecto en el host **`80`** (`REVERSE_PROXY_DEV_HTTP_PORT`). Sin `--proxy`, la web usa **`WEBAPP_HTTP_PORT` por defecto 80** (`80:3000`); Grafana usa **`GRAFANA_PORT` por defecto 3000**. Con **`--proxy`**, deja esa URL vacía para mismo origen vía nginx. **`prod`** siempre incluye `reverse-proxy` (`compose.prod.yml`). Ajusta **`SPRING_AI_OLLAMA_BASE_URL=http://ollama:11434`** con Ollama en compose (`--gpu`). Variable opcional: **`RAG_DEV_POLL_INTERVAL`**.

**Compose files** (see `docker/`): `compose.dev.yml`, `compose.dev-webapp.yml` (`--rag`), `compose.dev-proxy.yml` (`--rag --proxy`), `compose.obs.yml`, `compose.logs.yml`, `compose.infra.yml`, `compose.gpu.yml`, `compose.ollama-local-gpu.yml` / `compose.ollama-local-gpu.dev.yml`, `compose.ollama-remote.yml` / `compose.ollama-remote.dev.yml`, `compose.rag-dev-obs.yml`, `compose.prod.yml` (siempre nginx).

**Flags**: `dev`: `--all`, `--gpu`, `--ollama`, `--obs`, `--classifier`, `--logs`, `--infra`, `--rag`, **`--proxy`**, `--down`, `--volumes`. `prod`: `--all`, `--obs`, `--gpu`, `--ollama`, `--logs`, `--infra` (nginx siempre). **`down.sh`**: mismos flags que `up` para `dev` o `prod`. For **`down dev`** / **`build dev`**, pass the **same** flags as `up dev` (including `--rag`, **`--proxy`**, `--all`).

## Running Compose manually

From `docker/` (env files are optional; compose uses defaults if a file is missing):

- Main stack: `docker compose --env-file ../db/.env --env-file ../classifier-service/.env --env-file ../rag-service/.env up -d`
- With observability: add `-f compose.obs.yml` and `--env-file ../observability/.env`
- With Ollama in Docker (GPU): add `-f compose.gpu.yml -f compose.ollama-local-gpu.yml` and `--env-file ../ollama/.env` (see `ollama/README.md`), or use `./docker/scripts/up.sh prod --gpu`.

## Prod local (hardening) — `up` / `down` / `build`

**Prod local** starts the stack with `compose.prod.yml` (reverse proxy + hardened ports for internal services).

- Start: `./docker/scripts/up.sh prod [--all] [--obs] [--gpu|--ollama] [--logs] [--infra]`
- Build images: `./docker/scripts/build.sh prod` with the **same** flags as `up prod`
- Stop: `./docker/scripts/down.sh` with the **same** flags you used for `up` (e.g. `--all` = obs + GPU + logs + infra + `-v`)

Notes:
- **`--obs`** adds `compose.obs.yml` (opt-in). Plain `up.sh prod` is base + `compose.prod.yml` only (reverse proxy, hardened ports).
- `--gpu` and `--ollama` → same: starts `ollama` (NVIDIA GPU) if the Docker host supports it.
- `--logs` → Loki + Promtail; `--infra` → node-exporter + cAdvisor (host paths; see `docker/README.md`).

## Database backup / restore

See [`../../db/scripts/README.md`](../../db/scripts/README.md).

## Normalize shell scripts (CRLF → LF)

[`normalize-sh-lf.sh`](normalize-sh-lf.sh) strips `\r` from all `*.sh` under the repo (run from repo root):

```bash
./docker/scripts/normalize-sh-lf.sh
```

## Smoke test

After `docker compose up -d`, verify that services respond and that an RAG query goes through the classifier.

For a **repeatable pytest suite** (health + classify + backend + optional observability), see [`../../tests/integration/README.md`](../../tests/integration/README.md).

### Technical E2E (script)

To automate the flow (base + optional observability/GPU):

- `./tests/e2e/e2e-technical-compose.sh` (base stack)
- `./tests/e2e/e2e-technical-compose.sh --obs` (Jaeger/Prometheus/Grafana/OTEL)
- `./tests/e2e/e2e-technical-compose.sh --gpu` (Ollama in container with GPU; host must support GPU)
- `./tests/e2e/e2e-technical-compose.sh --obs --gpu` (both)

By default the script brings the stack up and then tears it down; use `--keep` to leave it running.

### 1. Health checks

- **classifier-service:** `curl -s http://localhost:8000/health` → should return `{"status":"ok","model":"loaded"}` (or `"not_loaded"` if the model failed to load).
- **Backend:** `curl -s http://localhost:9000/actuator/health` (if actuator is enabled) or any endpoint that returns 200.

### 2. Classifier

- **Direct to classifier-service:**  
  `curl -s -X POST http://localhost:8000/classify -H "Content-Type: application/json" -d "{\"query\": \"How many documents are there?\"}"`  
  → should return something like `{"queryType":"COUNT_DOCUMENTS"}` (or another valid type).

### 3. RAG query

Set `RAG_API_LEGACY_BASE_PATH` in your shell to the same value as `rag.api.legacy-base-path` in the running backend (see `rag-service` configuration).

- **Backend (classification happens internally):**  
  `curl -s "http://localhost:9000${RAG_API_LEGACY_BASE_PATH}/query?question=How%20many%20documents%20are%20there?"`  
  → should return 200 and a response body. If the classifier is unavailable, the backend may still respond using an LLM fallback.

### Optional script (from repo root, stack running)

```bash
# classifier-service health
curl -sf http://localhost:8000/health && echo " classifier-service OK"

# Classify
curl -sf -X POST http://localhost:8000/classify -H "Content-Type: application/json" -d '{"query":"How many documents?"}' && echo " Classify OK"

# Backend query (needs DB data and Ollama for a full answer)
curl -sf -o /dev/null -w "%{http_code}" "http://localhost:9000${RAG_API_LEGACY_BASE_PATH}/query?question=test" && echo " Backend query OK"
```

If the DB is empty or Ollama is down, the backend may still return 200 with an error message in the body; the minimum smoke check is HTTP 200.

## Repo layout note (`scripts/` at root)

The top-level [`../../scripts/README.md`](../../scripts/README.md) indexes automation entry points; use paths under `docker/scripts/`, `db/scripts/`, `rag-service/scripts/`, and `tests/` as listed there.
