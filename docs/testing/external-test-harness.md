# External test harness (rag-service)

Normative guide for **mocking and stubbing** external dependencies in `rag-service` tests so **unit**, **`@WebMvcTest`**, and controlled **`@SpringBootTest`** runs do not require Ollama, a live classifier HTTP service, or OTLP collectors.

**Related:** [Quality hub](../quality/README.md), [Coverage Target Ledger](../coverage/jacoco-coverage-target-ledger.md), [rag-service README](../../rag-service/README.md).

---

## Decision table: mock vs fake vs stub HTTP vs `@TestConfiguration`

| Situation | Prefer |
|-----------|--------|
| Spring bean with stable contract and many dependents | `@MockBean` / `@MockitoBean` on slices, or `@Primary` `@TestConfiguration` stub (see `TestAiStubConfiguration`) |
| HTTP client with real JSON request/response shape | **`MockRestServiceServer`** bound to the same `RestTemplate` the client uses (see `ClassifierClientTestSupport`) |
| Pure logic, no I/O | In-memory fake under `com.uniovi.rag.testsupport` |
| Noisy Micrometer / global meters in partial context | **Properties first** (`application-test.properties`); then `@MockBean MeterRegistry` only in tests that assert tracing/metrics wrappers |
| Risk of hiding configuration regressions | Prefer explicit **test properties** over broad mocks |

**Anti-patterns**

- Stubs that cannot simulate failure (always success) — hide bugs.
- Real `http://localhost:11434` or classifier `localhost:8000` in default test paths.
- Hardcoded product API prefix `/api/v5` in new helpers — use [`RagApiTestPaths`](../../rag-service/src/test/java/com/uniovi/rag/testsupport/RagApiTestPaths.java).

---

## Dependency inventory → excluded packages (future waves)

JaCoCo excludes many packages that **eventually** depend on the stack below. This table links **externals** to **typical consumers** (not exhaustive).

| External | Typical Java touchpoints | Excluded areas (orientative) |
|----------|--------------------------|-------------------------------|
| Ollama / Spring AI | `ChatModel`, `EmbeddingModel`, `ChatClient`, `OllamaApiClient`, `spring.ai.ollama.*` | `tool/**`, `service/analyser/**`, `service/ranker/**`, `service/retriever/**`, `application/service/runtime/**`, … |
| Classifier HTTP | `ClassifierServiceClient`, `rag.classifier.*` | `service/evaluation/**`, `application/service/evaluation/**`, runtime QU adapters |
| OTLP / Micrometer | `management.otlp.*`, `management.tracing.*`, `ObservabilitySupport`, `Tracer` | `infrastructure/observability/**` |
| Async | `@Async`, `TaskExecutor` | Chat jobs, lab workers (often excluded or IT) |
| Postgres | JDBC, JPA, Flyway | Knowledge/document ITs (not excluded as a whole; entities often excluded in JaCoCo) |

---

## 1. Ollama / Spring AI

| Artifact | Role |
|----------|------|
| [`TestAiStubConfiguration`](../../rag-service/src/test/java/com/uniovi/rag/testsupport/TestAiStubConfiguration.java) | `@TestConfiguration` `@Profile("test")`: `@Primary` `ChatModel`, `EmbeddingModel`, `OllamaApiClient.noHttpStub` — **no** outbound Ollama |
| [`ChatClientTestSupport`](../../rag-service/src/test/java/com/uniovi/rag/testsupport/ChatClientTestSupport.java) | Mockito deep stubs for `ChatClient` fluent API in **unit** tests |
| `OllamaConnectivityChecker` | **Mock** in service/WebMvc tests that inject it — no live ping |

**When to use what**

- **`@SpringBootTest` + profile `test`:** `@Import(TestAiStubConfiguration.class)` (pattern used by Postgres ITs).
- **Pure unit tests** (no context): `ChatClientTestSupport.mockForUserPromptChain()` + `stubUserPromptReturns` / `stubSystemUserPromptReturns` / `stubUserPromptThrows`.

**`ChatClient` bean:** Full context usually builds `ChatClient` from auto-configuration + stubbed `ChatModel`; if a slice fails for missing `ChatClient`, add a `@MockBean` or import minimal AI config — do not point to a real Ollama.

---

## 2. Classifier (HTTP)

| Property (main) | Purpose |
|-----------------|--------|
| `rag.classifier.service.url` | Base URL (empty ⇒ client no-op) |
| `rag.classifier.model-id` | Default model tag |
| `rag.classifier.service.timeout-ms` | HTTP timeouts |

