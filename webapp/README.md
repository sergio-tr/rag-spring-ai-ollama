# RAG webapp (Next.js)

Product UI for the RAG platform: projects, documents, settings (user/project RAG config, presets), Lab, and admin views. Uses **TanStack Query**, **Zustand** (`src/store/app.store.ts`), and **next-intl**.

**Target architecture (frozen model):** [Platform subsystems — Workspace / Product](../docs/architecture/target-architecture.md).

## Environment

Copy `.env.example` to `.env` (or use `./docker/scripts/create-env-webapp.sh` from the repo root). Key variables:

| Variable | Role |
| --- | --- |
| `NEXT_PUBLIC_API_BASE_URL` | Spring Boot backend origin (e.g. `http://localhost:9000`). Empty when the UI is served behind the same origin as the API (reverse proxy). |
| `NEXT_PUBLIC_RAG_API_PREFIX` | Must match Spring `rag.api.product-base-path` (see `.env.example`). |
| `NEXT_PUBLIC_TIMEZONE` | IANA timezone for next-intl (e.g. `UTC`). |
| `NEXT_PUBLIC_AUTH_ACCESS_COOKIE_NAME` / `NEXT_PUBLIC_AUTH_REFRESH_COOKIE_NAME` | Cookie names for session route handlers. |
| `NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED` | Show “Continue with Google” button (requires backend OAuth enabled and configured). |

> **Important — `NEXT_PUBLIC_*` is baked at build time.** Next.js inlines every `process.env.NEXT_PUBLIC_*` reference into the client bundle during `next build`. Changing these values requires a rebuild and a process restart for the browser to see the new value:
>
> - **Local dev** (`npm run dev`): edit `webapp/.env`/`webapp/.env.local`, then **stop and restart** `npm run dev`.
> - **Local production** (`npm run build && npm run start`): edit env, then run **both** `npm run build` and `npm run start` again.
> - **Docker (Dockerfile)**: the `NEXT_PUBLIC_*` values are passed as `ARG` lines at build time. Set them in the build environment (or via `docker compose build --build-arg`) and **rebuild the image** (`docker compose build webapp`).
> - **Docker Compose**: `docker/docker-compose.yml` forwards `NEXT_PUBLIC_*` from `webapp/.env` into the build args of the `webapp` service. After editing `webapp/.env`, run **`docker compose build webapp && docker compose up -d webapp`** — `docker compose restart webapp` alone will NOT bake new values into the bundle.
>
> The Google CTA on `/login` and `/register` reads `NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED` and the backend OAuth route (`/api/v5/auth/oauth/google/start`) is rendered as a plain `<a>` (full-page navigation), not a next-intl `<Link>`, so the browser does not prepend the active locale to that absolute API path.

**Product API usage (non-exhaustive):** under `NEXT_PUBLIC_RAG_API_PREFIX` (default in `.env.example`): `GET/POST/PATCH/DELETE …/projects`, `PUT …/activate`, `GET/POST …/projects/{id}/documents`, `GET/PUT …/me/preferences`, `GET/PUT …/me/personalization`, `GET …/me/summary`, `GET …/me/documents`, `POST …/me/account/export` (202) + `GET …/me/account/jobs/{id}` + `GET …/me/account/export/{id}/download`, `GET/PUT …/config/user` (legacy; prefer `/me/*` for UI prefs), `GET/PUT/DELETE …/config/project/{id}`, `GET …/config/schema`, `GET/POST/DELETE …/presets`. Auth (via `authApiPath`): `{NEXT_PUBLIC_RAG_API_PREFIX}/auth/login`, `…/register` (**may return 202** when email confirmation is enabled), `…/confirm-email`, `…/forgot-password`, `…/reset-password`, `…/me`, **OAuth** `GET …/oauth/google/start`, `GET …/oauth/google/callback` (backend redirect), `POST …/oauth/exchange` (SPA callback page), refresh via the BFF cookie route (see `src/lib/api-client.ts`). With default prefix **`/api/v5`**, the Google button targets **`/api/v5/auth/oauth/google/start`**. Legacy `/api/auth/*` may still work during transition. Canonical contract: OpenAPI from the backend (`/v3/api-docs` when enabled) and `src/lib/api-client.ts`.

### Chat (SSE + conversation context)

The **Chat** page (`src/app/[locale]/(app)/chat/page.tsx`) loads conversations with `GET {product}/projects/{id}/conversations`, history with `GET {product}/conversations/{id}/messages`, and sends user messages via **`postSseJson`** (`src/lib/sse-post.ts`): `POST {product}/conversations/{id}/messages` with `Accept: text/event-stream` and JSON body `{ content, llmModel? }` (matches `PostMessageRequest` in the backend). Each POST includes a W3C **`traceparent`** header from `src/lib/traceparent.ts` for OpenTelemetry correlation when observability is enabled.

