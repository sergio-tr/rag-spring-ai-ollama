# External dependency policy (tests)

Canonical **recipes and class names** live in [../testing/external-test-harness.md](../testing/external-test-harness.md). This page **freezes** the normative rules so reviews have a single checklist.

## Ollama / Spring AI

- **Unit / slice tests:** use **`TestAiStubConfiguration`** (or project test support) so **no** live `http://localhost:11434` dependency is required for assertions.
- **Production properties** default to a localhost Ollama URL; the **`test`** profile uses [`application-test.properties`](../../rag-service/src/test/resources/application-test.properties) and documents alignment with stubs.
- **Reject** new tests that require a **real** Ollama unless they are explicitly marked as **manual / E2E** and excluded from default `verify`.

Relevant keys in `application-test.properties`:

- `spring.ai.ollama.base-url` (present for binders; **stub** covers behavior)
- `rag.ollama.auto-pull-enabled=false`

## Classifier HTTP

- **Default:** `rag.classifier.service.url=` (**empty**) in `application-test.properties` → client is a **no-op** unless the test overrides the URL and uses **MockRestServiceServer** (or equivalent) — see harness doc.
- **Reject** accidental coupling to **`http://localhost:8000`** for unit tests unless the test **starts** a mock server or WireMock fixture.

Also set:

- `rag.health.classifier-enabled=false` (health noise)

## OTEL / Micrometer / OTLP

For default `test` runs, **disable push** and avoid real collectors:

- `management.otlp.metrics.export.enabled=false`
- `management.otlp.tracing.export.enabled=false`
- `management.metrics.export.otlp.enabled=false`
- `management.tracing.enabled=false`
- Harmless endpoints pinned when export is off (see file comments).

**Observability assertions:** use **`ObservabilitySupport`** / in-process registries only in tests that **explicitly** assert metrics/traces; do not add a **global** `MockBean Tracer` that hides wiring regressions without a narrow comment.

## Summary

| Dependency | Normative approach |
| ------------ | -------------------- |
| Ollama | Stubs / test configuration; **no** real network in default `verify` |
| Classifier | Empty base URL + mock HTTP when exercising the client |
| OTLP | Properties-first disable; surgical test doubles only when asserting telemetry |
