# Playwright E2E (webapp)

## Conventions

- **Order:** `public/` + `smoke/` → `auth/` → `projects/` → `documents/` → `chat/` → `config/` → `research/` → `admin/`.
- **Stability over raw coverage:** parallel runs where tests are isolated; `test.describe.serial` only when sharing a non-replaceable resource.
- **CI:** PR **smoke** runs `npm run test:e2e` (`--project=chromium` + `--grep-invert @fullstack`) — no live Spring API required for the selected UI specs.
- **Fullstack UI:** `npm run test:e2e:fullstack` — Spring `e2e` profile + Postgres; workflow [`.github/workflows/e2e-fullstack.yml`](../../.github/workflows/e2e-fullstack.yml), **not** mixed into the default CI matrix.
- **Tags:** `@fullstack` = needs real API + DB; optional `@smoke` = minimal fast checks (subset philosophy; CI still uses invert-`@fullstack` unless you add a dedicated grep).

## Layout

| Folder | Role |
| --- | --- |
| `fixtures/` | Test data factories and committed small files (`fixtures/files/`). |
| `support/` | Shared helpers (`helpers.ts`) — login, API URLs, project creation. |
| `public/` | No session — auth/register shells and client validation. |
| `smoke/` | Minimal stable checks without `@fullstack`. |
| `auth/`, `projects/`, `documents/`, `chat/`, `config/`, `research/`, `admin/` | Domain-grouped UI specs tagged `@fullstack` where applicable. |
| **`api/`** | **HTTP-only** (Playwright `request`); `npm run test:api` — no browser, targets Spring `API_BASE_URL`. See [`api/README.md`](api/README.md). |

Canonical testing overview: [`docs/testing/README.md`](../../docs/testing/README.md).
