# Quality baseline, test audit, and quality gates (AP01)

This hub records **verified** test execution, **exclusion inventories**, and **normative policies** for mocks, API path usage in tests, SonarCloud baseline, and coverage strategy. It complements [../testing/README.md](../testing/README.md) (what runs where) and [../coverage/README.md](../coverage/README.md) (report paths).

**Governance:** [../development/documentation-guidelines.md](../development/documentation-guidelines.md).

---

## Baseline execution record

Update this table when you re-run the canonical commands on a new release candidate or after major tool changes.

| Commit (short) | Full SHA | Date (UTC) | rag-service `./mvnw clean verify` | classifier `pytest tests/` | webapp `lint` / `typecheck` / `test:coverage` / `build` |
|----------------|----------|--------------|-------------------------------------|----------------------------|--------------------------------------------------------|
| `47e6d0d647` | `47e6d0d647db58e39e3c23157ff27003fb3fb572` | 2026-04-12 | **PASS** (local) | **Not executed** (Python `pip`/`pytest` not available in baseline environment) | **PASS** after mechanical `undefined` guard in [`webapp/src/proxy.ts`](../../webapp/src/proxy.ts) (`localeFromPath`) |

**Canonical commands (normative):**

1. **Backend:** `cd rag-service && ./mvnw clean verify` — JaCoCo `check` on bundle (line coverage ≥ 80% per [`pom.xml`](../../rag-service/pom.xml)).
2. **Classifier:** `cd classifier-service && pytest tests/ -v` (coverage via [`pytest.ini`](../../classifier-service/pytest.ini) / [`.coveragerc`](../../classifier-service/.coveragerc), `fail_under = 80`).
3. **Webapp:** `cd webapp && npm ci && npm run lint && npm run typecheck && npm run test:coverage && npm run build`.

**Operational equivalence:** from a Maven reactor that lists `rag-service`, `mvn test -pl rag-service` matches the test phase; `verify` includes JaCoCo report and `jacoco:check`.

**Prerequisites (classifier):** Python 3.11+, dependencies from `classifier-service/requirements.txt`, same as [`.github/workflows/reusable-ci-core.yml`](../../.github/workflows/reusable-ci-core.yml) `core_classifier` job.

---

## Exclusion and filtering matrix

Each row is a mechanism that changes what is **measured**, **analyzed**, or **run** — not all are “skipped tests”.

| Mechanism | Scope | What it excludes or filters | Apparent reason | Still valid? | Recommended action |
|-----------|--------|------------------------------|-----------------|--------------|-------------------|
| JaCoCo `<excludes>` in [`rag-service/pom.xml`](../../rag-service/pom.xml) | Coverage bytecode | Many production packages (config, model, large orchestration, tools, DTOs, entities, selected controllers) | Keep bundle ≥ 80%; focus on testable surfaces | Yes, until slice adds targeted tests | When touching a package, consider shrinking the exclude only with tests that restore the same gate |
| `sonar.coverage.exclusions` / `sonar.exclusions` in [`sonar-project.properties`](../../sonar-project.properties) | Sonar metrics / analysis | Aligns with JaCoCo + extra paths (e.g. TS/Python noise) | Multi-language Sonar | Yes | Keep in sync when JaCoCo excludes change |
| Vitest `coverage.exclude` in [`webapp/vitest.config.ts`](../../webapp/vitest.config.ts) | Frontend coverage gate | App Router pages/layouts, some UI shells, etc. | E2E / manual | Review per file | Document in module README when changing |
| Playwright `test:e2e` vs `test:e2e:fullstack` | CI jobs | Smoke excludes `@fullstack` | Time / infra | Yes | Treat as **CI filter**, not Maven exclusion |
| pytest markers (`unit`, `integration`, `slow`, …) in [`classifier-service/pytest.ini`](../../classifier-service/pytest.ini) | Discovery | Classification | Selective runs | Yes | **CI alignment:** `core_classifier` runs **full** `pytest tests/ -v`; **`sonar` job** runs a **subset** (`tests/unit`, one regression file, `-m "not integration and not slow"`). Treat as **intentional split** (fast Sonar path vs full PR gate) — if they diverge in failure, investigate before merge |
| Surefire `<excludes>` for test classes | Java test run | *(none in current `pom.xml`)* | — | — | If added later, add a row here |

---

## Normative policies (FD-ap01-*)

