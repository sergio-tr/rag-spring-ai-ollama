# E2E and integration testing strategy (canonical)

**Purpose:** Align **workflow runs**, **quality gates**, and **test layers** for this monorepo. Complements [testing/README.md](../testing/README.md).

**See also:** [../testing/README.md](../testing/README.md) (matrix, RTL guidance), [../operations/deploy-workflow-audit.md](../operations/deploy-workflow-audit.md) (deploy gate), [../operations/local-ci-parity.md](../operations/local-ci-parity.md), [../testing/traceability-legacy-tools.md](../testing/traceability-legacy-tools.md) (load-tool mapping).

---

## Principles

1. **Single PR pipeline:** [`ci.yml`](../../.github/workflows/ci.yml) runs on **pull requests** and **pushes** to **`dev`**, **`main`**, and **`master`**, and calls [`reusable-ci-core.yml`](../../.github/workflows/reusable-ci-core.yml) once. That reusable workflow defines an explicit **`needs:`** DAG: core (backend, classifier, webapp, Playwright smoke) → stack integration → Playwright `@fullstack` → Sonar → **performance** (Gatling smoke + `infra_probe.py`) **only** when the PR targets **`main`** or **`master`**.
2. **Stack truth** without a browser: `tests/integration` pytest + HTTP (inside the DAG).
3. **Full browser E2E:** Playwright `@fullstack` (inside the DAG).
4. **Canonical HTTP smoke** for operators: Playwright **API** project (`webapp/e2e/api`, `npm run test:api`).
5. **External performance:** [`gatling.yml`](../../.github/workflows/gatling.yml) and [`micro-benchmark.yml`](../../.github/workflows/micro-benchmark.yml) remain **cron + `workflow_dispatch`** with optional remote `BASE_URL` (skip when unset).

---

## Workflows vs quality gates

| Workflow | Trigger | Blocks PR merge? | Role |
|----------|---------|------------------|------|
| **`ci.yml`** | PR/push **`dev`**, **`main`**, **`master`** | **Yes** (required check) | Full DAG: backend, classifier, webapp, Playwright smoke, integration, fullstack E2E, Sonar; **+ performance** on PRs to **main/master** only. |
| `integration.yml` | `push` (paths) + `workflow_dispatch` | No (not on `pull_request`) | Manual / path-filtered stack runs without duplicating the DAG. |
| `e2e-fullstack.yml` | `push` (paths) + `workflow_dispatch` | No | Manual / path-filtered fullstack runs. |
| `sonar.yml` | **Push** to `dev` / `main` / `master` only | No | Post-merge branch analysis; **PR** Sonar runs inside `ci.yml`. |
| `build.yml` | PR/push | Optional | Compile-only. |
| `e2e.yml` | `workflow_dispatch` | No | Manual Playwright smoke. |
| `gatling.yml` | Cron + `workflow_dispatch` | No | External Gatling (skips if `GATLING_BASE_URL` empty on schedule). |
| `micro-benchmark.yml` | Cron + `workflow_dispatch` | No | External micro-benchmarks. |
| `system-checks.yml` | `workflow_dispatch` | No | Playwright API against a **running** Spring URL. |
| `build-images.yml` | As configured | Per team | Container images. |
| **`deploy.yml`** | `workflow_dispatch` | N/A | **Gate:** successful **`ci.yml`** run on the **same commit SHA** (full DAG includes fullstack E2E). |

**Branch protection:** Configure required checks to match the **`ci.yml`** run (job names come from `reusable-ci-core.yml`; verify exact names after the first green run).

**Local parity:** [`.github/local/README.md`](../../.github/local/README.md), [`local-ci-parity.md`](../operations/local-ci-parity.md).

---

## Layer mapping (short)

| Layer | Location | Where it runs in CI |
|-------|----------|---------------------|
| Unit / service | Modules | `reusable-ci-core` core jobs |
| Stack integration | `tests/integration` | `integration` job |
| E2E UI (smoke) | `webapp/e2e` | `core_webapp_e2e` job |
| E2E UI (fullstack) | `webapp/e2e` `@fullstack` | `e2e_fullstack` job |
| API / system smoke | `webapp/e2e/api` | Local `npm run test:api`; `system-checks.yml` |

---

## Webapp layout (`webapp/e2e/`)

Domain folders (`public/`, `smoke/`, `auth/`, `projects/`, `documents/`, `chat/`, `config/`, `research/`, `admin/`), shared **`support/helpers.ts`**, and **`fixtures/`** (users, projects, committed files). See [`webapp/e2e/README.md`](../../webapp/e2e/README.md).
