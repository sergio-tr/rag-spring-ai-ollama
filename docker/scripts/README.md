# Docker and environment scripts

**Location:** `docker/scripts/` — canonical implementations for Compose orchestration and `.env` generation. All command examples assume the **repository root** as the current directory, on **Linux** or **WSL2** (`bash`). **GitHub Actions** uses the same family of commands on `ubuntu-*` runners.

## Full stack verification (tests + coverage + Docker + integration)

Canonical script: [`../../tests/full-stack-verify.sh`](../../tests/full-stack-verify.sh).

Runs **all** automated checks in order:

1. `rag-service`: `./mvnw verify` (JaCoCo line coverage ≥ 80%). Uses the **same image definition as the backend**: `docker build --target build -f rag-service/Dockerfile` (JDK from `RAG_JAVA_JDK_BASE_IMAGE` in `rag-service/.env`), then a container with the repo mounted runs `mvnw verify` (named volume `rag-m2-cache` for `~/.m2`). No JDK on the host unless you force local Maven: `MAVEN_ON_HOST=1`. Build-stage tag: `RAG_BUILD_IMAGE_TAG` (default `rag-service-build:local`).
2. `classifier-service`: `pytest tests/` (coverage ≥ 80% per `pytest.ini`).
3. Docker: `docker-compose.yml` + `compose.obs.yml` + `compose.gpu.yml` and **profiles** `observability`, `logs`, `ollama` (and `infra` when enabled), matching `./docker/scripts/up.sh` full stack.
4. `tests/integration`: `INTEGRATION_CHECK_OBS=1 pytest tests/integration` (requires observability URLs on localhost).

**JaCoCo:** `rag-service/target/site/jacoco/index.html` shows **code coverage**, not a test list. Packages excluded in `rag-service/pom.xml` (e.g. `tool/**`) do not appear in that report. Use the HTML report for **≥80% lines per package**.

| Script | Platform |
| --- | --- |
| [`tests/full-stack-verify.sh`](../../tests/full-stack-verify.sh) | **Linux / WSL2** (canonical) |

Examples:

```bash
chmod +x tests/full-stack-verify.sh
./tests/full-stack-verify.sh
SKIP_DOCKER=1 ./tests/full-stack-verify.sh
MAVEN_ON_HOST=1 ./tests/full-stack-verify.sh   # host mvnw (JDK 21)
```

**Note:** Optional tiers use **Compose profiles** in `docker-compose.yml` (`observability`, `logs`, `infra`, `ollama`, `cadvisor`). `compose.gpu.yml` adds NVIDIA to the classifier when the runtime is available. They target **Linux** host semantics.

## Create default .env

Scripts in **this directory** (`create-env-*.sh`, `create-env-all.sh`).

| Script | Creates | Purpose |
| --- | --- | --- |
| `create-env-db.sh` | `db/.env` | Postgres (port, user, password, DB). Used by main compose. |
| `create-env-observability.sh` | `observability/.env` | Base images (OTEL, Jaeger, Prometheus, Grafana), Grafana password, ports. Used by `compose.obs.yml`. |
| `create-env-rag-service.sh` | `rag-service/.env` | Backend: base images (RAG_JAVA_*), SERVER_PORT, BACKEND_PORT, DB URL, Ollama, classifier URL. For build and local runs. |
| `create-env-classifier-service.sh` | `classifier-service/.env` | Classifier: base image, PORT, MODELS_DIR, DATA_DIR, CLASSIFIER_SERVICE_PORT. For build and local runs. |
| `create-env-ollama.sh` | `ollama/.env` | Ollama in Docker: base image (OLLAMA_BASE_IMAGE), OLLAMA_PORT. Used with **`--profile ollama`** (`--gpu` / `--ollama`). |
| `create-env-webapp.sh` | `webapp/.env` | Next.js public env and API base URL for local/Docker runs. |
| `create-env-all.sh` | All of the above | Runs every `create-env-*.sh` in this folder, then `sync_env_from_examples.py`. |

Example:

```bash
./docker/scripts/create-env-all.sh
```

If a `.env` already exists, it is not overwritten. Use `--force` to overwrite:

```bash
./docker/scripts/create-env-db.sh --force
```

Default values are in each component's `.env.example`. After creating the `.env` files, edit them to change ports, URLs, or secrets.

