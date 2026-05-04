# RAG webapp (Next.js)

Product UI for the RAG platform: projects, documents, settings (user/project RAG config, presets), Lab, and admin views. Uses **TanStack Query**, **Zustand** (`src/store/app.store.ts`), and **next-intl**.

**Target architecture (frozen model):** [Platform subsystems — Workspace / Product](../docs/architecture/target-architecture.md).

## Environment

Copy `.env.example` to `.env` (or use `./docker/scripts/create-env-webapp.sh` from the repo root). Key variables:

| Variable | Role |
| --- | --- |
| `NEXT_PUBLIC_API_BASE_URL` | Spring Boot backend origin (e.g. `http://localhost:9000`). Empty when the UI is served behind the same origin as the API (reverse proxy). **If empty**, browser calls (including `POST /api/v5/auth/oauth/exchange` on the Google callback page) target **the same host:port as Next.js** — fine behind nginx on `:80`, but **404 / “OAuth sign-in failed”** if you browse only the webapp host port (e.g. `:8081`) without a proxy; then set this to `http://127.0.0.1:9000` and rebuild. |
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
> The Google CTA on `/login` and `/register` reads `NEXT_PUBLIC_OAUTH_GOOGLE_ENABLED` and targets `/api/v5/auth/oauth/google/start` as a plain `<a>` (full-page navigation), not a next-intl `<Link>`, so the browser does not prepend the active locale to that API path. **Google Cloud Console** must list the backend callback URL exactly as composed from `RAG_AUTH_BACKEND_BASE_URL` + `RAG_AUTH_OAUTH_GOOGLE_REDIRECT_PATH` (see `rag-service/README.md` and `rag-service/.env.example`) to avoid `redirect_uri_mismatch`.

**Product API usage (non-exhaustive):** under `NEXT_PUBLIC_RAG_API_PREFIX` (default in `.env.example`): `GET/POST/PATCH/DELETE …/projects`, `PUT …/activate`, `GET/POST …/projects/{id}/documents`, `GET/PUT …/me/preferences`, `GET/PUT …/me/personalization` (Settings → User config: structured locale/theme fields; optional read-only JSON diagnostics), `GET …/me/summary`, `GET …/me/documents` (Settings → Data: plain-language usage summary and document library; optional collapsible API reference), `POST …/me/account/export` (202) + `GET …/me/account/jobs/{id}` + `GET …/me/account/export/{id}/download` (Settings → Account: persisted job lifecycle, resume after navigation, trace + explicit ZIP download), `GET/PUT …/config/user` (legacy; prefer `/me/*` for UI prefs), `GET/PUT/DELETE …/config/project/{id}` (Settings → Project: schema-driven fields; destructive clear uses a confirmation dialog), `GET …/config/schema`, `GET/POST/DELETE …/presets` (Settings → Presets: structured fields + read-only payload preview; JSON paste/export under Advanced). Auth (via `authApiPath`): `{NEXT_PUBLIC_RAG_API_PREFIX}/auth/login`, `…/register` (**may return 202** when email confirmation is enabled), `…/confirm-email`, `…/forgot-password`, `…/reset-password`, `…/me`, **OAuth** `GET …/oauth/google/start`, `GET …/oauth/google/callback` (backend redirect), `POST …/oauth/exchange` (SPA callback page), refresh via the BFF cookie route (see `src/lib/api-client.ts`). With default prefix **`/api/v5`**, the Google button targets **`/api/v5/auth/oauth/google/start`**. Legacy `/api/auth/*` may still work during transition. Canonical contract: OpenAPI from the backend (`/v3/api-docs` when enabled) and `src/lib/api-client.ts`.

### Chat (SSE + conversation context)

The **Chat** page (`src/app/[locale]/(app)/chat/page.tsx`) loads conversations with `GET {product}/projects/{id}/conversations`, history with `GET {product}/conversations/{id}/messages`, and sends user messages via **`postSseJson`** (`src/lib/sse-post.ts`): `POST {product}/conversations/{id}/messages` with `Accept: text/event-stream` and JSON body `{ content, llmModel? }` (matches `PostMessageRequest` in the backend). Each POST includes a W3C **`traceparent`** header from `src/lib/traceparent.ts` for OpenTelemetry correlation when observability is enabled.

Use **`PATCH {product}/conversations/{id}`** from the same UI for `presetId` / `clearPreset` and `documentFilter` (subset of project document UUIDs). Model and preset dropdowns use **`GET {product}/models`** and **`GET {product}/presets`**.

**Move conversation:** `POST {product}/projects/{sourceProjectId}/conversations/{conversationId}/move?destinationProjectId=` (**204 No Content**) reassigns the chat to another project you own; only **chat-local** corpus documents move with it, not shared project documents. The UI clears the persisted document subset after a move (`MoveConversationDialog`, `useMoveConversation`).

### Research Lab

