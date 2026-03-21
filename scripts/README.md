# Scripts

Scripts to generate default `.env` files for each component and to run Docker Compose interactively. Run from the **repository root**.

## Full stack verification (tests + coverage + Docker + integration)

Runs **all** automated checks in order:

1. `rag-service`: `./mvnw verify` (JaCoCo line coverage ≥ 80%). Uses the **same image definition as the backend**: `docker build --target build -f rag-service/Dockerfile` (JDK from `RAG_JAVA_JDK_BASE_IMAGE` in `rag-service/.env`), then a container with the repo mounted runs `mvnw verify` (named volume `rag-m2-cache` for `~/.m2`). No JDK on the host unless you force local Maven: `MAVEN_ON_HOST=1` / `-UseLocalMaven`. Build-stage tag: `RAG_BUILD_IMAGE_TAG` (default `rag-service-build:local`).
2. `classifier-service`: `pytest tests/` (coverage ≥ 80% per `pytest.ini`).
3. Docker: `docker compose` with `docker-compose.yml` + `compose.obs.yml` + `compose.logs.yml` + `compose.ollama.yml` (Ollama in Docker so the backend does not depend on `host.docker.internal`).
4. `tests/integration`: `INTEGRATION_CHECK_OBS=1 pytest tests/integration` (requires observability URLs on localhost).

**JaCoCo:** `target/site/jacoco/index.html` muestra **cobertura del código**, no un listado de tests. Los paquetes excluidos en `rag-service/pom.xml` (p. ej. `tool/**`) no aparecen en ese informe aunque tengan tests en Surefire. Tras `mvn verify` en `rag-service`, puedes comprobar **≥80% líneas en cada paquete del informe** con:

`python scripts/check-rag-jacoco-packages.py`

Reintentos acotados (no infinitos): `.\scripts\verify-rag-coverage-loop.ps1` (por defecto 50 intentos).

| Script | Platform |
|--------|----------|
| [full-stack-verify.ps1](full-stack-verify.ps1) | Windows PowerShell (repo root) |
| [full-stack-verify.sh](full-stack-verify.sh) | Linux/macOS/Git Bash |

Examples:

```powershell
.\scripts\full-stack-verify.ps1
.\scripts\full-stack-verify.ps1 -SkipDocker       # only Maven + classifier + integration (stack already up)
```

```bash
chmod +x scripts/full-stack-verify.sh
./scripts/full-stack-verify.sh
SKIP_DOCKER=1 ./scripts/full-stack-verify.sh
MAVEN_ON_HOST=1 ./scripts/full-stack-verify.sh   # host mvnw (JDK 21)
```

**Note:** `compose.infra.yml` (node-exporter, cAdvisor) and `compose.ollama-gpu.yml` (Ollama GPU) are optional; they use host paths that may not work on every OS. The scripts above use **base + observability + Loki/Promtail** as the broadest portable “full” stack.

## Create default .env

| Script | Creates | Purpose |
|--------|---------|---------|
| `create-env-db.sh` | `db/.env` | Postgres (port, user, password, DB), base image (POSTGRES_BASE_IMAGE). Used by main compose. |
| `create-env-observability.sh` | `observability/.env` | Base images (OTEL, Jaeger, Prometheus, Grafana), Grafana password, ports. Used by `compose.obs.yml`. |
| `create-env-rag-service.sh` | `rag-service/.env` | Backend: base images (RAG_JAVA_*), SERVER_PORT, BACKEND_PORT, DB URL, Ollama, classifier URL. For build and local runs. |
| `create-env-classifier-service.sh` | `classifier-service/.env` | Classifier: base image, PORT, MODELS_DIR, DATA_DIR, CLASSIFIER_SERVICE_PORT. For build and local runs. |
| `create-env-ollama.sh` | `ollama/.env` | Ollama (GPU stack): base image (OLLAMA_BASE_IMAGE), OLLAMA_PORT. Used by `compose.ollama-gpu.yml`. |
| `create-env-all.sh` | All five above | Runs all five create-env-* scripts. |

Example:

```bash
./scripts/create-env-all.sh
```

