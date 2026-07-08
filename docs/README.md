# Documentation hub (`docs/`)

**Global, conceptual documentation** for this monorepo: what the system is, how pieces relate, and where to find **how-to** detail. Module-specific commands and config live in **README files next to code** (`rag-service/`, `webapp/`, `docker/`, etc.).

**Governance:** [development/documentation-guidelines.md](development/documentation-guidelines.md); strategy: [development/documentation-governance-strategy.md](development/documentation-governance-strategy.md); [documentation inventory](development/documentation-inventory.md) (classification).

## Start here

| Topic | Document |
| --- | --- |
| **Architecture freeze (0.1 - target model)** | [architecture/target-architecture.md](architecture/target-architecture.md) - [rag-runtime-architecture.md](architecture/rag-runtime-architecture.md), [configuration-resolution-model.md](architecture/configuration-resolution-model.md), [knowledge-system-model.md](architecture/knowledge-system-model.md), [implementation-roadmap.md](architecture/implementation-roadmap.md); ADRs [0005–0013](adr/README.md) |
| **Spring AI / RAG modernization** | [ai/README.md](ai/README.md) - inventory, pipeline contracts; [adr/0013](adr/0013-agentic-patterns-adoption-gate.md) |
| Product scope and boundaries | [overview/README.md](overview/README.md) - [product-context.md](overview/product-context.md), [minimum-scope.md](overview/minimum-scope.md) |
| System context and diagrams | [architecture/README.md](architecture/README.md), [architecture/system-context.md](architecture/system-context.md) |
| Deployment (conceptual) | [architecture/deployment-model.md](architecture/deployment-model.md), [operations/README.md](operations/README.md) |
| Observability (operator) | [operations/grafana-observability-guide.md](operations/grafana-observability-guide.md), [observability/README.md](../observability/README.md) |
| CI/CD release checklist | [operations/release-readiness-checklist.md](operations/release-readiness-checklist.md), [operations/deploy-workflow-audit.md](operations/deploy-workflow-audit.md) |
| Integration / auth / RAG flows | [architecture/integration-flows.md](architecture/integration-flows.md) |
| Domain concepts | [domain/README.md](domain/README.md) |
| Data model (ER) | [architecture/DATA_MODEL.md](architecture/DATA_MODEL.md) |
| Testing strategy (overview) | [testing/README.md](testing/README.md), [development/e2e-testing-strategy.md](development/e2e-testing-strategy.md) (workflows vs gates), [testing/traceability-retired-tools.md](testing/traceability-retired-tools.md) (load-tool mapping) |
| CI / PR validation and Compose pins | [devops/README.md](devops/README.md) |
| Coverage reports (paths, Sonar) | [coverage/README.md](coverage/README.md) |
| SonarCloud scan locally (match `sonar.yml`) | [development/sonar-local-analysis.md](development/sonar-local-analysis.md), [docker/scripts/README.md](../docker/scripts/README.md) |
| Classifier registry (train → activate) | [development/classifier-registry-demo.md](development/classifier-registry-demo.md) |
| Performance / load (overview) | [performance/README.md](performance/README.md) |
| ADRs | [adr/README.md](adr/README.md) |
| Backend refactoring governance (`rag-service`) | [backend/refactoring-governance.md](backend/refactoring-governance.md); ADR [0012](adr/0012-backend-refactoring-governance.md) |
| Contributor doc rules | [development/README.md](development/README.md), [development/documentation-guidelines.md](development/documentation-guidelines.md) (including [inventory](development/documentation-inventory.md)) |

## Platform assumptions (Linux-first)

- Shell automation under `docker/scripts/`, `tests/**/*.sh`, and **GitHub Actions** on `ubuntu-*` expect a **Linux** (or **WSL2**) environment.
- Container images are **Linux** userlands.

## Repository map (canonical README per area)

| Area | Path | README |
| --- | --- | --- |
| Backend | `rag-service/` | [rag-service/README.md](../rag-service/README.md) |
| Webapp | `webapp/` | [webapp/README.md](../webapp/README.md) |
| Classifier | `classifier-service/` | [classifier-service/README.md](../classifier-service/README.md) |
| Docker / Compose | `docker/` | [docker/README.md](../docker/README.md) |
| Compose scripts | `docker/scripts/` | [docker/scripts/README.md](../docker/scripts/README.md) |
| Database | `db/` | [db/README.md](../db/README.md) |
| Ollama (image / stack) | `ollama/` | [ollama/README.md](../ollama/README.md) |
| Observability | `observability/` | [observability/README.md](../observability/README.md) |
| Automation (Compose + env) | `docker/scripts/` | [docker/scripts/README.md](../docker/scripts/README.md) |

**Topic-to-README lookup:** [architecture/references.md](architecture/references.md)

## Architecture artifacts

