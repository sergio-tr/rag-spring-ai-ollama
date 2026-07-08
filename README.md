# RAG Spring AI Ollama
<!-- ═══════════════════════════════════════════════════════════════════════════
     BADGES - selected project status
     ═══════════════════════════════════════════════════════════════════════════ -->

[![Observability smoke](https://github.com/sergio-tr/rag-spring-ai-ollama/actions/workflows/observability-smoke.yml/badge.svg)](https://github.com/sergio-tr/rag-spring-ai-ollama/actions/workflows/observability-smoke.yml)
[![Build (no tests)](https://github.com/sergio-tr/rag-spring-ai-ollama/actions/workflows/build.yml/badge.svg)](https://github.com/sergio-tr/rag-spring-ai-ollama/actions/workflows/build.yml)

<!-- SonarCloud quality metrics -->
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=sergio-tr_rag-spring-ai-ollama&metric=coverage)](https://sonarcloud.io/summary/new_code?id=sergio-tr_rag-spring-ai-ollama)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=sergio-tr_rag-spring-ai-ollama&metric=bugs)](https://sonarcloud.io/summary/new_code?id=sergio-tr_rag-spring-ai-ollama)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=sergio-tr_rag-spring-ai-ollama&metric=vulnerabilities)](https://sonarcloud.io/summary/new_code?id=sergio-tr_rag-spring-ai-ollama)
[![Security Rating](https://sonarcloud.io/api/project_badges/measure?project=sergio-tr_rag-spring-ai-ollama&metric=security_rating)](https://sonarcloud.io/summary/new_code?id=sergio-tr_rag-spring-ai-ollama)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=sergio-tr_rag-spring-ai-ollama&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=sergio-tr_rag-spring-ai-ollama)

<!-- Last release -->
[![GitHub Release](https://img.shields.io/github/v/release/sergio-tr/rag-spring-ai-ollama?label=latest%20release&color=blue)](https://github.com/sergio-tr/rag-spring-ai-ollama/releases/latest)

---

## Project links

- **Test reports and documentation:** [GitHub Pages](https://sergio-tr.github.io/rag-spring-ai-ollama/)
- **Deployed web application:** [Production web app](https://hatchback-obsession-staring.ngrok-free.dev/en/login)

RAG (Retrieval-Augmented Generation) system built with **Spring Boot**, **Spring AI**, **LiteLLM** (configured API catalog), and **PostgreSQL + pgvector**. Includes a trainable query-type classifier exposed as an HTTP microservice (FastAPI; default **scikit-learn** model, optional Keras training path).

**Documentation:** global architecture, domain, and governance live in **[`docs/README.md`](docs/README.md)** (policy layers and non-canonical areas: [`docs/development/documentation-governance-strategy.md`](docs/development/documentation-governance-strategy.md)). Per-module setup and commands live in each folder’s **README** (see table below).

**Quality baseline:** canonical commands + CI parity - [`docs/testing/baseline-runbook.md`](docs/testing/baseline-runbook.md); hub (exclusions, policies, Sonar links) - [`docs/quality/README.md`](docs/quality/README.md).

**CI pull requests, job gates, Docker/Compose pins:** [`docs/devops/README.md`](docs/devops/README.md).

**Where it runs:** Repository automation (`docker/scripts/*.sh`, `tests/**/*.sh`, Compose, Gatling via `./gradlew`) and **CI/CD** are designed for **Linux** (local shell or `ubuntu-*` runners). **Docker images** for backend, classifier, databases, and observability are **Linux-based**. For day-to-day parity with CI, develop on Linux or **WSL2**, not raw Windows shells.

## Quick start (development)

```bash
# 1. Create env files for each component
./docker/scripts/create-env-all.sh

# 2. Start infrastructure (Postgres) in Docker
./docker/scripts/up.sh dev

# 3. Backend with hot-reload (terminal 2)
cd rag-service && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 4. Classifier with hot-reload (terminal 3)
cd classifier-service && uvicorn uvicorn_entry:app --reload --reload-dir app

# Default HTTP API base (see rag-service application properties).
# Override with RAG_API_PRODUCT_BASE_PATH (Spring) and NEXT_PUBLIC_RAG_API_PREFIX (Next.js).
```

## Test pyramid & CI/CD

| Layer | Command / workflow | Gate |
| --- | --- | --- |
| Backend unit + integration | `cd rag-service && ./mvnw verify` | JaCoCo bundle **≥ 80%** (`jacoco-check`) |
| Classifier | `cd classifier-service && pytest tests/` | **≥ 80%** via `.coveragerc` `fail_under` + `pytest.ini` |
| Webapp unit | `cd webapp && npm test` | Vitest (expand coverage over time) |
| E2E smoke (Playwright) | `cd webapp && npm run build && npm run test:e2e` (excludes `@fullstack`) | **[`.github/workflows/ci.yml`](.github/workflows/ci.yml)** job `core_webapp_e2e`; optional re-run via [`.github/workflows/e2e.yml`](.github/workflows/e2e.yml) (`workflow_dispatch`) |
| E2E + API (E2E-01–10 smoke) | `make webapp-e2e-fullstack` (Spring `e2e`: stubs AI + Postgres; SSE `done.sources`) | [`.github/workflows/e2e-fullstack.yml`](.github/workflows/e2e-fullstack.yml) when `webapp/**` or `rag-service/**` change |
| System / API smoke (canonical) | `cd webapp && npm run test:api` (`API_BASE_URL` = Spring); `make system-checks` | [`.github/workflows/system-checks.yml`](.github/workflows/system-checks.yml) **manual** |
| Load / stress (Gatling) | `cd tests/gatling && ./gradlew gatlingRun --simulation …`; details: [tests/gatling/README.md](tests/gatling/README.md), overview: [docs/performance/README.md](docs/performance/README.md) | [`.github/workflows/gatling.yml`](.github/workflows/gatling.yml) dispatch / schedule; set `GATLING_BASE_URL` |
## Full stack with Docker Compose

```bash
# Build and start all services
cd docker
docker compose \
  --env-file ../db/.env \
  --env-file ../rag-service/.env \
  --env-file ../classifier-service/.env \
  up -d
```

## Execution modes

| Mode | Command | Description |
| --- | --- | --- |
| Dev (hybrid) | `./docker/scripts/up.sh dev` | Only infra in Docker; services run locally |
| Dev (max infra) | `./docker/scripts/up.sh dev --all` | `--gpu --obs --classifier --logs --infra` (GPU Ollama, obs, Loki/Promtail, node-exporter; cAdvisor is `--profile cadvisor`) |
| Full compose | `cd docker && docker compose ... up -d` | Everything in Docker |
| With observability | add `-f compose.obs.yml --env-file ../observability/.env` | + OTEL/Jaeger/Prometheus/Grafana |
| With GPU | `-f compose.gpu.yml` + `--profile ollama` in `docker-compose.yml` | Classifier on GPU; optional local Ollama on GPU via `docker/scripts` `--gpu` |
| Prod local | `./docker/scripts/up.sh prod [--obs]` | Hardened: nginx reverse proxy; add `--obs` for OTEL/Jaeger/Prometheus/Grafana |

## Key API endpoints

Prefix is **configurable**. Spring: `rag.api.product-base-path` (`RAG_API_PRODUCT_BASE_PATH`). Webapp: `NEXT_PUBLIC_RAG_API_PREFIX` must match the product base path (see `webapp/.env.example`). Below, `{product}` stands for that configured prefix.

| Method | Endpoint | Description |
| --- | --- | --- |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/actuator/prometheus` | Prometheus metrics |
| `GET` | `/v3/api-docs` | OpenAPI 3 JSON (springdoc) |
| `GET` | `/swagger-ui.html` | Swagger UI (interactive API docs) |
| `GET` \ | `PUT` | `{product}/config/user` | Effective user RAG config (JSON); authenticated |
| `GET` \ | `PUT` \ | `DELETE` | `{product}/config/project/{projectId}` | Project overrides; authenticated, project ownership |
| `GET` | `{product}/lab/status` | Lab capability stub (authenticated) |
| `GET` | `{product}/admin/health` | Admin health (`403` unless JWT role `ADMIN`) |

**Ollama URL:** set `SPRING_AI_OLLAMA_BASE_URL` (alias `OLLAMA_BASE_URL`) to the Ollama HTTP API - for example `http://127.0.0.1:11434` on the host. See [docs/operations/environments.md](docs/operations/environments.md) and [rag-service/README.md](rag-service/README.md) for more details.

**Generated docs:** Javadoc: `cd rag-service && ./mvnw javadoc:javadoc` → `rag-service/target/reports/apidocs`. OpenAPI: `/v3/api-docs` when springdoc is enabled; export with [`rag-service/scripts/export-openapi.sh`](rag-service/scripts/export-openapi.sh). CI may write `openapi.json` during `verify` when a Postgres datasource is available. TypeDoc: `cd webapp && npm run doc` → `webapp/docs/api`. See [docs/README.md](docs/README.md) (auto-generated API docs).

**Types:** OpenAPI is served at `/v3/api-docs`. TypeScript types in `webapp/src/types/api.ts` may be maintained manually until an `openapi-generator` job is added in CI; keep field names aligned with Spring DTOs and JSON keys from `RagConfig.toValueMap()`.

Classifier endpoints: `POST /classify`, `GET /models`, `POST /train`, `POST /evaluate`

## SonarCloud (quality gate and static analysis)

Analysis is driven by [`sonar-project.properties`](sonar-project.properties) and [`.github/workflows/sonar.yml`](.github/workflows/sonar.yml). Set `sonar.projectKey` and `sonar.organization` to match your SonarCloud project, and add a **`SONAR_TOKEN`** repository secret (SonarCloud → *My Account → Security*).

**Local scan (same steps as CI):** [`docs/development/sonar-local-analysis.md`](docs/development/sonar-local-analysis.md) - script [`.github/local/sonar-local.sh`](.github/local/sonar-local.sh). Requires Postgres + Docker for the scanner image; set `SONAR_TOKEN` in the environment.

**Branches:** pushes and PRs to `main` / `dev` trigger analysis. In SonarCloud, set the main branch to `main` (*Project → Administration → Branches and Pull Requests*) so **New Code** is computed correctly.

### Recommended Quality Gate: `Project Gate`

Create an organization-level gate (*Organization → Quality Gates → Create*) and assign it to this project (*Project → Administration → Quality Gate*).

**Conditions on New Code**

| Metric | Operator | Threshold |
| --- | --- | --- |
| Coverage on New Code | `<` | `80%` |
| Duplicated Lines on New Code | `>` | `3%` |
| Maintainability / Reliability / Security Rating | worse than | `A` |
| Security Hotspots Reviewed | `<` | `100%` |

**Conditions on Overall Code**

| Metric | Operator | Threshold |
| --- | --- | --- |
| Coverage | `<` | `80%` |
| Duplicated Lines | `>` | `5%` |
| Maintainability / Reliability Rating | worse than | `B` |
| Security Rating | worse than | `A` |

### Quality profiles (optional)

Copy **Sonar way** for Java and Python and enable extra rules as needed (e.g. hardcoded credentials, permissive CORS, weak crypto). Assign the profile to the project.

### Security Hotspots

Hotspots require manual review; the gate may require **100% reviewed**. Typical justified cases in this repo: monitoring credentials in SQL init scripts, test credentials in `test-init.sql`, and config under `observability/**` when using environment variables. Use *Fixed*, *Won’t Fix*, or *Acknowledged* with a short comment in SonarCloud.

Production credentials must always come from environment / `.env` files, not from literals in application code.

### Badges

- **GitHub Actions:** The README header lists a **subset** of workflows (native `…/workflows/<file>/badge.svg`). Badges track the latest run GitHub associates on the **default branch**; `workflow_dispatch`-only or uncommon triggers may show an empty or stale status until a recent successful run exists.
- **SonarCloud:** URLs use `sergio-tr_rag-spring-ai-ollama` (same as `sonar.projectKey` in [`sonar-project.properties`](sonar-project.properties)). If you create a **new** SonarCloud project, update both files consistently.
- **Release:** The GitHub Release badge uses `sergio-tr/rag-spring-ai-ollama`. If the repo lives under another `owner/name`, replace that segment everywhere in this file (Actions URLs use `owner/name` in the path; Sonar uses `owner_project`).

## Documentation

| Document | Description |
| --- | --- |
| [docs/README.md](docs/README.md) | **Hub:** architecture, domain, operations (conceptual), testing/performance overview, ADR index |
| [docs/development/documentation-guidelines.md](docs/development/documentation-guidelines.md) | Where to document what; `docs/` vs module READMEs |
| [rag-service/README.md](rag-service/README.md) | Backend build, variables, Compose, smoke test link |
| [classifier-service/README.md](classifier-service/README.md) | Classifier API, run locally, regression testing |
| [docker/README.md](docker/README.md) | Compose usage, execution modes, deployment runbook |
| [docker/scripts/README.md](docker/scripts/README.md) | Canonical operator scripts: Compose orchestration + `.env` generation |
| [db/README.md](db/README.md) | Database setup |
| [ollama/README.md](ollama/README.md) | Ollama / GPU stack |
| [observability/README.md](observability/README.md) | Observability stack (OTEL, Jaeger, Prometheus, Grafana) |
| [tests/README.md](tests/README.md) | Test automation index: Gatling, stack integration (pytest), technical e2e compose, Python micro-benchmarks |
| [tests/integration/README.md](tests/integration/README.md) | Integration tests (pytest): classifier, backend, cross-service, and observability (OTEL/Jaeger/Prometheus when `compose.obs.yml` is up) |

**SonarCloud:** quality gate, CI setup, and hotspot policy are documented in the [SonarCloud](#sonarcloud-quality-gate-and-static-analysis) section above (extended notes may exist only in a local `docs/` copy).

## Production deployment

The system is deployed inside the **University of Oviedo** network. External access may require **VPN** or on-campus connectivity.

| Role | Host |
| --- | --- |
| Application server (Docker Compose + self-hosted runner) | Private university network (runner on application VM) |
| Public URL (ngrok reserved domain) | `https://hatchback-obsession-staring.ngrok-free.dev` |
| Model-serving server (LiteLLM → Ollama) | `156.35.160.78` |

**Architecture:** A **self-hosted GitHub Actions runner** on the application server checks out the repository locally and runs Docker Compose. GitHub does **not** open inbound SSH to the university machine; the runner maintains **outbound HTTPS** to GitHub.

**Model serving:** The backend consumes **LiteLLM** only through an **OpenAI-compatible API** on the model-serving server. **Ollama** runs behind LiteLLM and is **not** called directly by the production backend (`OPENAI_COMPATIBLE` provider).

**Email:** Production uses real **SMTP** delivery via `support.rag@gmail.es`. **Mailpit** is development-only.

**Authentication:** **Google OAuth** in production (backend-owned flow).

**Legal:** Privacy Policy and Terms of Use are available at `{FRONTEND_PUBLIC_URL}/en/privacy-policy` and `{FRONTEND_PUBLIC_URL}/en/terms` (replace with the deployed URL).

**Observability:** Enabled in production deploy (`--obs --obs-private`); Grafana/Jaeger/Prometheus are internal to the Docker network.

### URLs and workflows

| Item | Value |
| --- | --- |
| Production application URL | `https://hatchback-obsession-staring.ngrok-free.dev` |
| Backend API URL | Same-origin via reverse proxy (`/api/v5`) |
| Privacy Policy | `{FRONTEND_PUBLIC_URL}/en/privacy-policy` |
| Terms of Use | `{FRONTEND_PUBLIC_URL}/en/terms` |
| GitHub Pages documentation | [https://sergio-tr.github.io/rag-spring-ai-ollama/](https://sergio-tr.github.io/rag-spring-ai-ollama/) |
| Deploy workflow | [`.github/workflows/deploy.yml`](.github/workflows/deploy.yml) (`push` to `main` or manual) |
| Documentation workflow | [`.github/workflows/docs-pages.yml`](.github/workflows/docs-pages.yml) |
| Runner check workflow | [`.github/workflows/self-hosted-runner-check.yml`](.github/workflows/self-hosted-runner-check.yml) |

**Repository settings:** Configure GitHub **Variables** `DEPLOY_DIR` and `DEPLOY_HEALTH_URL`. For documentation site: **Settings → Pages → Build and deployment → Source → GitHub Actions** (one-time; required before [`docs-pages.yml`](.github/workflows/docs-pages.yml) can deploy). Prefer **branch protection** on `main` requiring CI before merge (deploy relies on validated `main`).

**Server command (operator):**

```bash
./docker/scripts/up.sh prod --server --obs --obs-private --no-env-prompt
```

**Environment template:** [`.env.example`](.env.example) (index) and per-component `.env.example` files. Copy to `.env` on the server; never commit secrets.

**Detail:** [docs/operations/runbook-docker-vm.md](docs/operations/runbook-docker-vm.md), [docs/operations/deploy-workflow-audit.md](docs/operations/deploy-workflow-audit.md), [docker/README.md](docker/README.md).

## Tech stack

**Backend**: Spring Boot · Spring AI · Java · Maven · Flyway · JaCoCo  
**Classifier**: FastAPI · scikit-learn (default) / TensorFlow-Keras (training path) · Python 3.11 · pytest-cov  
**Database**: PostgreSQL + pgvector  
**LLM runtime**: LiteLLM (production, OpenAI-compatible) · Ollama (development / behind LiteLLM on model server)  
**Observability**: OpenTelemetry · Jaeger · Prometheus · Grafana · Loki  
**Infrastructure**: Docker · Docker Compose · Nginx  
**CI/CD**: GitHub Actions · SonarCloud · GHCR  
