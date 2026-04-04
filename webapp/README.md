# RAG webapp (Next.js)

Product UI for the RAG platform: projects, documents, settings (user/project RAG config, presets), Lab, and admin views. Uses **TanStack Query**, **Zustand** (`src/store/app.store.ts`), and **next-intl**.

## Environment

Copy `.env.example` to `.env` (or use `./scripts/create-env-webapp.sh` from the repo root). Key variables:

| Variable | Role |
|----------|------|
| `NEXT_PUBLIC_API_BASE_URL` | Spring Boot backend origin (e.g. `http://localhost:9000`). Empty when the UI is served behind the same origin as the API (reverse proxy). |
| `NEXT_PUBLIC_RAG_API_PREFIX` | Must match Spring `rag.api.product-base-path` (see `.env.example`). |
| `NEXT_PUBLIC_TIMEZONE` | IANA timezone for next-intl (e.g. `UTC`). |
| `NEXT_PUBLIC_AUTH_ACCESS_COOKIE_NAME` / `NEXT_PUBLIC_AUTH_REFRESH_COOKIE_NAME` | Cookie names for session route handlers. |

**Product API usage (non-exhaustive):** under `NEXT_PUBLIC_RAG_API_PREFIX` (default in `.env.example`): `GET/POST/PATCH/DELETE …/projects`, `PUT …/activate`, `GET/POST …/projects/{id}/documents`, `GET/PUT …/config/user`, `GET/PUT/DELETE …/config/project/{id}`, `GET …/config/schema`, `GET/POST/DELETE …/presets`. Auth: `/api/auth/login`, `/api/auth/register`, refresh via `/api/auth/refresh` (see `src/lib/api-client.ts`). Canonical contract: OpenAPI from the backend (`/v3/api-docs` when enabled) and `src/lib/api-client.ts`.

### Chat (SSE + conversation context)

The **Chat** page (`src/app/[locale]/(app)/chat/page.tsx`) loads conversations with `GET {product}/projects/{id}/conversations`, history with `GET {product}/conversations/{id}/messages`, and sends user messages via **`postSseJson`** (`src/lib/sse-post.ts`): `POST {product}/conversations/{id}/messages` with `Accept: text/event-stream` and JSON body `{ content, llmModel? }` (matches `PostMessageRequest` in the backend). Each POST includes a W3C **`traceparent`** header from `src/lib/traceparent.ts` for OpenTelemetry correlation when observability is enabled.

Use **`PATCH {product}/conversations/{id}`** from the same UI for `presetId` / `clearPreset` and `documentFilter` (subset of project document UUIDs). Model and preset dropdowns use **`GET {product}/models`** and **`GET {product}/presets`**.

### Research Lab

Routes under `src/app/[locale]/(app)/lab/`: **`useLabStatus`** (`src/features/lab/hooks/use-lab-status.ts`) calls **`GET {product}/lab/status`** to enable/disable evaluation buttons and show classifier availability. Long operations use **`POST {product}/lab/evaluations/*`** and **`POST {product}/lab/classifier/*`** with default **async** (**HTTP 202** + `LabJobAcceptedDto`). Progress is tracked with **`followLabJob`** (`src/lib/lab-job-follow.ts`): **polling** via `GET {product}/lab/jobs/{id}` (`src/lib/async-task.ts`) or **SSE** via `GET …/events` (`src/lib/lab-job-sse.ts`). Optional **`?sync=true`** returns inline JSON for quick local checks. Lab results are **reports only** (ADR 0001 — no silent writes to presets or project config).

Playwright specs under `e2e/research/` (Lab) may skip or time out if the classifier URL is unset or evaluation data is not loaded; see messages in the Lab UI when `datasets.enabled` is false.

## Commands

```bash
npm install
npm run dev          # http://localhost:3000 — set NEXT_PUBLIC_API_BASE_URL to your rag-service
npm run typecheck
npm run build
npm run test         # Vitest (unit)
npm run test:coverage # Vitest + v8 coverage gate (80% lines/branches/functions/statements on instrumented `src/**`; see vitest.config.ts excludes)
npm run test:e2e          # Playwright UI smoke: chromium only, excludes @fullstack
npm run test:e2e:fullstack # Playwright UI @fullstack — Spring e2e + DB (see e2e/README.md)
npm run test:api          # Playwright API (HTTP only, Spring API_BASE_URL) — see e2e/api/README.md
```

After `npm run test:coverage`, open **`coverage/index.html`** in a browser for the HTML report (folder is gitignored at repo root). `lcov.info` is generated for IDE/Sonar extensions.

**React / Testing Library (behavior-first):** see [docs/testing/README.md](../docs/testing/README.md) (section *React / Testing Library (webapp)*) and [docs/adr/0004-react-testing-library-behavior-first.md](../docs/adr/0004-react-testing-library-behavior-first.md). Shared `renderWithProviders` / MSW are deferred until feature-level UI tests are added (same doc).

OpenAPI for the backend: `GET http://<backend>:9000/v3/api-docs` (see `rag-service/README.md`).

## Authentication and long sessions

`apiFetch` attaches the JWT, retries once on **401** via `/api/auth/refresh`, then throws. If the session cannot be refreshed, **`SessionExpiredBridge`** redirects to `/{locale}/login`. Login and register calls use `skipCredentials: true` and do not trigger that redirect.

## E2E

Layout and CI policy: **`e2e/README.md`**. **`@fullstack`** specs live under domain folders (`e2e/auth/`, `e2e/projects/`, …) and require seed credentials (`E2E_SEED_EMAIL` / `E2E_SEED_PASSWORD`, defaults `dev@local.test` / `dev`) plus a reachable API. Canonical strategy: [`docs/development/e2e-testing-strategy.md`](../docs/development/e2e-testing-strategy.md); config: `playwright.config.ts`.

## TypeDoc

```bash
npm run doc
```

Output: `docs/api/` (often gitignored).
