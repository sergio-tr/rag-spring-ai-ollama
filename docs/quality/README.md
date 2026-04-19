# Quality baseline, test audit, and quality gates (AP01)

This hub records **verified** test execution, **exclusion inventories**, and **normative policies** for mocks, API path usage in tests, SonarCloud baseline, and coverage strategy. It complements [../testing/README.md](../testing/README.md) (what runs where) and [../coverage/README.md](../coverage/README.md) (report paths).

**JaCoCo Coverage Target Ledger** (canonical exclude inventory, policy, Sonar vs JaCoCo parity, and the **Residual final allowlist** in the Wave 6.09 section): [../coverage/jacoco-coverage-target-ledger.md](../coverage/jacoco-coverage-target-ledger.md).

**External test harness** (Ollama, classifier HTTP, OTLP — mocks and recipes): [../testing/external-test-harness.md](../testing/external-test-harness.md).

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
| JaCoCo `<excludes>` in [`rag-service/pom.xml`](../../rag-service/pom.xml) | Coverage bytecode | Many production packages (config, model, large orchestration, **tools**, DTOs, most JPA entities); **`com.uniovi.rag/tool/**` stays excluded** (large surface; would collapse the bundle ratio without a dedicated campaign). **`infrastructure/observability/**`** is excluded (matches Sonar `**/observability/**`; the old `com.uniovi.rag/observability/**` pattern was a no-op). **`ChatMessageApplicationService`** is measured (`ChatMessageApplicationServiceTest` + `AfterCommitTaskScheduler` port with a production `SpringAfterCommitTaskScheduler`). **`ProjectVisualStyleValidator`**: [`ProjectVisualStyleValidatorTest`](../../rag-service/src/test/java/com/uniovi/rag/application/service/account/ProjectVisualStyleValidatorTest.java) (counted in JaCoCo; no longer Sonar-only). **`LabEvaluationRunService`** (under excluded `application/service/evaluation/**`): [`LabEvaluationRunServiceTest`](../../rag-service/src/test/java/com/uniovi/rag/application/service/evaluation/LabEvaluationRunServiceTest.java) for future exclude shrink. **`ToolResult`**: [`ToolResultTest`](../../rag-service/src/test/java/com/uniovi/rag/tool/ToolResultTest.java) (package still JaCoCo-excluded until `tool/**` is narrowed). **Measured when covered** (non-exhaustive): `ConversationApplicationService`, `ProjectDocumentApplicationService`, `application.service.me.*` (unit tests), `AsyncLabTaskRunner`, `api.v5.*Controller`, `ProcessQueryService`, `MessageStreamController`, `LabBenchmarkController`, `MinuteDocumentStructureExpander`, `ConfigProfileController`, `ConfigProfileApplicationService`, `MoveConversationApplicationService`, `PromoteDocumentApplicationService`, `UserAccountPersistenceAdapter`, `AnswerGenerationKernel`, `ChatMessageJobHandler`, `ProjectKnowledgeController`, `ChatStreamChunks`, `ContextPropagatingFutures`, `LegacyCompatibilityValidatorBridge`, `ChatScopedRagConfigResolver`, `LocalBinaryStorageAdapter`, `KnowledgeLegacyBackfillService`, `RuntimeConfigResolutionService`, `MeetingMinutesToolRawResult`, `domain.evaluation` enums / `RagEvaluationLegacy`, nested port records on `BinaryStoragePort` / `EvaluationDatasetStorePort`, `ChatJobCancellationRegistry`, `LocalEvaluationDatasetStorageAdapter`, `AuditApplicationService`, `KnowledgeIndexSnapshotService`, `ProductionSecurityValidator`, `E2eAdminUserSeeder`. **`AuditLogEntity`**: Postgres IT (`AuditLogPersistenceIT`). **`ConversationDraftEntity`**: Postgres IT (`ConversationDraftPersistenceIT`). | Keep bundle ≥ 80%; focus on testable surfaces | Yes | When touching a package, shrink an exclude only with tests that keep the same bundle gate |
| `sonar.coverage.exclusions` / `sonar.exclusions` in [`sonar-project.properties`](../../sonar-project.properties) | Sonar metrics / analysis | Aligns with JaCoCo for rag-service Java coverage intent; adds **`**/domain/runtime/functioncalling/**`**, **`**/domain/runtime/retrieval/**`**, **`**/domain/entity/**`**, **`**/api/v5/dto/**`**, **`**/api/auth/dto/**`** (parity with the POM). **`**/*Configuration.java`** / **`**/*Properties.java`** are **broader** than JaCoCo’s `com.uniovi.rag.configuration/**` (Sonar-only, on purpose). TS/Python noise paths unchanged. | Multi-language Sonar | Yes | When changing JaCoCo `<excludes>`, update `sonar.coverage.exclusions` for the same Java intent; re-run `verify` and a Sonar scan on release candidates |
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

