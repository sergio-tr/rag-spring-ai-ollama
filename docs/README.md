# Documentation hub (`docs/`)

**Global, conceptual documentation** for this monorepo: what the system is, how pieces relate, and where to find **how-to** detail. Module-specific commands and config live in **README files next to code** (`rag-service/`, `webapp/`, `docker/`, etc.).

**Governance:** [development/documentation-guidelines.md](development/documentation-guidelines.md)

## Start here

| Topic | Document |
| --- | --- |
| Product scope and boundaries | [overview/README.md](overview/README.md) — [product-context.md](overview/product-context.md), [thesis-scope.md](overview/thesis-scope.md) |
| System context and diagrams | [architecture/README.md](architecture/README.md), [architecture/system-context.md](architecture/system-context.md) |
| Deployment (conceptual) | [architecture/deployment-model.md](architecture/deployment-model.md), [operations/README.md](operations/README.md) |
| Observability (operator) | [operations/grafana-observability-guide.md](operations/grafana-observability-guide.md), [observability/README.md](../observability/README.md) |
| CI/CD release checklist | [operations/release-readiness-checklist.md](operations/release-readiness-checklist.md), [operations/deploy-workflow-audit.md](operations/deploy-workflow-audit.md) |
| Integration / auth / RAG flows | [architecture/integration-flows.md](architecture/integration-flows.md) |
| Domain concepts | [domain/README.md](domain/README.md) |
| Data model (ER) | [architecture/DATA_MODEL.md](architecture/DATA_MODEL.md) |
| Testing strategy (overview) | [testing/README.md](testing/README.md), [development/e2e-testing-strategy.md](development/e2e-testing-strategy.md) (workflows vs gates), [testing/traceability-legacy-tools.md](testing/traceability-legacy-tools.md) (load-tool mapping) |
| Coverage reports (paths, Sonar) | [coverage/README.md](coverage/README.md) |
| SonarCloud scan locally (match `sonar.yml`) | [development/sonar-local-analysis.md](development/sonar-local-analysis.md), [scripts/README.md](../scripts/README.md) |
| Classifier registry (train → activate) | [development/classifier-registry-demo.md](development/classifier-registry-demo.md) |
| Performance / load (overview) | [performance/README.md](performance/README.md) |
| ADRs | [adr/README.md](adr/README.md) |
| Contributor doc rules | [development/README.md](development/README.md) |

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
| Automation index (Markdown) | `scripts/` | [scripts/README.md](../scripts/README.md) |

**Topic-to-README lookup:** [architecture/references.md](architecture/references.md)

## Architecture artifacts

| Kind | Location |
| --- | --- |
| Diagram index and export guide | [architecture/README.md](architecture/README.md), [architecture/thesis-diagrams.md](architecture/thesis-diagrams.md) |
| Mermaid sources | [architecture/](architecture/) (`*.mmd`) |
| Backend package map (navigation) | [architecture/BACKEND_PACKAGES.md](architecture/BACKEND_PACKAGES.md) |
| Frontend module map (navigation) | [architecture/FRONTEND_MODULES.md](architecture/FRONTEND_MODULES.md) |

## Auto-generated API artifacts

| Stack | Command | Output |
| --- | --- | --- |
| Java | `cd rag-service && ./mvnw javadoc:javadoc` | `rag-service/target/site/apidocs` |
| OpenAPI | `/v3/api-docs` when enabled; or `rag-service/scripts/export-openapi.sh` | JSON |
| TypeScript | `cd webapp && npm run doc` | `webapp/docs/api` |

## CI workflows (summary)

| Workflow | When it runs | Role |
| --- | --- | --- |
| [`ci.yml`](../.github/workflows/ci.yml) | PR/push `main`/`master` | **Primary gate**: `mvn verify` (JUnit), classifier `pytest`, webapp lint/typecheck/coverage/build, Playwright **smoke** (excludes `@fullstack`) |
| [`build.yml`](../.github/workflows/build.yml) | PR/push | Fast compile-only (`mvn package -DskipTests`, classifier build) — no tests |
| [`integration.yml`](../.github/workflows/integration.yml) | PR/push path filter + `workflow_dispatch` | **Stack HTTP integration**: `pytest tests/integration` against Spring **`e2e`** + Postgres (`INTEGRATION_CHECK_OBS=0`; classifier tests skip if no classifier) |
| [`e2e-fullstack.yml`](../.github/workflows/e2e-fullstack.yml) | PR/push path filter + `workflow_dispatch` | Spring `e2e` + Postgres + Playwright **`@fullstack`** (browser E2E) |
| [`e2e.yml`](../.github/workflows/e2e.yml) | `workflow_dispatch` only | Manual Playwright smoke (same idea as `ci` webapp-e2e) |
| [`gatling.yml`](../.github/workflows/gatling.yml) | Weekly cron + `workflow_dispatch` | **Gatling** JVM scenarios incl. `MixedRealistic*` (skips if `GATLING_BASE_URL` empty) |
| [`micro-benchmark.yml`](../.github/workflows/micro-benchmark.yml) | Weekly cron + `workflow_dispatch` | Optional Python RAG micro-benchmarks (skips if `BENCHMARK_BASE_URL` empty) — no PR gate |
| [`system-checks.yml`](../.github/workflows/system-checks.yml) | `workflow_dispatch` | Playwright API (`npm run test:api` in `webapp/`) against a **running** Spring URL |
| [`sonar.yml`](../.github/workflows/sonar.yml) | As configured | SonarCloud analysis |
| [`build-images.yml`](../.github/workflows/build-images.yml) | As configured | Container image builds |
| [`deploy.yml`](../.github/workflows/deploy.yml) | `workflow_dispatch` | **Manual deploy** to VM; **gates** on `ci.yml` + `e2e-fullstack.yml` at same SHA — see [operations/deploy-workflow-audit.md](operations/deploy-workflow-audit.md) |

Details and the testing pyramid: [testing/README.md](testing/README.md) (including **Quality gates before deploy**). Workflows vs gates (aligned): [development/e2e-testing-strategy.md](development/e2e-testing-strategy.md). Load tool roles: [performance/README.md](performance/README.md). Release checklist: [operations/release-readiness-checklist.md](operations/release-readiness-checklist.md).

## Sample data

PDFs under `rag-service/src/main/resources/docs/` are optional **ingestion samples** for demos; not required for CI.
