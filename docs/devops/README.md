# DevOps and CI policy

Normative reference for **pull request validation**, **workflow alignment**, **Dockerfile / Compose parameterization**, and **local vs CI parity**. Operational runbooks remain under [`docs/operations/`](../operations/README.md); Docker how-to stays in [`docker/README.md`](../../docker/README.md).

## Pull requests and branch gates

**Entry workflow:** [`.github/workflows/ci.yml`](../../.github/workflows/ci.yml) invokes [`.github/workflows/reusable-ci-core.yml`](../../.github/workflows/reusable-ci-core.yml) (`workflow_call`). It runs on **`pull_request`** to `dev`, `main`, and `master`, and on **`push`** to **`main` and `master` only** (post-merge and deploy SHA alignment; not on `push` to `dev` — see [`.github/ci/dev-pr-gate.md`](../../.github/ci/dev-pr-gate.md)).

### Branch protection checklist (required checks vs GitHub UI labels)

GitHub Rulesets / branch protection **required status checks** must match the **job name** shown in the Actions UI (the `name:` field), not only the YAML job id.

| YAML job id | Name in GitHub Actions UI | Typical required for `dev` PR |
| --- | --- | --- |
| `core_backend` | Backend (Java) | Yes |
| `core_classifier` | Query Classifier Service (Python) | Yes |
| `core_webapp` | Webapp (Next.js) | Yes |
| `core_webapp_e2e` | Webapp E2E (Playwright smoke) | Yes |
| `core_done` | Core stages complete | No (aggregator) |
| `integration` | Stack integration (pytest) | Yes |
| `docker_build_smoke` | Docker build smoke (no push) | No (release-quality lane; run on PRs to `main`/`master` and push `main`/`master`) |
| `compose_structural_guard` | Compose structural guard (no Docker) | Yes |
| `e2e_fullstack` | E2E fullstack (Playwright @fullstack) | Yes |
| `sonar` | SonarCloud Scan | No (release-quality lane; run on PRs to `main`/`master` and push `main`/`master`) |
| `performance` | Performance (Gatling + infra probe) | No for `dev` (runs only when PR base is `main` or `master`) |

