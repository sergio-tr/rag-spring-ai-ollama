# E2E and integration testing strategy (canonical)

**Purpose:** Align **workflow runs**, **quality gates**, and **test layers** for this monorepo. Complements [testing/README.md](../testing/README.md).

**See also:** [../testing/README.md](../testing/README.md) (matrix, RTL guidance), [../operations/deploy-workflow-audit.md](../operations/deploy-workflow-audit.md) (deploy gate), [../testing/traceability-legacy-tools.md](../testing/traceability-legacy-tools.md) (load-tool mapping).

---

## Principles

1. **Fast feedback** on every PR: unit + service tests + webapp lint/typecheck/coverage + Playwright **smoke** (excluding `@fullstack` where configured).
2. **Stack truth** without a browser: `tests/integration` pytest + HTTP.
3. **Full browser E2E** on a heavier path: Playwright `@fullstack` in `e2e-fullstack.yml`.
4. **Canonical HTTP smoke** for operators: Playwright **API** project (`webapp/e2e/api`, `npm run test:api`).
5. **Load tools** (Gatling) and optional Python micro-benchmarks do not block default PR merge unless policy changes.

---

## Workflows vs quality gates

| Workflow | Typical trigger | Blocks PR merge? | Role |
|----------|-----------------|------------------|------|
| `ci.yml` | PR/push `main`/`master` | **Yes** (required check) | Backend `mvn verify`, classifier pytest, webapp lint/typecheck/coverage/build, Playwright smoke (no `@fullstack`). |
| `build.yml` | PR/push | Optional | Fast compile-only; no tests. |
| `integration.yml` | Path filter + `workflow_dispatch` | Optional (often required in mature teams) | Stack HTTP integration (`tests/integration`). |
| `e2e-fullstack.yml` | Path filter + `workflow_dispatch` | Optional on PR unless branch protection adds it | Playwright `@fullstack` + Spring `e2e` + Postgres. |
| `e2e.yml` | `workflow_dispatch` only | No | Manual Playwright smoke. |
| `gatling.yml` | Cron + `workflow_dispatch` | No | Gatling JVM (skips if base URL empty). |
| `micro-benchmark.yml` | Cron + `workflow_dispatch` | No | Python RAG micro-benchmarks (optional). |
| `system-checks.yml` | `workflow_dispatch` | No | Playwright API against a **running** Spring URL. |
| `sonar.yml` | As configured | Per team | SonarCloud analysis. |
| `build-images.yml` | As configured | Per team | Container images. |
| **`deploy.yml`** | `workflow_dispatch` | N/A (manual deploy) | **Gate:** requires successful runs of **`ci.yml`** and **`e2e-fullstack.yml`** on the **same commit SHA** before SSH deploy. |

**Deploy gate detail:** See [deploy-workflow-audit.md](../operations/deploy-workflow-audit.md). Workflows **not** in the deploy gate today include `integration.yml`, `sonar.yml`, and load tests — add them to branch protection or to the deploy script only if policy requires.

---

## Layer mapping (short)

| Layer | Location | Primary workflow |
|-------|----------|------------------|
| Unit / service | Modules | `ci.yml` |
| Stack integration | `tests/integration` | `integration.yml` |
| E2E UI (smoke) | `webapp/e2e` | `ci.yml` |
| E2E UI (fullstack) | `webapp/e2e` `@fullstack` | `e2e-fullstack.yml` |
| API / system smoke | `webapp/e2e/api` | Local `npm run test:api`; `system-checks.yml` |

---

## Webapp layout (`webapp/e2e/`)

Domain folders (`public/`, `smoke/`, `auth/`, `projects/`, `documents/`, `chat/`, `config/`, `research/`, `admin/`), shared **`support/helpers.ts`**, and **`fixtures/`** (users, projects, committed files). See [`webapp/e2e/README.md`](../../webapp/e2e/README.md).