**Default product base:** tests use [`rag-service/src/test/resources/application.properties`](../../rag-service/src/test/resources/application.properties) (`rag.api.product-base-path=/api/v5`). Prefer [`RagApiTestPaths`](../../rag-service/src/test/java/com/uniovi/rag/testsupport/RagApiTestPaths.java) (`path(...)`, `productBasePath()`) for MockMvc URLs so they track that file — already applied across most `interfaces/rest` WebMvc tests.

**Alternate base paths:** slices that intentionally override the product path (e.g. `@TestPropertySource(properties = "rag.api.product-base-path=/api/v1")` or `/api/test`) must keep an explicit `PRODUCT_BASE` (or equivalent) per nested class; do **not** use `RagApiTestPaths` there, because it reads the shared classpath `application.properties`, not the Spring `Environment` of that slice.

**Backlog (incremental):**

1. When adding tests, default to `RagApiTestPaths` unless the slice fixes a non-default base path.
2. Keep CI/env alignment: [`reusable-ci-core.yml`](../../.github/workflows/reusable-ci-core.yml) sets `NEXT_PUBLIC_RAG_API_PREFIX` — keep defaults consistent when refactoring env blocks.

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

### DTOs, bootstrap, and JPA entities

Pure REST **DTO records**, **bootstrap** (`Application`), and **configuration** bindings stay excluded from the JaCoCo bundle where they are thin wiring or data holders; the **80% gate** is meant to stress orchestration and behavior. When removing an entity or DTO exclude, add **meaningful** coverage (for example `@JsonTest` for Jackson edge cases, or a Postgres integration test that round-trips Flyway schema), not mechanical getter-only tests.

### “Zero exclusions” and persistence slices

A **literal 0% JaCoCo excludes** goal is **aspirational** with a **single bundle** line gate at **0.80**: large packages (`tool/**`, full JPA surface, all DTOs) usually need either **many** tests or a **measurement policy change** (per-package thresholds, `includes`, or documented residual excludes). The project’s default for **JPA** is **Postgres-backed `SpringBootTest` ITs** (Flyway schema, `TestcontainersDatasourceConfiguration` / `SafeTestSecretsApplicationContextInitializer`) rather than `@DataJpaTest`; use the latter only when a minimal slice is proven stable with Flyway.

**Parity checklist (when you touch excludes):**

1. Edit [`rag-service/pom.xml`](../../rag-service/pom.xml) JaCoCo `<excludes>` and the matching Java patterns in [`sonar-project.properties`](../../sonar-project.properties) `sonar.coverage.exclusions`.
2. Prefer **tests first**, then **remove** excludes; run `cd rag-service && ./mvnw clean verify`.
3. Spot-check `target/site/jacoco/index.html` for the affected package.

**Master roadmap (progressive exclude reduction)** — execution order: (1) thin adapters / small application services with Mockito + paridad Sonar; (2) `@WebMvcTest` for REST surfaces not yet sliced; (3) heavier `application.service` types (`Conversation*`, `ProjectDocument*`, `me/**`) incrementally; (4) pipelines / async / `tool/**` / retriever with mocks and **minimal** refactors; (5) residual DTO–config–JPA policy in this doc + stable `verify`. Several steps are **already satisfied** in tree (e.g. `ConfigProfile*`, `Move*`, `Promote*`, `UserAccountPersistenceAdapter`, `ProjectKnowledgeControllerWebMvcTest`, `ChatMessageApplicationService` + `AfterCommitTaskScheduler`); large globs (`knowledge/**`, `runtime/**`, `tool/**`) stay excluded until a deliberate vertical campaign.

---

## Related

- Testing overview: [../testing/README.md](../testing/README.md)
- Coverage report locations: [../coverage/README.md](../coverage/README.md)
- Repository hub: [../README.md](../README.md)