**Test profile:** [`application-test.properties`](../../rag-service/src/test/resources/application-test.properties) sets **`rag.classifier.service.url=`** (empty) so full-context tests do not open TCP to a default localhost classifier unless a test overrides the property.

| Artifact | Role |
|----------|------|
| [`ClassifierClientTestSupport`](../../rag-service/src/test/java/com/uniovi/rag/testsupport/ClassifierClientTestSupport.java) | `MockRestServiceServer` + `RestTemplate` + `ClassifierServiceClient` fixture; `defaultBaseUrl()` aligns `requestTo(...)` expectations |
| [`ClassifierServiceClientTest`](../../rag-service/src/test/java/com/uniovi/rag/infrastructure/classifier/ClassifierServiceClientTest.java) | Reference tests: 200/5xx/invalid body/timeout |

**Recipe**

1. `ClassifierMockRestFixture f = ClassifierClientTestSupport.newDefaultFixture();`
2. `f.server().expect(requestTo(ClassifierClientTestSupport.defaultBaseUrl() + "/classify")).andRespond(...);`
3. Call `f.client().classify(...)`; `f.server().verify();`
4. Use **`newFixture(customBase, modelId, timeout)`** if expectations must use another base URL (keep URL and `requestTo` in sync).

**Negative paths:** use `withServerError()`, `withStatus(BAD_REQUEST)`, or inject a Mockito `RestTemplate` that throws `RestClientException` (see existing test).

---

## 3. OpenTelemetry / OTLP / Micrometer

**Normative file:** [`application-test.properties`](../../rag-service/src/test/resources/application-test.properties)

- `management.otlp.metrics.export.enabled=false`
- `management.otlp.tracing.export.enabled=false`
- `management.metrics.export.otlp.enabled=false`
- `management.tracing.enabled=false`
- Harmless localhost endpoints for `management.otlp.tracing.endpoint` / `metrics.export.url` when the environment supplies empty OTLP vars (see comments in that file).

**OTLP checklist (Wave 6.02 audit)**

- [x] Profile `test` disables OTLP push and tracing as above.
- [x] Root [`application.properties`](../../rag-service/src/main/resources/application.properties) may still define OTLP keys; test profile overrides take precedence for `@ActiveProfiles("test")`.
- [ ] Add a dedicated `@TestConfiguration` “NoOpOtlp” **only** if a future change shows export threads or SDK noise **despite** these properties (document evidence in a PR).

Slices that partially start the context should set the same flags via `@TestPropertySource` if they inherit problematic actuator defaults.

---

## 4. Async / executors

- Prefer invoking `@Async` methods **directly** from tests when the goal is **logic** coverage (same thread).
- For pipeline tests that need determinism: document use of a **sync** `TaskExecutor` `@Bean` in a test-only `@TestConfiguration` (do not change production executors in harness waves without a feature reason).
- Avoid `Thread.sleep` for coordination; prefer bounded waits already used in the module or refactor to synchronous seams.

---

## 5. JDBC / Postgres (integration)

Not replaced by this harness; **reference** only:

- [`SafeTestSecretsApplicationContextInitializer`](../../rag-service/src/test/java/com/uniovi/rag/testsupport/SafeTestSecretsApplicationContextInitializer.java)
- [`TestcontainersDatasourceConfiguration`](../../rag-service/src/test/java/com/uniovi/rag/testsupport/TestcontainersDatasourceConfiguration.java)
- `@EnabledIf(com.uniovi.rag.testsupport.TestEnvironment#…)` for Docker/CI matrix

Use **`@SpringBootTest` + Flyway** for schema-realistic ITs; prefer **`@WebMvcTest`** with [`RagWebMvcTestApplication`](../../rag-service/src/test/java/com/uniovi/rag/testsupport/webmvc/RagWebMvcTestApplication.java) when testing controllers without DB (excludes DataSource, Flyway, Security auto-config — **does not** add Ollama).

---

## 6. API paths in tests

Use [`RagApiTestPaths`](../../rag-service/src/test/java/com/uniovi/rag/testsupport/RagApiTestPaths.java) and `src/test/resources/application.properties` `rag.api.product-base-path`. Slices with **`@TestPropertySource`** overriding the base path must use a **local** `PRODUCT_BASE` constant — not `RagApiTestPaths` (it reads classpath `application.properties`, not the slice `Environment`).

---

## Verification (Wave 6.02)

- From `rag-service/`: `./mvnw test` exits **0**.
- No new default test should open real Ollama or classifier ports.
- JaCoCo `<excludes>` are **unchanged** in 6.02 (harness-only).

---

## Related links

- [Testing overview](README.md)
- [Sonar local analysis](../development/sonar-local-analysis.md)
