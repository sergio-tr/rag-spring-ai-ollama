# Tests (`tests/`)

Automation and verification assets for the monorepo. Each major area has its own README with commands and environment variables.

| Folder | Role |
|--------|------|
| [e2e/](e2e/) | Compose/shell **technical** smoke (not the Playwright browser suite; that lives under `webapp/e2e/`). |
| [gatling/](gatling/) | JVM **load and stress** (Gatling Gradle module). |
| [integration/](integration/) | **Stack HTTP integration** (pytest + httpx) against a running backend. |
| [performance/](performance/) | Python **micro-benchmarks**, scenarios, and schema. |

**Root script:** [full-stack-verify.sh](full-stack-verify.sh) — optional full-pipeline verification (RAG `mvnw verify`, classifier `pytest`, **webapp** `npm` lint / typecheck / Vitest coverage / `build` / `typedoc`, Docker Compose stack **including the webapp image**, pytest `tests/integration`, Playwright **API** `webapp` `npm run test:api`). Env: `SKIP_DOCKER`, `SKIP_INTEGRATION`, `SKIP_WEBAPP`, `SKIP_WEBAPP_PLAYWRIGHT`, `MAVEN_ON_HOST`, `API_BASE_URL`, `INTEGRATION_LOGIN_*`, `CLASSIFIER_URL` (see script header).

**Canonical testing documentation:** [../docs/testing/README.md](../docs/testing/README.md).
