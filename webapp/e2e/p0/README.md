# P0 full-application E2E

Consolidated Playwright suite validating main product phases from the UI.

## Run

```bash
cd webapp
E2E_ADMIN_ENABLED=1 npm run test:e2e:p0
```

Requires live demo stack (default UI: `https://127.0.0.1:8444`).

## CI

Nightly / manual: `.github/workflows/e2e-p0-playwright.yml` on **self-hosted** runner.

```bash
# From repo root
bash scripts/ci/start-e2e-stack.sh
bash scripts/ci/wait-e2e-stack.sh
cd webapp && E2E_ADMIN_ENABLED=1 PLAYWRIGHT_RETRIES=1 npm run test:e2e:p0
```

Fast lane (no P0-06 RAG): `npm run test:e2e:p0:fast`

Evidence: `docs/evidence/p0-playwright-cicd-integration-20250701/`

## Tags

`@p0-app` `@p0-app-fast` `@p0-app-rag` (P0-06 only) `@fullstack`

## Layout

- `fixtures/` - network/console guard + extended `test`
- `page-objects/` - Login, Projects, Chat, Documents, Admin, Lab
- `p0-full-application.spec.ts` - nine P0 tests

Evidence: `docs/evidence/full-application-e2e-playwright-20250701/`
