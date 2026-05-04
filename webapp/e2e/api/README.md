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
| `INTEGRATION_LOGIN_EMAIL` / `INTEGRATION_LOGIN_PASSWORD` | `dev@local.test` / `dev` | Seed user for JWT flows. |
| `CLASSIFIER_URL` | empty | When set, optional classifier `/health` check runs in `system-smoke.chain.spec.ts`. |

## Commands

```bash
cd webapp
# API tests only — skips Next webServer (requires Spring healthy at API_BASE_URL)
npm run test:api

# List API specs without starting Spring (no health probe)
npm run test:api:list
```

`npm run test:api` uses `scripts/test-api.mjs`, which checks **`/actuator/health`** before running **`playwright test --project=api`**.

## CI

PR pipeline job **`playwright_api_smoke`** in [`.github/workflows/reusable-ci-core.yml`](../../.github/workflows/reusable-ci-core.yml) runs **`npm run test:api`** after **`core_webapp`** (same DAG as Vitest + UI smoke). **`core_done`** waits on **`playwright_api_smoke`** so Phase **8D** API specs are merge gates on PRs to **`dev`** (and **`main`/`master`**).

## Layout

- `fixtures/env.ts`, `fixtures/auth.ts` — URLs and login helpers.
- `fixtures/json-contract.ts` — guards so JSON clients never accept HTML/nginx-style error bodies (`assertBodyNotHtml`, `parseJsonExpectNonHtml`).
- `system/system-smoke.chain.spec.ts` — serial smoke: health, auth, config, **lab/status** (typed bundle fields + capability flags), optional classifier, OpenAPI, readiness.
- `lab/lab-status.api.spec.ts` — Lab status typed readiness (`referenceBundle*`, `countsByDatasetKind`).
- `lab/lab-typed-datasets.api.spec.ts` — templates download (magic bytes), experimental-datasets upload (valid + mismatched kind → 422), benchmarks compatible/incompatible (202 vs 400), legacy `POST …/lab/evaluations/rag` → **410**.
- `system/api-errors.api.spec.ts` — unknown routes return JSON error envelopes, not HTML.
- `auth/`, `projects/`, `documents/`, `chat/`, `lab/`, `me/`, `config/` — domain smoke specs (`@api`).
