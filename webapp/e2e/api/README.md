# Playwright API tests (HTTP only, no browser)

**Role:** Smoke and contract checks against the **Spring Boot** origin (`API_BASE_URL`), using `APIRequestContext` (`request` fixture). No Next.js server required.

## Policy (coexistence)

| Runner | Scope |
| --- | --- |
| **`webapp/e2e/api/**` (this tree)** | Canonical **API + system-style** smoke: auth, product paths, optional classifier/OpenAPI/readiness. |
| **`tests/integration/` (pytest)** | **Backend integration** depth: full HTTP matrices, lab jobs, observability, classifier contracts — **not** duplicated line-for-line in Playwright. |

**Rule:** Do not copy pytest JSON field-by-field assertions into Playwright; keep Playwright API tests **shallow** (status + key fields) unless promoting a new smoke invariant.

## Environment

| Variable | Default | Purpose |
| --- | --- | --- |
| `API_BASE_URL` | `http://127.0.0.1:9000` | Spring Boot base (also accepts `INTEGRATION_BACKEND_URL`). |
| `RAG_API_PRODUCT_BASE_PATH` | *(match backend `rag.api.product-base-path`)* | Product API prefix (also `INTEGRATION_RAG_PRODUCT_BASE_PATH`). |
| `RAG_API_LEGACY_BASE_PATH` | *(match backend `rag.api.legacy-base-path`)* | Legacy prefix for lightweight query checks. |
| `INTEGRATION_LOGIN_EMAIL` / `INTEGRATION_LOGIN_PASSWORD` | `dev@local.test` / `dev` | Seed user for JWT flows. |
| `CLASSIFIER_URL` | empty | When set, optional classifier `/health` check runs in `system-smoke.chain.spec.ts`. |

## Commands

```bash
cd webapp
# API tests only — skips Next webServer
npm run test:api
```

Uses `PLAYWRIGHT_SKIP_WEBSERVER=1` internally so `npm run start` is not spawned.

## Layout

- `fixtures/env.ts`, `fixtures/auth.ts` — URLs and login helpers.
- `system/system-smoke.chain.spec.ts` — serial smoke: health, auth, config, lab, optional classifier, OpenAPI, readiness, legacy query envelope.
- `auth/`, `projects/`, `documents/`, `chat/` — domain smoke specs (`@api`).
