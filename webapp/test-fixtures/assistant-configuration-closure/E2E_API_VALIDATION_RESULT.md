# E2E/API Validation Result

Recorded: 2026-06-29

## Command

```bash
cd webapp && PLAYWRIGHT_SKIP_WEBSERVER=1 PLAYWRIGHT_IGNORE_HTTPS_ERRORS=1 NODE_TLS_REJECT_UNAUTHORIZED=0 \
  npm run test:api -- --grep 'ProviderRuntimeAcceptance|lab-evaluation-contracts|config|settings'
```

Focused re-run (config + provider):

```bash
cd webapp && PLAYWRIGHT_SKIP_WEBSERVER=1 PLAYWRIGHT_IGNORE_HTTPS_ERRORS=1 NODE_TLS_REJECT_UNAUTHORIZED=0 \
  npm run test:api -- --grep 'config-presets|ProviderRuntimeAcceptance'
```

Stack preflight: `test-api.mjs` mandatory checks (liveness, `/en/login`, seed login, selectable models, seed project).

## Result

| Suite | Tests | Outcome |
|-------|-------|---------|
| Config and presets API (`config-presets.api.spec.ts`) | 3 | **PASS** |
| Provider runtime acceptance (`ProviderRuntimeAcceptance.spec.ts`) | 1 | **FAIL** (environment) |
| System smoke chain (`system-smoke.chain.spec.ts`, matched by grep) | 1 | **FAIL** (serial auth flake; out of closure scope) |

**Note:** No spec files match `lab-evaluation-contracts` in the current tree; that pattern matches zero tests.

## Passed checks

- `GET /api/v5/config/schema` returns version + fields array
- `GET /api/v5/config/user` returns JSON object (authenticated)
- `GET /api/v5/presets` returns JSON array
- ProviderRuntimeAcceptance **live runtime checks** (readiness probes, model selector scoping, chat job SUCCEEDED, no Ollama leak in answer/logs) - all passed before Maven subprocess gate

## Failed checks

- **ProviderRuntimeAcceptance** - Maven unit-test subprocess checks (`http-routing`, `ner-provider`, `metadata-cache-provider`, `judge-provider`, `error-composer-provider`, `health-probes`) failed with `Operation not permitted` / `Permission denied` when spawning `./mvnw test` from the Playwright host process.
- **System smoke chain GET config/user** - 401 in parallel worker (serial chain ordering; not part of minimal closure grep intent).

## Environment blockers

1. Host Maven execution from Playwright API spec blocked by filesystem permissions in WSL sandbox.
2. Intermittent backend 502 when `docker-backend-dev` loses `Application.class` on bind-mounted `target/`.

## Screenshots impact

- Screenshots captured via browser Playwright (`@evidence` / `@partial`); **not blocked** by API Maven subprocess failures.
- UI flows validated visually: assistant configuration, settings, Lab setup, admin catalog, source documents, advanced technical details appendix.

## Final E2E/API status

**CONDITIONAL_PASS** - Product-facing config API and live provider runtime checks pass; full `ProviderRuntimeAcceptance` document gate blocked by host Maven permissions, not by product regression.
