# RAG Spring AI Ollama

<!-- ═══════════════════════════════════════════════════════════════════════════
     BADGES
     ═══════════════════════════════════════════════════════════════════════════ -->

<!-- CI & Build -->
[![CI](https://github.com/sergio-tr/rag-spring-ai-ollama/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/sergio-tr/rag-spring-ai-ollama/actions/workflows/ci.yml)
[![Build (no tests)](https://github.com/sergio-tr/rag-spring-ai-ollama/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/sergio-tr/rag-spring-ai-ollama/actions/workflows/build.yml)
[![SonarCloud](https://github.com/sergio-tr/rag-spring-ai-ollama/actions/workflows/sonar.yml/badge.svg?branch=main)](https://github.com/sergio-tr/rag-spring-ai-ollama/actions/workflows/sonar.yml)

<!-- SonarCloud quality metrics -->
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=YOUR_SONAR_PROJECT_KEY&metric=alert_status)](https://sonarcloud.io/dashboard?id=YOUR_SONAR_PROJECT_KEY)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=YOUR_SONAR_PROJECT_KEY&metric=coverage)](https://sonarcloud.io/dashboard?id=YOUR_SONAR_PROJECT_KEY)
[![Bugs](https://sonarcloud.io/api/project_badges/measure?project=YOUR_SONAR_PROJECT_KEY&metric=bugs)](https://sonarcloud.io/dashboard?id=YOUR_SONAR_PROJECT_KEY)
[![Vulnerabilities](https://sonarcloud.io/api/project_badges/measure?project=YOUR_SONAR_PROJECT_KEY&metric=vulnerabilities)](https://sonarcloud.io/dashboard?id=YOUR_SONAR_PROJECT_KEY)
[![Security Hotspots](https://sonarcloud.io/api/project_badges/measure?project=YOUR_SONAR_PROJECT_KEY&metric=security_hotspots)](https://sonarcloud.io/dashboard?id=YOUR_SONAR_PROJECT_KEY)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=YOUR_SONAR_PROJECT_KEY&metric=sqale_rating)](https://sonarcloud.io/dashboard?id=YOUR_SONAR_PROJECT_KEY)

<!-- Last release -->
[![GitHub Release](https://img.shields.io/github/v/release/sergio-tr/rag-spring-ai-ollama?label=latest%20release&color=blue)](https://github.com/sergio-tr/rag-spring-ai-ollama/releases/latest)
[![Build & Push Images](https://github.com/sergio-tr/rag-spring-ai-ollama/actions/workflows/build-images.yml/badge.svg)](https://github.com/sergio-tr/rag-spring-ai-ollama/actions/workflows/build-images.yml)

---

RAG (Retrieval-Augmented Generation) system built with **Spring Boot**, **Spring AI**, **Ollama**, and **PostgreSQL + pgvector**. Includes a trainable query-type classifier exposed as an HTTP microservice (FastAPI + TensorFlow).

## Quick start (development)

```bash
# 1. Create env files for each component
./scripts/create-env-all.sh

# 2. Start infrastructure (Postgres) in Docker
./scripts/up.sh dev

# 3. Backend with hot-reload (terminal 2)
cd rag-service && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 4. Classifier with hot-reload (terminal 3)
cd classifier-service && uvicorn main:app --reload --reload-dir app

# API available at http://localhost:9000/api/v4
```

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
|---|---|---|
| Dev (hybrid) | `./scripts/up.sh dev` | Only infra in Docker; services run locally |
| Dev (max infra) | `./scripts/up.sh dev --all` | `--gpu --obs --classifier --logs --infra` (GPU Ollama, obs, Loki/Promtail, node-exporter/cAdvisor) |
| Full compose | `cd docker && docker compose ... up -d` | Everything in Docker |
| With observability | add `-f compose.obs.yml --env-file ../observability/.env` | + OTEL/Jaeger/Prometheus/Grafana |
| With GPU (Ollama) | add `-f compose.ollama-gpu.yml --env-file ../ollama/.env` | + Ollama with NVIDIA GPU |
| Prod local | `./scripts/up.sh prod [--obs]` | Hardened: nginx reverse proxy; add `--obs` for OTEL/Jaeger/Prometheus/Grafana |

## Key API endpoints

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v4/query?question=...` | RAG query (JSON: `success` + `data` or `error`; HTTP **503** + `LLM_UNAVAILABLE` if Ollama unreachable) |
| `POST` | `/api/v4/documents` | Upload document (multipart) |
| `POST` | `/api/v4/documents/minute` | Add meeting minute (JSON) |
| `DELETE` | `/api/v4/documents/{id}` | Delete document |
| `GET` | `/api/v4/evaluate` | Run RAG evaluation |
| `POST` | `/api/v4/evaluate/custom` | Evaluate with custom config |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/actuator/prometheus` | Prometheus metrics |

Classifier endpoints: `POST /classify`, `GET /models`, `POST /train`, `POST /evaluate`

## SonarCloud (quality gate and static analysis)

Analysis is driven by [`sonar-project.properties`](sonar-project.properties) and [`.github/workflows/sonar.yml`](.github/workflows/sonar.yml). Set `sonar.projectKey` and `sonar.organization` to match your SonarCloud project, and add a **`SONAR_TOKEN`** repository secret (SonarCloud → *My Account → Security*).

**Branches:** pushes and PRs to `main` / `dev` trigger analysis. In SonarCloud, set the main branch to `main` (*Project → Administration → Branches and Pull Requests*) so **New Code** is computed correctly.

### Recommended Quality Gate: `Project Gate`

Create an organization-level gate (*Organization → Quality Gates → Create*) and assign it to this project (*Project → Administration → Quality Gate*).

**Conditions on New Code**

| Metric | Operator | Threshold |
|---|---|---|
| Coverage on New Code | `<` | `80%` |
| Duplicated Lines on New Code | `>` | `3%` |
| Maintainability / Reliability / Security Rating | worse than | `A` |
| Security Hotspots Reviewed | `<` | `100%` |

**Conditions on Overall Code**

| Metric | Operator | Threshold |
|---|---|---|
| Coverage | `<` | `70%` |
| Duplicated Lines | `>` | `5%` |
| Maintainability / Reliability Rating | worse than | `B` |
| Security Rating | worse than | `A` |

### Quality profiles (optional)

Copy **Sonar way** for Java and Python and enable extra rules as needed (e.g. hardcoded credentials, permissive CORS, weak crypto). Assign the profile to the project.

### Security Hotspots

Hotspots require manual review; the gate may require **100% reviewed**. Typical justified cases in this repo: monitoring credentials in SQL init scripts, test credentials in `test-init.sql`, and config under `observability/**` when using environment variables. Use *Fixed*, *Won’t Fix*, or *Acknowledged* with a short comment in SonarCloud.

Production credentials must always come from environment / `.env` files, not from literals in application code.

### Badges

README badges use SonarCloud’s public badge API. Replace `YOUR_SONAR_PROJECT_KEY` in the URLs above with your real **project key** so metrics resolve.

## Documentation

| Document | Description |
|---|---|
| [rag-service/README.md](rag-service/README.md) | Backend build, variables, Compose, smoke test link |
| [classifier-service/README.md](classifier-service/README.md) | Classifier API, run locally, regression testing |
| [docker/README.md](docker/README.md) | Compose usage, execution modes, deployment runbook |
| [scripts/README.md](scripts/README.md) | Env scripts, prod-local, backup/restore, smoke test |
| [db/README.md](db/README.md) | Database setup |
| [ollama/README.md](ollama/README.md) | Ollama / GPU stack |
| [observability/README.md](observability/README.md) | Observability stack (OTEL, Jaeger, Prometheus, Grafana) |
| [docs/DEV_STACK_OBS_Y_OLLAMA_HOST.md](docs/DEV_STACK_OBS_Y_OLLAMA_HOST.md) | Dev + observabilidad + Ollama en el host (sin contenedor Ollama) |
| [tests/integration/README.md](tests/integration/README.md) | Integration tests (pytest): classifier, backend, cross-service, and observability (OTEL/Jaeger/Prometheus when `compose.obs.yml` is up) |

**SonarCloud:** quality gate, CI setup, and hotspot policy are documented in the [SonarCloud](#sonarcloud-quality-gate-and-static-analysis) section above (extended notes may exist only in a local `docs/` copy).

The root `docs/` folder is listed in `.gitignore`: local-only drafts, analysis, planning, and long guides (e.g. `DEVELOPMENT_ENVIRONMENT.md`, evaluation notes) are not versioned.

## Tech stack

**Backend**: Spring Boot · Spring AI · Java · Maven · Flyway · JaCoCo  
**Classifier**: FastAPI · TensorFlow/Keras · Python 3.11 · pytest-cov  
**Database**: PostgreSQL + pgvector  
**LLM runtime**: Ollama (local, GPU-optional)  
**Observability**: OpenTelemetry · Jaeger · Prometheus · Grafana · Loki  
**Infrastructure**: Docker · Docker Compose · Nginx  
**CI/CD**: GitHub Actions · SonarCloud · GHCR  