If a `.env` already exists, it is not overwritten. Use `--force` to overwrite:

```bash
./scripts/create-env-db.sh --force
```

Default values are in each component's `.env.example`. After creating the `.env` files, edit them to change ports, URLs, or secrets.

## Interactive .env creation: set-env.sh

`set-env.sh` only asks whether to create each component's `.env` (db, observability, rag-service, classifier-service, ollama). It does **not** run Docker Compose — use `./scripts/up.sh dev` or `./scripts/up.sh prod` afterward.

### Unified script: `docker-compose.sh` (build / up / down)

All compose file chains and env files are implemented in **`./scripts/docker-compose.sh`**:

```text
./scripts/docker-compose.sh <build|up|down> <dev|prod> [env options] [stack options]
```

- **`down`**: `<dev|prod>` is optional and defaults to **`prod`** (same behaviour as `./scripts/down.sh` with no mode).
- Thin wrappers: **`up.sh`** → `up`, **`build.sh`** → `build`, **`down.sh`** → `down prod` si el primer argumento no es `dev`/`prod`; **`./scripts/down.sh dev [--all|...]`** usa la misma cadena `-f` que `up dev` (incluye **backend-dev** con `--rag` / `--all`).

**Build** uses `docker compose build` with the **same** `-f` / `--env-file` list as the matching `up` (env setup runs for `build` and `up`, not for `down`):

```bash
./scripts/build.sh dev --all
./scripts/build.sh prod --obs --gpu
./scripts/docker-compose.sh build dev --env db
```

**Stop dev stack** (same compose chain as `up dev`): `./scripts/up.sh dev --down` or `./scripts/docker-compose.sh down dev [same flags as up dev]`.

```bash
./scripts/set-env.sh
```

**Unified up** (`./scripts/up.sh <dev|prod>`) can optionally create `.env` files before `compose up`:

- **`--env <name>`** — run the matching `create-env-*.sh` once (repeatable). Names: `db`, `obs`, `rag`, `classifier`, `ollama`, `all`. Comma-separated values in one `--env` are allowed (e.g. `--env db,rag`).
- **`--no-env-prompt`** — skip the interactive question below.
- **(interactive TTY, no `--env`)** — prompts: `Run interactive .env setup (set-env.sh)? [y/N]`

Env setup runs **before** `compose up`, not before `dev --down`.

**Examples:**

```bash
./scripts/up.sh dev --env db --env rag
./scripts/up.sh prod --env all --no-env-prompt
```

**Hybrid dev infra**: `./scripts/up.sh dev [options]`. **`--all`** enables `--gpu --obs --classifier --logs --infra --rag` (Loki/Promtail + node-exporter/cAdvisor + **backend-dev** en Docker). Use `./scripts/up.sh dev --all --down` to stop and remove volumes (`-v`).

**`--rag` (dev only):** arranca **`backend-dev`** (Spring en Docker): volumen del código `rag-service/`, recompilación en bucle y **Spring Boot DevTools** reinicia al cambiar `target/classes` (similar en espíritu a uvicorn `--reload` / Vite). Incluye `classifier-service` en Docker si aún no lo pediste con `--classifier`. Ajusta **`SPRING_AI_OLLAMA_BASE_URL=http://ollama:11434`** en `rag-service/.env` si Ollama va en el mismo compose. Variable opcional: **`RAG_DEV_POLL_INTERVAL`** (segundos entre comprobaciones de cambios, por defecto 2).

**Compose files** (see `docker/`): `compose.dev.yml` (`--classifier`), `compose.obs.yml` (`--obs`), `compose.logs.yml` (`--logs`), `compose.infra.yml` (`--infra`), `compose.ollama-gpu.yml` (`--gpu`) or `compose.ollama.yml` (`--ollama`, CPU Ollama — mutually exclusive with `--gpu`), `compose.rag-dev.yml` (`--rag`), `compose.rag-dev-obs.yml` (solo si `--rag` y `--obs`: OTLP al collector), `compose.prod.yml` (prod only).

