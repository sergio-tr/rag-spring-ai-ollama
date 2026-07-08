# API path policy (`/api/v5` and versioned routes)

Normative decisions are **FD-api-path-tests** in [README.md](README.md) (hub). This page records a **snapshot inventory** and a **batch elimination** plan without a big-bang refactor.

## Single source of truth

| Concern | Source |
| --------- | -------- |
| Default product base path (runtime + most tests) | [`rag-service/src/main/resources/application.properties`](../../rag-service/src/main/resources/application.properties) (`rag.api.product-base-path`; default **`/api/v5`**) |
| Test classpath default | [`rag-service/src/test/resources/application.properties`](../../rag-service/src/test/resources/application.properties) |
| Type-safe URL builder for MockMvc | [`RagApiTestPaths`](../../rag-service/src/test/java/com/uniovi/rag/testsupport/RagApiTestPaths.java) (`path(...)`, `productBasePath()`) |

**Rule:** do not add new scattered string literals **`/api/vN`** in tests or helpers; use **`RagApiTestPaths`** and/or `@TestPropertySource` / `@DynamicPropertySource` with an explicit **`PRODUCT_BASE`** when the slice intentionally uses a **non-default** base (e.g. `/api/v1`, `/api/test`).

## Inventory snapshot (commit `ebd3453`)

Counts are **`rg '/api/v5'`** occurrence counts per file (not unique test cases). Purpose: show where defaults and docs still mention the literal.

| Area | Representative files | Role |
| ------ | ------------------------ | ------ |
| **Backend config** | `RagApiPathProperties.java`, `application.properties` | Default constant and binder - **expected** |
| **Backend tests** | `RagApiPathAndAccountAndRuntimePropertiesTest.java`, `RagApiTestPaths.java` | Assertions and fallback for property load - **expected** |
| **Webapp client** | `webapp/src/lib/api-client.ts` | Default prefix when env unset - **expected** |
| **Webapp tests** | `lab-job-follow.test.ts`, `lab-job-sse.test.ts` | Prefer deriving from shared test constant aligned with `NEXT_PUBLIC_RAG_API_PREFIX` when touching these files |
| **E2E / Playwright** | `webapp/e2e/support/helpers.ts`, `webapp/e2e/api/fixtures/env.ts` | Env-driven; default `/api/v5` for local stack |
| **CI / Compose** | `reusable-ci-core.yml`, `docker-compose.yml`, `e2e-fullstack.yml`, `e2e.yml` | Build-time / runtime defaults for stacks |
| **Stack integration** | `tests/integration/conftest.py`, `test_stack_integration.py` | `INTEGRATION_RAG_PRODUCT_BASE_PATH` override |
| **Performance / Gatling** | `tests/performance/**`, `tests/gatling/.../RagPaths.scala` | Scenario defaults |
| **Docs** | `docs/**`, `rag-service/README.md` | Documentation only |

**rag-service `src/test` Java literals:** confined to **properties normalization tests** and **`RagApiTestPaths`** fallback; MockMvc suites should use **`RagApiTestPaths`** (already standard for `interfaces/rest` WebMvc tests).

## Batch plan (incremental)

1. **New tests:** mandatory **`RagApiTestPaths`** + property-driven base; **no** new literals.
2. **Webapp unit tests:** when editing a file, replace hard-coded paths with a single **`DEFAULT_PREFIX`** constant imported from a small test helper or `process.env` mock consistent with [`api-client.ts`](../../webapp/src/lib/api-client.ts).
3. **Stack integration / perf YAML:** keep env-aware defaults; document overrides in [../testing/baseline-runbook.md](../testing/baseline-runbook.md) if commands change.
4. **Historical routing exceptions:** if a test **must** assert cross-version routing, name the class/method (e.g. `...HistoricalPrefixCompatibilityTest`) and link an issue in the class-level Javadoc.

Deep history: [../coverage/jacoco-coverage-target-ledger.md](../coverage/jacoco-coverage-target-ledger.md) (Wave 6.08 inventory command).