Routes under `src/app/[locale]/(app)/lab/`: **`useLabStatus`** (`src/features/lab/hooks/use-lab-status.ts`) calls **`GET {product}/lab/status`** (reference bundle flags, classifier availability). Canonical benchmarks use **`POST {product}/lab/benchmarks/{kind}/runs`** (**HTTP 202** + async task). **`LabEvaluationRunCard`** drives LLM (`LLM_JUDGE_QA`), embedding (`EMBEDDING_RETRIEVAL`), and RAG preset (`RAG_PRESET_END_TO_END`) flows under **`/lab/evaluation/llm`**, **`/lab/evaluation/embedding`**, **`/lab/evaluation/rag`**. Classifier flows remain **`POST {product}/lab/classifier/*`**. Progress uses **`followLabJob`** (`src/lib/lab-job-follow.ts`): polling **`GET {product}/lab/jobs/{id}`** or SSE **`GET …/events`**. After **`SUCCEEDED`**, the UI loads MVP summaries via **`GET …/lab/runs/{id}/export/mvp/{items.json,rollups.json}`** and offers **`items.csv`**. **`useLabJobSessionStore`** persists bounded session rows (including **`evaluationRunId`** when returned). Lab outputs are **reports only** (ADR 0001).

The Lab **overview** mounts **`LabExperimentalDatasetPanel`**, wired to **`GET …/lab/dataset-templates/{kind}`**, **`POST …/lab/experimental-datasets`**, **`GET …/lab/experimental-datasets`**, **`GET …/lab/experimental-datasets/{id}/validation`**.

Playwright specs under `e2e/research/` (Lab) may skip or time out if the classifier URL is unset or the packaged evaluation workbook is unavailable (`datasets.enabled` follows `datasets.questionCount` > 0 from `GET {product}/lab/status`; see reference bundle fields `referenceBundleAvailable` / `referenceBundleValid`).

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

After a successful session commit, the webapp schedules a **silent refresh** about **two minutes before** the access JWT `exp` (`src/lib/auth-access-scheduler.ts`), so active users are less likely to hit 401 during SSE or ordinary requests. Backend defaults (override via env): access token **3600s**, refresh token **604800s** — see `rag-service/src/main/resources/application.properties` (`rag.jwt.access-ttl-seconds`, `rag.jwt.refresh-ttl-seconds`).

### Local HTTP / LAN / mobile smoke

- **`npm run dev`** — listens on `localhost:3000` only (default Next dev server).
- **`npm run dev:lan`** — binds **`0.0.0.0:3000`** so other devices on the LAN can load `http://<your-host-ip>:3000`.
- Point **`NEXT_PUBLIC_API_BASE_URL`** at a backend URL reachable from the phone (often `http://<your-host-ip>:9000`).
- Add that browser origin to **`RAG_CORS_ALLOWED_ORIGINS`** on Spring (comma-separated patterns). Dev defaults allow `http(s)://localhost:*` and `http(s)://127.0.0.1:*` only — **not** arbitrary LAN IPs.

### HTTPS in local dev

- Prefer the **reverse-proxy** profile described in **`docker/compose.dev-proxy.yml`** (HTTP on `${REVERSE_PROXY_DEV_HTTP_PORT:-80}`, HTTPS on `${REVERSE_PROXY_DEV_HTTPS_PORT:-8444}`). Mount TLS material via `TLS_CERT_PATH` / `TLS_KEY_PATH` (see `reverse-proxy` Dockerfile / README). Tools such as **[mkcert](https://github.com/FiloSottile/mkcert)** are suitable for generating trusted local certificates.
- Alternatively terminate TLS only on Next (custom server) — not the default in this repo; proxy path keeps one origin for browser + BFF cookies.

**Chat layout / toolbar overflow (product notes):** [`docs/frontend/chat-layout.md`](../docs/frontend/chat-layout.md).

## E2E

Layout and CI policy: **`e2e/README.md`**. **`@fullstack`** specs live under domain folders (`e2e/auth/`, `e2e/projects/`, …) and require seed credentials (`E2E_SEED_EMAIL` / `E2E_SEED_PASSWORD`, defaults `dev@local.test` / `dev`) plus a reachable API. Canonical strategy: [`docs/development/e2e-testing-strategy.md`](../docs/development/e2e-testing-strategy.md); config: `playwright.config.ts`.

## TypeDoc (generated HTML API docs)

From **`webapp/`**:

```bash
npm run doc
```

| Topic | Detail |
| ----- | ------ |
| **Output directory** | `webapp/docs/api/` (configured in [`typedoc.json`](typedoc.json); **`entryPoints`** include `src/app`, `src/components`, `src/features`, `src/hooks`, `src/store`, `src/lib`, `src/types`) |
| **Git** | **Do not commit** generated HTML — `webapp/docs/api/` is listed in the repo root [`.gitignore`](../.gitignore). Treat docs as **local output or CI artifacts** only. |
| **CI** | Workflow [`.github/workflows/reusable-ci-core.yml`](../.github/workflows/reusable-ci-core.yml) job **`core_webapp`** runs `npm run doc` after `npm run build` and uploads the folder as artifact **`webapp-typedoc-api`** (retention 14 days). Download from the workflow run’s **Artifacts** to browse offline. |

Operational detail stays in this README per [`documentation-guidelines.md`](../docs/development/documentation-guidelines.md).
