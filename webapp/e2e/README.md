# Playwright E2E (webapp)

## Conventions

- **Order:** `public/` + `smoke/` → `auth/` → `projects/` → `documents/` → `chat/` → `config/` → `research/` → `admin/`.
- **Stability over raw coverage:** parallel runs where tests are isolated; `test.describe.serial` only when sharing a non-replaceable resource.
- **CI (PRs to `dev` / `main` / `master` via [`ci.yml`](../../.github/workflows/ci.yml) → [`reusable-ci-core.yml`](../../.github/workflows/reusable-ci-core.yml)):** **`core_webapp`** runs **`npm run test:coverage`**. **`core_webapp_e2e`** runs **`npm run test:e2e:ci-fast`** (`@smoke`, `retries=0`, `max-failures=1`, workers=1). **`playwright_api_smoke`** runs **`npm run test:api`**. **`integration`** + **`integration_classifier_required`** run **`pytest tests/integration`**. **`e2e_fullstack`** runs stack curl → **`test:e2e:preflight:only`** → **`test:e2e:fullstack:critical`** (**11** Playwright tests with `@fullstack` + `@critical`, not the full `@fullstack` tree). **`docker_build_smoke`** is limited to **`main`/`master`** PRs and pushes (not **`dev`** PRs).
- **Manual / optional:** [`.github/workflows/e2e-fullstack.yml`](../../.github/workflows/e2e-fullstack.yml) remains an alternate entry for fullstack-only runs outside the main PR DAG.
- **Tags:** `@fullstack` = picked only by **`test:e2e:fullstack`**; UI smoke uses **`grep-invert @fullstack`** so accidental tagging matters.
- **PR critical path:** only tests with **both** `@fullstack` and `@critical` run in CI job `e2e_fullstack` (`npm run test:e2e:fullstack:critical` — **11** tests). List: [`.cursor/context/evidence/m1-ci-e2e-gate/E2E_TAXONOMY.md`](../../.cursor/context/evidence/m1-ci-e2e-gate/E2E_TAXONOMY.md).
- **Closure (`e2e/closure/`, `@closure`):** NIGHTLY / manual — not DEV_REQUIRED; includes long `lab-rag-preset-evidence` (do not add `@critical` without gate review).
- **Stack preflight:** **`npm run test:e2e:stack-preflight`** probes backend `/actuator/health` and web `/en/login` (fails in &lt;60s when the stack is down). Chained before **`test:e2e:preflight`** and **`test:e2e:fullstack`**. Set **`E2E_SKIP_STACK_PREFLIGHT=1`** for offline UI-only runs (e.g. `test:e2e:ci-fast` on port 32123 without Spring).

## Layout

| Folder | Role |
| --- | --- |
| `fixtures/` | Test data factories and committed small files (`fixtures/files/`). |
| `support/` | Shared helpers (`helpers.ts`) — login, API URLs, project creation. |
| `public/` | No session — auth/register shells and client validation. |
| `smoke/` | Minimal stable checks without `@fullstack`. |
| `auth/` | Domain-grouped UI specs tagged `@fullstack` where applicable. **M2:** `auth-email-confirmation.spec.ts` (`@fullstack` `@critical`) — register → pending → confirm (outbox) → login; requires Spring profile **`e2e`** with `RAG_AUTH_EMAIL_CONFIRMATION_ENABLED` + mail outbox. |
| `projects/`, `documents/`, `chat/`, `config/`, `research/`, `settings/`, `admin/` | Other fullstack journeys. |
| **`api/`** | **HTTP-only** (Playwright `request`); `npm run test:api` — no browser, targets Spring `API_BASE_URL`. See [`api/README.md`](api/README.md). |

Canonical testing overview: [`docs/testing/README.md`](../../docs/testing/README.md).