Use **`PATCH {product}/conversations/{id}`** from the same UI for `presetId` / `clearPreset` and `documentFilter` (subset of project document UUIDs). Model and preset dropdowns use **`GET {product}/models`** and **`GET {product}/presets`**.

**Move conversation:** `POST {product}/projects/{sourceProjectId}/conversations/{conversationId}/move?destinationProjectId=` (**204 No Content**) reassigns the chat to another project you own; only **chat-local** corpus documents move with it, not shared project documents. The UI clears the persisted document subset after a move (`MoveConversationDialog`, `useMoveConversation`).

### Research Lab

Routes under `src/app/[locale]/(app)/lab/`: **`useLabStatus`** (`src/features/lab/hooks/use-lab-status.ts`) calls **`GET {product}/lab/status`** to enable/disable evaluation buttons and show classifier availability. Long operations use **`POST {product}/lab/evaluations/*`** and **`POST {product}/lab/classifier/*`** with default **async** (**HTTP 202** + `LabJobAcceptedDto`). Progress is tracked with **`followLabJob`** (`src/lib/lab-job-follow.ts`): **polling** via `GET {product}/lab/jobs/{id}` (`src/lib/async-task.ts`) or **SSE** via `GET …/events` (`src/lib/lab-job-sse.ts`). Optional **`?sync=true`** returns inline JSON for quick local checks. Lab results are **reports only** (ADR 0001 — no silent writes to presets or project config).

Playwright specs under `e2e/research/` (Lab) may skip or time out if the classifier URL is unset or evaluation data is not loaded; see messages in the Lab UI when `datasets.enabled` is false.

## Commands

The repository **does not commit** `package-lock.json` (ignored at repo root). Use **`npm install`** locally, in CI, and in the `webapp/Dockerfile` (no `npm ci`). Node **22** is set in `package.json` `engines` and in GitHub Actions via `node-version-file: webapp/package.json`. CI caches `~/.npm` with [`.github/actions/cache-npm-webapp`](../.github/actions/cache-npm-webapp/action.yml) keyed by `webapp/package.json`.

```bash
npm install
npm run dev          # http://localhost:3000 — set NEXT_PUBLIC_API_BASE_URL to your rag-service
npm run typecheck
npm run build
npm run test         # Vitest (unit)
npm run test:coverage # Vitest + v8 coverage gate (80% lines/statements/functions/branches on instrumented `src/**`; see vitest.config.ts — App Router pages/layouts and a few lab/layout shells are excluded from the gate; behavior is covered via E2E or shared components)
npm run test:e2e          # Playwright UI smoke: chromium only, excludes @fullstack
npm run test:e2e:fullstack # Playwright UI @fullstack — Spring e2e + DB (see e2e/README.md)
npm run test:api          # Playwright API (HTTP only, Spring API_BASE_URL) — see e2e/api/README.md
```

After `npm run test:coverage`, open **`coverage/index.html`** in a browser for the HTML report (folder is gitignored at repo root). `lcov.info` is generated for IDE/Sonar extensions.

**React / Testing Library (behavior-first):** see [docs/testing/README.md](../docs/testing/README.md) (section *React / Testing Library (webapp)*) and [docs/adr/0004-react-testing-library-behavior-first.md](../docs/adr/0004-react-testing-library-behavior-first.md). Shared `renderWithProviders` / MSW are deferred until feature-level UI tests are added (same doc).

OpenAPI for the backend: `GET http://<backend>:9000/v3/api-docs` (see `rag-service/README.md`).

## Authentication and long sessions

`apiFetch` attaches the JWT, retries once on **401** via the BFF route **`{NEXT_PUBLIC_RAG_API_PREFIX}/auth/refresh`** (default **`/api/v5/auth/refresh`**), then throws. If the session cannot be refreshed, **`SessionExpiredBridge`** redirects to `/{locale}/login`. Login and register calls use `skipCredentials: true` and do not trigger that redirect.

## E2E

Layout and CI policy: **`e2e/README.md`**. **`@fullstack`** specs live under domain folders (`e2e/auth/`, `e2e/projects/`, …) and require seed credentials (`E2E_SEED_EMAIL` / `E2E_SEED_PASSWORD`, defaults `dev@local.test` / `dev`) plus a reachable API. Canonical strategy: [`docs/development/e2e-testing-strategy.md`](../docs/development/e2e-testing-strategy.md); config: `playwright.config.ts`.

## TypeDoc

```bash
npm run doc
```

Output: `docs/api/` (often gitignored).