### Sync missing keys from `.env.example`

[`sync_env_from_examples.py`](sync_env_from_examples.py) appends any `KEY=value` present in `.env.example` but missing from an existing `.env` (does not overwrite values). Use `--dry-run` to preview.

```bash
python3 ./docker/scripts/sync_env_from_examples.py
python3 ./docker/scripts/sync_env_from_examples.py --dry-run
```

Requires **Python 3** and **PyYAML** (same as the compose helpers below).

### Compose inventory and policy guard

| Script | Role |
| --- | --- |
| [`compose_inventory.py`](compose_inventory.py) | Lists every `docker/*.yml` and each service with `image` vs `build` (per-file, not merged stacks). |
| [`compose_guard.py`](compose_guard.py) | Policy rules: no `image:` in `docker/*.yml`, valid `build:` blocks, optional env/port/healthcheck strictness. Full run may report **violations** for `environment_literal` / `healthcheck_*` during migration. **CI** uses `--only-rules image_forbidden,yaml_error,build_invalid,build_missing_context,build_missing_dockerfile` (see [`.github/workflows/docker-compose-ci.yml`](../../.github/workflows/docker-compose-ci.yml)). |

**Mailpit** (`docker-compose.yml`, profile `dev-mail`): upstream image is pinned via **`docker/mailpit/Dockerfile`** and build arg **`MAILPIT_BASE_IMAGE`** (default `axllent/mailpit:v1.29.7`), because Compose services must use **`build:`** only — never a top-level **`image:`**.

```bash
python3 ./docker/scripts/compose_inventory.py
python3 ./docker/scripts/compose_inventory.py --format markdown
python3 ./docker/scripts/compose_guard.py
python3 ./docker/scripts/compose_guard.py --only-rules image_forbidden,yaml_error,build_invalid,build_missing_context,build_missing_dockerfile
python3 ./docker/scripts/compose_guard.py --json
```

Exit code of `compose_guard.py` is **1** when any **enforced** rule fails (default: all rules).

## Interactive .env creation: `set-env.sh`

`set-env.sh` only asks whether to create each component's `.env` (db, observability, rag-service, classifier-service, ollama, **webapp**). It does **not** run Docker Compose — use `./docker/scripts/up.sh dev` or `./docker/scripts/up.sh prod` afterward.

### Unified script: `docker-compose.sh` (build / up / down)

Entry point: [`docker-compose.sh`](docker-compose.sh). Shortcuts: [`up.sh`](up.sh), [`build.sh`](build.sh), [`down.sh`](down.sh) (same directory).

```text
./docker/scripts/docker-compose.sh <build|config|up|down> <dev|prod> [env options] [stack options]
```

- **`down`**: `<dev|prod>` is optional and defaults to **`prod`** (same behaviour as `./docker/scripts/down.sh` with no mode).
- **`up.sh`** / **`build.sh`** call `docker-compose.sh`; **`down.sh`** forwards to `docker-compose.sh down`, defaulting to `prod` when the first argument is not `dev`/`prod`. **`./docker/scripts/down.sh dev [--all|...]`** uses the same `-f` chain as `up dev` (includes **backend-dev** with `--rag` / `--all`).

**Build** uses `docker compose build` with the **same** `-f` / `--env-file` list as the matching `up` (env setup runs for `build` and `up`, not for `down`):

```bash
./docker/scripts/build.sh dev --all
./docker/scripts/build.sh prod --obs --gpu
./docker/scripts/docker-compose.sh build dev --env db
```

**Config validation** uses the same `-f`, profile, and env-file chain as `up`, but never prompts or starts containers:

```bash
./docker/scripts/docker-compose.sh config prod --obs --no-env-prompt
./docker/scripts/docker-compose.sh config prod --obs --obs-private --no-env-prompt
./docker/scripts/docker-compose.sh config prod --obs --ollama --no-env-prompt
./docker/scripts/docker-compose.sh config dev --rag --obs --no-env-prompt
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

**Hybrid dev infra**: `./docker/scripts/up.sh dev [options]`. **`--all`** enables `--gpu --obs --classifier --logs --infra --rag` (Loki/Promtail + node-exporter + **backend-dev** in Docker; cAdvisor is opt-in **`--profile cadvisor`**). Use `./docker/scripts/up.sh dev --all --down` to stop and remove volumes (`-v`).

**`--rag` (dev only):** starts **`backend-dev`** and the **`webapp`** in Docker (plus `classifier-service` when needed): `rag-service/` volume, compile loop, and **Spring Boot DevTools**. **`--proxy`** (only with `--rag`) publishes **nginx** like prod (`/` → webapp, `/api/*` → backend-dev); default host HTTP port is **`80`** (`REVERSE_PROXY_DEV_HTTP_PORT`). Without `--proxy`, the webapp uses **`WEBAPP_HTTP_PORT` default 80** (`80:3000`); Grafana uses **`GRAFANA_PORT` default 3000**. With **`--proxy`**, leave the webapp API base URL empty for same-origin nginx. **`prod`** always includes `reverse-proxy` (`compose.prod.yml`). Set **`SPRING_AI_OLLAMA_BASE_URL=http://ollama:11434`** only when using in-stack Ollama (`--gpu` / `--ollama`). Optional variable: **`RAG_DEV_POLL_INTERVAL`**.

**Compose layout:** `docker-compose.yml` (core + optional services behind **profiles**: `observability`, `logs`, `infra`, `ollama`, `cadvisor`, and **`rag`** for `backend-dev`). Overlays: `compose.dev.yml` (includes webapp ordering for `--rag`), `compose.dev-proxy.yml` (`--rag --proxy`, adds **`--profile proxy`**), `compose.obs.yml` (Spring OTLP for `backend` / `classifier-service`), `compose.gpu.yml`, `compose.rag-dev-obs.yml` (`--rag --obs`), `compose.prod.yml`, and `compose.prod-obs.yml` only with `prod --obs --obs-private`. Ollama HTTP URL is always from **`rag-service/.env`**; **`--ollama-remote`** only affects whether the **`ollama`** profile is started together with **`--gpu`/`--ollama`**.

**Flags**: `dev`: `--all`, `--gpu`, `--ollama`, `--obs`, `--classifier`, `--logs`, `--infra`, `--rag`, **`--proxy`**, `--down`, `--volumes`. `prod`: `--all`, `--obs`, `--obs-private`, `--gpu`, `--ollama`, `--logs`, `--infra` (nginx always). **`down.sh`**: same flags as `up` for `dev` or `prod`. For **`down dev`** / **`build dev`**, pass the **same** flags as `up dev` (including `--rag`, **`--proxy`**, `--all`).

## Running Compose manually

From `docker/` (env files are optional; compose uses defaults if a file is missing):

- Main stack: `docker compose --env-file ../db/.env --env-file ../classifier-service/.env --env-file ../rag-service/.env up -d`
- With observability: add `-f compose.obs.yml`, **`--profile observability`**, and `--env-file ../observability/.env`
- With Ollama in Docker (GPU): add `-f compose.gpu.yml`, **`--profile ollama`**, and `--env-file ../ollama/.env` (see `ollama/README.md`), or use `./docker/scripts/up.sh prod --ollama`. Host-Ollama is the default recommended demo mode and does not need the `ollama` profile.

## Prod local (hardening) — `up` / `down` / `build`

**Prod local** starts the stack with `compose.prod.yml` (reverse proxy + hardened ports for internal services).

- Start: `./docker/scripts/up.sh prod [--all] [--obs] [--obs-private] [--gpu| --ollama] [--logs] [--infra]`
- Build images: `./docker/scripts/build.sh prod` with the **same** flags as `up prod`
- Stop: `./docker/scripts/down.sh` with the **same** flags you used for `up` (e.g. `--all` = obs + GPU + logs + infra + `-v`)

Notes:

- **`--obs`** adds `compose.obs.yml` and **`--profile observability`** (opt-in). In local/demo mode, Prometheus, Grafana, and Jaeger publish host ports so screenshots can be captured.
- **`--obs-private`** adds `compose.prod-obs.yml` on top of `--obs` and keeps Prometheus, Grafana, Jaeger, and OTEL ports internal.
- `--gpu` and `--ollama` → same: **`--profile ollama`** if the Docker host has the NVIDIA runtime.
- `--logs` → **`--profile logs`** (Loki + Promtail); `--infra` → **`--profile infra`** (node-exporter). cAdvisor: **`--profile cadvisor`** (see `docker/README.md`).

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

### Local demo smoke (script)

For the official prod-local demo path (host-Ollama by default):

```bash
./docker/scripts/local-demo-smoke.sh --obs
```

The script runs `docker-compose.sh config ...`, starts the prod-local stack unless `--skip-up` is passed, prints `docker compose ps`, checks webapp, backend Actuator health, classifier health, `/actuator/prometheus`, host Ollama model tags, and Prometheus/Grafana/Jaeger when `--obs` is enabled. Use `--obs-private` when observability UIs must stay internal; in that mode localhost UI checks are skipped unless `DEMO_SMOKE_PROMETHEUS_URL`, `DEMO_SMOKE_GRAFANA_URL`, and `DEMO_SMOKE_JAEGER_URL` point to forwarded ports. The authenticated model-registry check runs when `DEMO_SMOKE_EMAIL` and `DEMO_SMOKE_PASSWORD` are supplied; otherwise it is skipped without failing so no secrets are required in docs.

Optional in-stack Ollama:

```bash
./docker/scripts/local-demo-smoke.sh --obs --ollama
```

This requires NVIDIA Container Toolkit and `rag-service/.env` pointing to `http://ollama:11434`.

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

### 3. Authenticated product smoke

**Dev seeded accounts:** when the backend runs with profile **`dev`**, two accounts are available by default:

- Admin: `admin@dev.local` / `dev`
- User: `user@dev.local` / `dev`

Override in `rag-service/.env` via `RAG_DEV_SEED_*` (see `rag-service/.env.example`).

- Login to obtain a JWT:
  `curl -s -X POST http://localhost:9000/api/v5/auth/login -H "Content-Type: application/json" -d "{\"email\":\"admin@dev.local\",\"password\":\"dev\"}"`
- Use the JWT to call stable product endpoints (non-snapshot-dependent):
  - `GET /api/v5/config/schema`
  - `GET /api/v5/presets`

### Optional script (from repo root, stack running)

```bash
# classifier-service health
curl -sf http://localhost:8000/health && echo " classifier-service OK"

# Classify
curl -sf -X POST http://localhost:8000/classify -H "Content-Type: application/json" -d '{"query":"How many documents?"}' && echo " Classify OK"

# Authenticated product smoke (non-snapshot-dependent)
TOKEN="$(curl -sf -X POST http://localhost:9000/api/v5/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"dev@local.test","password":"dev"}' | python -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')"
curl -sf -H "Authorization: Bearer ${TOKEN}" "http://localhost:9000/api/v5/config/schema" >/dev/null && echo " Backend schema OK"
curl -sf -H "Authorization: Bearer ${TOKEN}" "http://localhost:9000/api/v5/presets" >/dev/null && echo " Backend presets OK"
```

Avoid using query paths as the basic connectivity smoke; keep smoke checks non-snapshot-dependent and authenticated.

## Pre-release validation pack (operator)

Before tagging a release or thesis snapshot, capture evidence for:

1. **Compose syntax:** `docker compose … config -q` for `docker-compose.yml` with **`--profile logs`** (and other profiles as needed), for `docker-compose.yml` + `compose.obs.yml` + **`--profile observability`**, and for `docker-compose.yml` + `compose.prod.yml` (same env-file pattern as CI — see [`.github/workflows/observability-smoke.yml`](../../.github/workflows/observability-smoke.yml)).
2. **Runtime:** [`rag-service/scripts/smoke-test.sh`](../../rag-service/scripts/smoke-test.sh) against the running backend; Actuator health/readiness.
3. **Deep stack (optional / nightly):** [`tests/full-stack-verify.sh`](../../tests/full-stack-verify.sh) — heavy; run manually or on a scheduled runner if not part of PR CI.

Canonical orchestration remains **`./docker/scripts/up.sh`** and **`./docker/scripts/docker-compose.sh`**; do not maintain one-off compose examples that diverge from those scripts ([`../README.md`](../README.md)).

## Repo layout note

`docker/scripts/` is the canonical location for Compose orchestration and `.env` generation. The file [`../../rag-service/scripts/up.sh`](../../rag-service/scripts/up.sh) **delegates** to `docker/scripts/up.sh` — it is not a second source of compose flags.