| ID | Policy |
|----|--------|
| **FD-ap01-single-gate-command** | Backend gate: `cd rag-service && ./mvnw clean verify`. |
| **FD-ap01-api-path-tests** | In Spring tests, build paths from **`rag.api.product-base-path`** (`@TestPropertySource` / `@DynamicPropertySource`) and a **single constant per test class** (e.g. `PRODUCT_BASE`). Do not scatter literals `/api/vN` except tests whose **only** purpose is cross-prefix compatibility (document in test name or comment). |
| **FD-ap01-ollama-mock** | Prefer **mock `OllamaConnectivityChecker`** and stubbed `ChatModel`/`ChatClient` in unit/WebMvc tests; unit tests should not require a live Ollama. |
| **FD-ap01-otel-test-env** | For full-context or metrics export: disable OTLP export or use inert endpoints (e.g. `MANAGEMENT_OTLP_METRICS_EXPORT_ENABLED=false` as in [../development/sonar-local-analysis.md](../development/sonar-local-analysis.md)). |
| **FD-ap01-sonar-baseline-source** | Baseline must reference project key `sergio-tr_rag-spring-ai-ollama` from [`sonar-project.properties`](../../sonar-project.properties) and [SonarCloud quality gates](https://docs.sonarsource.com/sonarqube-cloud/managing-your-projects/defining-quality-gates/). |
| **FD-ap01-coverage-dual** | **Global:** maintain ≥ 80% where already enforced (JaCoCo bundle, Vitest thresholds, classifier `fail_under`). **New code:** Sonar **New Code** quality gate is the primary merge contract; record dashboard numbers below. |

---

## API path literals in tests (inventory summary)

**Finding:** Many MockMvc tests under `rag-service/src/test/.../interfaces/rest` still use hardcoded **`/api/v5`** (or `/api/v1`) in `perform(get/post(...))`. Newer contract tests use `@TestPropertySource(properties = "rag.api.product-base-path=/api/v1")` and a `PRODUCT_BASE` constant — that is the **target pattern**.

**Representative files with `/api/v5` or mixed versions** (non-exhaustive; re-run search when planning refactors):

- `RuntimeTraceRegressionSuiteDefinitionControllerTest`, `RuntimeTraceRegressionSuite*ControllerTest`, `RuntimeTraceReplay*ControllerTest`, legacy `RagControllerTest`, and related WebMvc tests.

**Backlog (incremental, no big bang):**

1. When touching a test class for other reasons, migrate literals to `PRODUCT_BASE` + property.
2. Keep CI/env alignment: [`reusable-ci-core.yml`](../../.github/workflows/reusable-ci-core.yml) sets `NEXT_PUBLIC_RAG_API_PREFIX` — document the same default in one place when refactoring env blocks.

---

## Ollama and OTLP in tests

**Ollama**

- Production URL: `spring.ai.ollama.base-url` ([`application.properties`](../../rag-service/src/main/resources/application.properties)).
- **Normative test pattern:** mock [`OllamaConnectivityChecker`](../../rag-service/src/main/java/com/uniovi/rag/interfaces/rest/support/OllamaConnectivityChecker.java) and avoid live HTTP in unit tests (see e.g. [`SimpleProcessQueryServiceTest`](../../rag-service/src/test/java/com/uniovi/rag/service/query/SimpleProcessQueryServiceTest.java), [`RagEvaluationConfigurationTest`](../../rag-service/src/test/java/com/uniovi/rag/configuration/RagEvaluationConfigurationTest.java)).

**OTLP / metrics**

- OTLP endpoints default in `application.properties` (`management.otlp.*`).
- For local Sonar pipeline parity, use variables documented in [../development/sonar-local-analysis.md](../development/sonar-local-analysis.md) (e.g. disable metrics export during analysis when no collector is present).

---

## SonarCloud baseline

| Field | Value |
|-------|--------|
| **Project key** | `sergio-tr_rag-spring-ai-ollama` ([`sonar-project.properties`](../../sonar-project.properties)) |
| **Organization** | `sergio-tr` |
| **Dashboard** | [SonarCloud project summary](https://sonarcloud.io/summary/new_code?id=sergio-tr_rag-spring-ai-ollama) |
| **Quality gate** | As configured in SonarCloud for the organization (see official docs above). **Record** numeric baseline (coverage, bugs, vulnerabilities, security hotspots) from the dashboard when closing a release; update this table periodically. |

**CI workflow:** [`sonar.yml`](../../.github/workflows/sonar.yml); local parity: [../development/sonar-local-analysis.md](../development/sonar-local-analysis.md).

**Fork PR note:** `SONAR_TOKEN` may be missing on forks — the reusable pipeline fails fast if absent; document team policy for required checks.

---

## Coverage strategy: global vs new code

| Layer | Global target | New / changed code |
|-------|----------------|-------------------|
| **Java (rag-service)** | JaCoCo bundle line ≥ **80%** after configured excludes (`verify`) | Sonar **New Code** metrics; do not drop gate without team decision |
| **Webapp** | Vitest thresholds **80%** lines/statements/functions/branches on included globs | Same; PR decoration in Sonar for TS |
| **Classifier** | `fail_under = 80` in [`.coveragerc`](../../classifier-service/.coveragerc) | Align Sonar subset with full pytest expectations |

**Expensive or low-value tests**

- **Tag or mark** slow/integration tests (classifier already uses markers).
- **Do not** delete tests solely to raise coverage; prefer **narrower scope** or **faster fixtures**.
- **Replace** brittle timing with deterministic waits where the stack already supports it.

---

## Related

- Testing overview: [../testing/README.md](../testing/README.md)
- Coverage report locations: [../coverage/README.md](../coverage/README.md)
- Repository hub: [../README.md](../README.md)
