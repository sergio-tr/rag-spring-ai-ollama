# Backend package map (`rag-service`)

**Navigation only** — not a substitute for [rag-service/README.md](../../rag-service/README.md) (build, profiles, smoke tests). One-line roles for `com.uniovi.rag.*`. For methods, use **Javadoc**: `cd rag-service && ./mvnw javadoc:javadoc` → `target/site/apidocs`.

| Package / area | Role |
|----------------|------|
| `application.port.out` | Outbound ports (interfaces) for use cases |
| `application.config` | Runtime configuration resolution: `ConfigResolverService`, `RuntimeConfigResolutionInput`, `CompatibilityValidator`, `ReindexImpactAnalyzer`, `SystemPromptComposer` |
| `application.usecase` | Application services / use cases (e.g. auth) |
| `api.auth` | REST auth controllers, DTOs, exceptions (not application logic) |
| `api.admin` | `ROLE_ADMIN`: allowlist, Ollama pull orchestration |
| `interfaces.rest` | Product REST: projects, documents, conversations, SSE messages, presets, lab jobs, config schema (base path `rag.api.product-base-path`) |
| `controller` | Legacy RAG HTTP surface (`RagController`, Ollama helpers) — prefix from `rag.api.legacy-base-path` |
| `configuration` | Spring Security, CORS, feature flags, path properties (`RagApiPathProperties`), beans wiring |
| `security` | JWT filter, `JwtService`, `RagPrincipal` |
| `bootstrap` | Startup seeders (e.g. e2e admin), safety validators |
| `domain` | Domain enums and types (framework-free top-level package) |
| `domain.config.capability` | `Capability`, `CapabilitySet`, `CapabilityGroup` (activation / presence for resolution) |
| `domain.config.rules` | Declarative `CompatibilityRule` implementations (evaluated by `CompatibilityValidator`) |
| `domain.config.runtime` | `ResolvedRuntimeConfig`, `ResolvedConfigSnapshot`, provenance / profile types for resolution |
| `domain.config.indexing` | `ReindexImpact` / `ReindexImpactLevel` (semantic reindex preview) |
| `domain.config.prompt` | `SystemPromptLayers` and related prompt-layer types (composition in `SystemPromptComposer`) |
| `domain.runtime` | Effective RAG config / feature snapshots used during a query |
| `infrastructure.persistence` | Spring Data JPA repositories, custom persistence adapters |
| `infrastructure.persistence.jpa` | JPA entities and entity factories |
| `infrastructure.classifier` | HTTP clients to **classifier-service** (`ClassifierLabClient`, `QueryClassifier`, etc.) |
| `service.query` | `ProcessQueryService` orchestration |
| `service.query.pipeline` | Preparation, synthesis, tools routing, answer kernel |
| `service.retriever` | Vector / corpus retrieval implementations |
| `service.config` | User/project configuration resolution and sanitization |
| `service.chat` | Chat-oriented orchestration helpers (if present) |
| `service.document` | Ingestion pipeline for project documents |
| `service.evaluation` | Minute evaluation and related services |
| `service.async` | Async tasks (e.g. lab jobs) |
| `service.preset` | RAG preset CRUD |
| `service.project` | Project-level operations |
| `service.model` | Allowed models / Ollama alignment |
| `observability` | Tracing helpers around evaluation |

**Webapp map:** App Router and API client live under `webapp/src/`; run `cd webapp && npm run doc` for TypeDoc.
