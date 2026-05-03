# Playwright E2E (webapp)

## Conventions

- **Order:** `public/` + `smoke/` → `auth/` → `projects/` → `documents/` → `chat/` → `config/` → `research/` → `admin/`.
- **Stability over raw coverage:** parallel runs where tests are isolated; `test.describe.serial` only when sharing a non-replaceable resource.
- **CI (PRs to `dev` / `main` / `master` via [`ci.yml`](../../.github/workflows/ci.yml) → [`reusable-ci-core.yml`](../../.github/workflows/reusable-ci-core.yml)):** **`core_webapp`** runs **`npm run test:coverage`** (Vitest = same suite as **`npm run test`**, with Istanbul output). **`core_webapp_e2e`** runs **`npm run test:e2e`** (`chromium`, **`--grep-invert @fullstack`**, **`testIgnore`** excludes **`e2e/api/**`**). **`playwright_api_smoke`** runs **`npm run test:api`** against Spring **`e2e`** + Postgres (Phase 8D). **`integration`** + **`integration_classifier_required`** run **`pytest tests/integration`** (Phase 8E HTTP extras). **`e2e_fullstack`** runs **`npm run test:e2e:fullstack`** (`--grep @fullstack`) after integration — browser + proxy + live backend (Phase 8C). **`docker_build_smoke`** is limited to **`main`/`master`** PRs and pushes (not **`dev`** PRs).
- **Manual / optional:** [`.github/workflows/e2e-fullstack.yml`](../../.github/workflows/e2e-fullstack.yml) remains an alternate entry for fullstack-only runs outside the main PR DAG.
- **Tags:** `@fullstack` = picked only by **`test:e2e:fullstack`**; UI smoke uses **`grep-invert @fullstack`** so accidental tagging matters.

## Layout

| Folder | Role |
| --- | --- |
| `fixtures/` | Test data factories and committed small files (`fixtures/files/`). |
| `support/` | Shared helpers (`helpers.ts`) — login, API URLs, project creation. |
| `public/` | No session — auth/register shells and client validation. |
| `smoke/` | Minimal stable checks without `@fullstack`. |
| `auth/`, `projects/`, `documents/`, `chat/`, `config/`, `research/`, `settings/`, `admin/` | Domain-grouped UI specs tagged `@fullstack` where applicable (e.g. Phase 8C journeys under `projects/`, `chat/`, `settings/`). |
| **`api/`** | **HTTP-only** (Playwright `request`); `npm run test:api` — no browser, targets Spring `API_BASE_URL`. See [`api/README.md`](api/README.md). |

Canonical testing overview: [`docs/testing/README.md`](../../docs/testing/README.md).