**Separate workflow** [`.github/workflows/docker-compose-ci.yml`](../../.github/workflows/docker-compose-ci.yml): job **Env, compose guard, docker compose config** — see [Compose validation (two layers)](#compose-validation-two-layers) below.

### Job DAG (names as in GitHub Actions)

`core_backend` → parallel with `core_classifier` and the `core_webapp` → `core_webapp_e2e` chain → `core_done` → `integration` and `compose_structural_guard` (parallel) → `e2e_fullstack` → release-quality lanes (`docker_build_smoke` + `sonar`) on `main`/`master` only → `performance` (conditional).

| Job id | Role | Typical merge policy |
| --- | --- | --- |
| `core_backend` | `rag-service`: `./mvnw clean verify`, Javadoc, pinned Postgres check script | **Blocking** |
| `core_classifier` | `classifier-service`: `pytest tests/` (see `pytest.ini` / `.coveragerc`) | **Blocking** |
| `core_webapp` | `webapp`: lint, typecheck, `test:coverage`, build, `npm run doc` | **Blocking** |
| `core_webapp_e2e` | Playwright smoke (excludes `@fullstack`) | **Blocking** |
| `core_done` | Aggregates core stages | N/A (no-op step) |
| `integration` | Spring `e2e` + Postgres + `pytest tests/integration` (stack HTTP) | **Blocking** (recommended) |
| `docker_build_smoke` | `docker build` for app images + `db` + `reverse-proxy` (no push); see inventory below | **Blocking** for PRs to `main`/`master` (not default for `dev`) |
| `compose_structural_guard` | `create-env-all.sh` + structural `compose_guard.py` (no Docker daemon) | **Blocking** |
| `e2e_fullstack` | Spring + Postgres + Playwright `@fullstack` | **Blocking** (recommended) |
| `sonar` | Coverage + SonarCloud scan | **Blocking** for PRs to `main`/`master` when `SONAR_TOKEN` is available (see forks below) |
| `performance` | Gatling + Python infra probe | **Not** run for PRs to `dev` (only when `pull_request` base is `main` or `master`) |

Team policy: mark the jobs you require in GitHub **Branch protection** / **Rulesets** to match the tables above. Do not treat `performance` as a `dev` PR gate unless you change the workflow conditions.

### Classifier: core vs Sonar

Both **`core_classifier`** and the **`sonar`** job run the **same** command for Python tests: `pytest tests/ -v` from `classifier-service/` (project `addopts` in `pytest.ini` apply, including coverage XML for Sonar). The Sonar step then runs `scripts/patch_coverage_xml_for_sonar.py` for path normalization only.

### Forks and `SONAR_TOKEN`

Fork PRs **do not** receive repository secrets. The `sonar` job fails fast if `SONAR_TOKEN` is missing. **Normative options:** (1) do not require the `sonar` check for fork PRs; or (2) use an organization policy / bot that runs analysis on the base repo. Pick one and keep branch protection consistent.

### Next.js public env in CI

The reusable workflow defines workflow-level `env`:

- `NEXT_PUBLIC_RAG_API_PREFIX` (canonical product API prefix for builds)
- `NEXT_PUBLIC_API_BASE_URL` (default `http://localhost:9000`)

Jobs that need a different host (e.g. `127.0.0.1` for the fullstack browser job) **override** `NEXT_PUBLIC_API_BASE_URL` at job level. **Do not** scatter duplicate literals for `NEXT_PUBLIC_RAG_API_PREFIX` across steps.

Compose defaults for the same prefix live under **Compose parameterization** below (`docker-compose.yml`).

## Dockerfile and image build inventory

| Service | Dockerfile path | CI smoke (`docker_build_smoke`) | GHCR push workflow | Compose `build` |
| --- | --- | --- | --- | --- |
| Postgres | [`db/Dockerfile`](../../db/Dockerfile) (thin `FROM` pin via `POSTGRES_BASE_IMAGE`) | Yes (`POSTGRES_BASE_IMAGE` = workflow `POSTGRES_SERVICE_IMAGE`) | `context: db` in [`build-images.yml`](../../.github/workflows/build-images.yml) | `postgres` |
| `rag-service` | [`rag-service/Dockerfile`](../../rag-service/Dockerfile) | Yes | [`build-images.yml`](../../.github/workflows/build-images.yml) `context: rag-service` | `backend` service |
| `classifier-service` | [`classifier-service/Dockerfile`](../../classifier-service/Dockerfile) | Yes | `context: classifier-service` | `classifier-service` |
| `webapp` | [`webapp/Dockerfile`](../../webapp/Dockerfile) | Yes (with `NEXT_PUBLIC_*` build-args) | `context: webapp` | `webapp` |
| `reverse-proxy` | [`reverse-proxy/Dockerfile`](../../reverse-proxy/Dockerfile) | Yes | Yes | overlays as documented in `docker/README.md` |
| `ollama` | [`ollama/Dockerfile`](../../ollama/Dockerfile) | No | Yes | optional overlays |
| Observability | [`observability/*/Dockerfile`](../../observability/) | No | Yes | `docker-compose.yml` profiles + `compose.obs.yml` |

**Source of truth for GHCR tags:** [`build-images.yml`](../../.github/workflows/build-images.yml) — SHA tag + `:latest` (non-contractual).

## Compose parameterization (source of truth)

| Concern | Canonical location | Notes |
| --- | --- | --- |
| Postgres base image | Fixed build-arg in [`docker/docker-compose.yml`](../../docker/docker-compose.yml) (`pgvector/pgvector:0.8.2-pg16-bookworm`); `POSTGRES_BASE_IMAGE` in `db/.env` is for docs / manual `docker build` | Canonical string in [`.github/ci/postgres-service-image.env`](../../.github/ci/postgres-service-image.env). CI job services use `env.POSTGRES_SERVICE_IMAGE` in [`reusable-ci-core.yml`](../../.github/workflows/reusable-ci-core.yml). Verified by [`.github/scripts/verify-pinned-postgres-image.sh`](../../.github/scripts/verify-pinned-postgres-image.sh). |
| Java base images (backend image) | `RAG_JAVA_JDK_BASE_IMAGE`, `RAG_JAVA_JRE_BASE_IMAGE` | `docker-compose.yml` `args` → `rag-service/.env` / defaults in Dockerfile `ARG`. |
| Python base (classifier) | `CLASSIFIER_PYTHON_BASE_IMAGE` | Compose `args` → `classifier-service/.env`. |
| Webapp `NEXT_PUBLIC_*` at build | `WEBAPP_NEXT_PUBLIC_*` or `NEXT_PUBLIC_*` via compose env | See `webapp` service `build.args` in [`docker-compose.yml`](../../docker/docker-compose.yml); defaults include `/api/v5` for the API prefix. |
| Entry point | `docker/` directory | Canonical invocations in [`docker/README.md`](../../docker/README.md) (`--env-file` chain). |

**Rule:** When changing a pinned image or public API prefix, update (1) Compose or env templates, (2) CI workflow if the same value is duplicated, and (3) the verify script if Postgres changes.

### Compose validation (two layers)

1. **Every PR** (main CI): job **`compose_structural_guard`** runs `create-env-all.sh --force` and structural [`compose_guard.py`](../../docker/scripts/compose_guard.py) (`image_forbidden`, `yaml_error`, `build_*`). No Docker daemon required.
2. **When Docker paths change:** [`.github/workflows/docker-compose-ci.yml`](../../.github/workflows/docker-compose-ci.yml) runs the same structural guard plus **`docker compose config -q`** on representative merged file sets (see workflow). Trigger: `paths` **`docker/**`** or edits to that workflow on **`pull_request`** (all configured bases). Application-only PRs rely on layer (1) unless you run Compose locally.

**Full `compose_guard` (including `environment_literal`, `healthcheck_*`):** not enforced in CI until remaining overlays are migrated; tracked backlog: `compose.obs.yml`, `compose.prod.yml` (`reverse-proxy`), `compose.rag-dev-obs.yml`, `docker-compose.yml` (classifier healthcheck). CI continues to use `--only-rules` structural subset in both workflows.

**Remote / host Ollama:** URLs come only from **`rag-service/.env`** (`OLLAMA_BASE_URL`, `SPRING_AI_OLLAMA_BASE_URL`). Flag **`--ollama-remote`** on **`docker/scripts/up.sh`** skips the local **`ollama`** container profile when used with **`--gpu`/`--ollama`**; it does not add extra compose merge files.

## Local vs CI parity

Run the same commands the workflows run:

| Layer | Command |
| --- | --- |
| Backend | `cd rag-service && ./mvnw clean verify` |
| Classifier | `cd classifier-service && pytest tests/` |
| Webapp | `cd webapp && npm run lint && npm run typecheck && npm run test:coverage && npm run build` |
| Docker smoke | `docker build` from each application `Dockerfile` (match CI build-args for `webapp`) |
| Compose policy + config | Structural rules: same as CI job **Compose structural guard**. Full merge validation: `docker compose … config -q` (same merges as [`.github/workflows/docker-compose-ci.yml`](../../.github/workflows/docker-compose-ci.yml)) |

Deeper detail: [`docs/testing/README.md`](../testing/README.md), [`docs/operations/local-ci-parity.md`](../operations/local-ci-parity.md) if present.

## Related documentation boundaries

When extending this hub, prefer edits under `docs/devops/**`, `docs/testing/**`, and cross-links from `docs/architecture/**` only when the system-level story changes. Do not use this document as justification to edit [`DATA_MODEL.md`](../architecture/DATA_MODEL.md) or [`configuration-resolution-model.md`](../architecture/configuration-resolution-model.md); those files follow their own change control.
