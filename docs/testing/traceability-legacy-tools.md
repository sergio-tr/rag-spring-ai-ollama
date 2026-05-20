# Smoke and load tooling (reference)

**Canonical HTTP smoke (operators):** Playwright API tests — `webapp/e2e/api`, `npm run test:api`.  
**Load / stress:** Gatling — `tests/gatling/` (see [`tests/gatling/README.md`](../../tests/gatling/README.md), [`gatling.yml`](../../.github/workflows/gatling.yml)).  
**Stack HTTP integration (depth):** `tests/integration/` (pytest).

## Where to look

| Concern | Location |
| --------- | ---------- |
| Actuator / OpenAPI / readiness | Gatling: `ActuatorHealthSimulation`, `OpenApiAndReadinessSimulation`; Playwright API specs under `webapp/e2e/api/` |
| Ramps, stress, mixed traffic | Gatling mixed profiles and dedicated simulations (`tests/gatling/README.md`) |
| Authenticated product API | Gatling: `ProductAuthenticatedSimulation`; Playwright: `webapp/e2e/api/**/*.spec.ts` |
| Auth negative paths | Gatling: `AuthLoginNegativeSimulation`; Playwright: `webapp/e2e/api/auth/login.api.spec.ts` |
| Admin API | Gatling: `AdminApiSimulation`; Playwright: `webapp/e2e/admin/*.spec.ts` |
| Historical query load (comparison only) | Gatling: `LegacyQueryLoadSimulation`, `LegacyQuerySpikeSimulation`, mixed realistic |
| UI journeys | Playwright: `webapp/e2e/**` (see [`webapp/e2e/README.md`](../../webapp/e2e/README.md)) |
| Observability checks | Optional pytest stack; Gatling health simulations |

**CI:** load workflows use **`gatling.yml`** (`workflow_dispatch` + schedule) with `GATLING_BASE_URL` and optional `vars.GATLING_SIMULATION`.

**Makefile:** `make system-checks` → `cd webapp && API_BASE_URL=... npm run test:api`.

## Related documentation

- [../testing/README.md](../testing/README.md) — testing matrix
- [../performance/README.md](../performance/README.md) — Gatling + Python micro-benchmarks
- [../../webapp/e2e/api/README.md](../../webapp/e2e/api/README.md) — Playwright API smoke