**Flags**: `dev`: `--all`, `--gpu`, `--ollama`, `--obs`, `--classifier`, `--logs`, `--infra`, `--rag`, `--down`, `--volumes`. `prod`: `--all`, `--obs`, `--gpu`, `--ollama`, `--logs`, `--infra`. **`down.sh`**: por defecto **prod** (`./scripts/down.sh` = `down prod`); **`./scripts/down.sh dev ...`** igual que `up dev` (para parar **backend-dev**, mismos flags que al subir). **`build.sh`**: same toggles as `up.sh` for the chosen mode (runs `docker compose build`). For **`down dev`** / **`build dev`**, pass the **same** flags as `up dev` (including `--rag` / `--all`) so the compose file chain matches.

## Running Compose manually

From `docker/` (env files are optional; compose uses defaults if a file is missing):

- Main stack: `docker compose --env-file ../db/.env --env-file ../classifier-service/.env --env-file ../rag-service/.env up -d`
- With observability: add `-f compose.obs.yml` and `--env-file ../observability/.env`
- With GPU: add `-f compose.ollama-gpu.yml` and `--env-file ../ollama/.env` (Ollama is built from `ollama/Dockerfile`; see `ollama/README.md`)

## Prod local (hardening) — `up` / `down` / `build`

**Prod local** starts the stack with `compose.prod.yml` (reverse proxy + hardened ports for internal services).

- Start: `./scripts/up.sh prod [--all] [--obs] [--gpu|--ollama] [--logs] [--infra]`
- Build images: `./scripts/build.sh prod` with the **same** flags as `up prod`
- Stop: `./scripts/down.sh` with the **same** flags you used for `up` (e.g. `--all` = obs + GPU + logs + infra + `-v`)

Notes:
- **`--obs`** adds `compose.obs.yml` (opt-in). Plain `up.sh prod` is base + `compose.prod.yml` only (reverse proxy, hardened ports).
- `--gpu` → `compose.ollama-gpu.yml`; `--ollama` → `compose.ollama.yml` (CPU). Not both.
- `--logs` → Loki + Promtail; `--infra` → node-exporter + cAdvisor (host paths; see `docker/README.md`).

## Database backup / restore

These scripts target Postgres in a container (default container name `postgres`).

### Backup

Produces a `.sql` file with `pg_dump`:

```bash
./scripts/backup-db.sh
```

Optional variables:

- `DB_CONTAINER_NAME` (default `postgres`)
- `OUTPUT_DIR` (default `backups`)
- `POSTGRES_USER` (default `postgres`)
- `POSTGRES_DB` (default `vectordb`)

The file is saved under `OUTPUT_DIR` with a name like `db-backup-<YYYYMMDD-HHMMSS>.sql`.

### Restore

Restores a `.sql` backup with `psql` (destructive: replaces current schema/data per the dump):

```bash
./scripts/restore-db.sh path/to/db-backup-YYYYMMDD-HHMMSS.sql
```

Optional variables:

- `DB_CONTAINER_NAME` (default `postgres`)
- `POSTGRES_USER` (default `postgres`)
- `POSTGRES_DB` (default `vectordb`)

## Smoke test

After `docker compose up -d`, verify that services respond and that an RAG query goes through the classifier.

For a **repeatable pytest suite** (health + classify + backend + optional observability), see [`tests/integration/README.md`](../tests/integration/README.md).

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

- **Backend (classification happens internally):**  
  `curl -s "http://localhost:9000/api/v4/query?question=How%20many%20documents%20are%20there?"`  
  → should return 200 and a response body. If the classifier is unavailable, the backend may still respond using an LLM fallback.

### Optional script (from repo root, stack running)

```bash
# classifier-service health
curl -sf http://localhost:8000/health && echo " classifier-service OK"

# Classify
curl -sf -X POST http://localhost:8000/classify -H "Content-Type: application/json" -d '{"query":"How many documents?"}' && echo " Classify OK"

# Backend query (needs DB data and Ollama for a full answer)
curl -sf -o /dev/null -w "%{http_code}" "http://localhost:9000/api/v4/query?question=test" && echo " Backend query OK"
```

If the DB is empty or Ollama is down, the backend may still return 200 with an error message in the body; the minimum smoke check is HTTP 200.