| Kind | Location |
| --- | --- |
| Diagram index and export guide | [architecture/README.md](architecture/README.md), [architecture/diagram-export.md](architecture/diagram-export.md) |
| Mermaid sources | [architecture/](architecture/) (`*.mmd`) |
| Backend package map (navigation) | [architecture/BACKEND_PACKAGES.md](architecture/BACKEND_PACKAGES.md) |
| Frontend module map (navigation) | [architecture/FRONTEND_MODULES.md](architecture/FRONTEND_MODULES.md) |

## Auto-generated API artifacts

| Stack | Command | Output |
| --- | --- | --- |
| Java | `cd rag-service && ./mvnw javadoc:javadoc` | `rag-service/target/reports/apidocs` |
| OpenAPI | `/v3/api-docs` when enabled; or `rag-service/scripts/export-openapi.sh` | JSON |
| TypeScript | `cd webapp && npm run doc` | `webapp/docs/api` |

## CI workflows (summary)

| Workflow | When it runs | Role |
| --- | --- | --- |
| [`ci.yml`](../.github/workflows/ci.yml) | `pull_request` on `dev`/`main`/`master`; `push` on `main`/`master` only | **Primary gate**: `mvn verify` (JUnit), classifier `pytest`, webapp lint/typecheck/coverage/build, Playwright **smoke** (excludes `@fullstack`), stack integration, **Docker build smoke** (no push), `@fullstack` E2E. **SonarCloud** runs only on PRs to `main`/`master` and pushes to `main`/`master` (not `dev` PRs); see [devops/README.md](devops/README.md). |
| [`docker-compose-ci.yml`](../.github/workflows/docker-compose-ci.yml) | `pull_request` path `docker/**` on `dev`/`main`/`master`; `push` path `docker/**` on `main`/`master` only | `create-env-all.sh`, `compose_guard.py`, `docker compose config` (merge validation) |
| [`build.yml`](../.github/workflows/build.yml) | `pull_request` + `push` on `main`/`master` | Fast compile-only (`mvn package -DskipTests`, classifier build) - no tests; optional extra check, not the full DAG |
| [`integration.yml`](../.github/workflows/integration.yml) | `push` path filter on `main`/`master` + `workflow_dispatch` | **Stack HTTP integration**: `pytest tests/integration` against Spring **`e2e`** + Postgres (`INTEGRATION_CHECK_OBS=0`; classifier tests skip if no classifier) |
| [`e2e-fullstack.yml`](../.github/workflows/e2e-fullstack.yml) | `push` path filter on `main`/`master` + `workflow_dispatch` | Spring `e2e` + Postgres + Playwright **`@fullstack`** (browser E2E) |
| [`e2e.yml`](../.github/workflows/e2e.yml) | `workflow_dispatch` only | Manual Playwright smoke (same idea as `ci` webapp-e2e) |
| [`gatling.yml`](../.github/workflows/gatling.yml) | Weekly cron + `workflow_dispatch` | **Gatling** JVM scenarios incl. `MixedRealistic*` (skips if `GATLING_BASE_URL` empty) |
| [`micro-benchmark.yml`](../.github/workflows/micro-benchmark.yml) | Weekly cron + `workflow_dispatch` | Optional Python RAG micro-benchmarks (skips if `BENCHMARK_BASE_URL` empty) - no PR gate |
| [`system-checks.yml`](../.github/workflows/system-checks.yml) | `workflow_dispatch` | Playwright API (`npm run test:api` in `webapp/`) against a **running** Spring URL |
| [`sonar.yml`](../.github/workflows/sonar.yml) | `workflow_dispatch` | Ad-hoc SonarCloud analysis; PR analysis runs inside `ci.yml` → `reusable-ci-core` |
| [`build-images.yml`](../.github/workflows/build-images.yml) | `release` **published** + `workflow_dispatch` | Container image builds (GHCR); gates on successful [`ci.yml`](../.github/workflows/ci.yml) for the resolved commit |
| [`deploy.yml`](../.github/workflows/deploy.yml) | `workflow_dispatch` | **Manual deploy** to VM; **gates** on successful [`ci.yml`](../.github/workflows/ci.yml) for the same commit SHA - see [operations/deploy-workflow-audit.md](operations/deploy-workflow-audit.md) |
| [`docs-pages.yml`](../.github/workflows/docs-pages.yml) | `push` on `main` + `workflow_dispatch` | Publishes Javadoc + TypeDoc to **GitHub Pages** (`docs-site/`). First run auto-enables Pages via `configure-pages` `enablement: true`; if blocked by org policy, set **Settings → Pages → Source: GitHub Actions** once and re-run. |

Details and the testing pyramid: [testing/README.md](testing/README.md) (including **Quality gates before deploy**). Workflows vs gates (aligned): [development/e2e-testing-strategy.md](development/e2e-testing-strategy.md). Load tool roles: [performance/README.md](performance/README.md). Release checklist: [operations/release-readiness-checklist.md](operations/release-readiness-checklist.md).

## Sample data

PDFs under `rag-service/src/main/resources/docs/` are optional **ingestion samples** for demos; not required for CI.
