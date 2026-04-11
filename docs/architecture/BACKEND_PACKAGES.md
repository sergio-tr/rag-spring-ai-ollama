# Backend package map (`rag-service`)

**Navigation only** — not a substitute for [rag-service/README.md](../../rag-service/README.md) (build, profiles, smoke tests). One-line roles for `com.uniovi.rag.*`. For methods, use **Javadoc**: `cd rag-service && ./mvnw javadoc:javadoc` → `target/site/apidocs`.

| Package / area | Role |
|----------------|------|
| `application.port` | Hexagonal ports: `ConfigurationSourcePort` (layer loads + raw preset/profile sources), `ConversationRuntimeOverrideLoader`, `RagConfigurationResolver` |
| `application.port.out` | Outbound ports (interfaces) for use cases |
| `application.config` | Runtime configuration resolution: `ConfigResolverService`, `RuntimeConfigResolutionInput`, `CompatibilityValidator`, `ReindexImpactAnalyzer`, `SystemPromptComposer` |
| `application.service` | Use cases including `ResolvedConfigSnapshotApplicationService` (persist resolved snapshots after `ConfigResolverService.resolve`) |
| `application.service.knowledge` | Knowledge System: `KnowledgeConfigurationIntegrationService` (config-aware preview/execute orchestration), `KnowledgeBuildProjectionMapper` (sole builder of `KnowledgeBuildProjection`), `KnowledgePipelineOrchestrator` (sole corpus `document_artifact` + `vector_store` writes; config-aware rebuild takes `KnowledgeBuildProjection`), `KnowledgeIndexingService`, `KnowledgeIngestionService` (persists default `resolved_config_snapshot` per ingest for linkage), `KnowledgeSnapshotService`, `ReindexService` (executes precomputed `KnowledgeReindexDecision` only), `ProjectKnowledgeApplicationService` (REST façade → integration service; no direct resolver/orchestrator) |
| `application.usecase` | Application services / use cases (e.g. auth) |
| `api.auth` | REST auth controllers, DTOs, exceptions (not application logic) |
| `api.admin` | `ROLE_ADMIN`: allowlist, Ollama pull orchestration |
| `interfaces.rest` | Product REST: projects, documents, conversations, knowledge (`ProjectKnowledgeController`: ingest, rebuild preview/execute, legacy reindex alias, snapshots), SSE messages, presets, lab jobs, config schema (base path `rag.api.product-base-path`) |
| `interfaces.rest.dto.knowledge` | Snapshot responses; `KnowledgeIngestRequest` / `KnowledgeReindexRequest` (query-parameter shapes for knowledge routes) |
| `controller` | Legacy RAG HTTP surface (`RagController`, Ollama helpers) — prefix from `rag.api.legacy-base-path` |
| `configuration` | Spring Security, CORS, feature flags, path properties (`RagApiPathProperties`), beans wiring |
| `security` | JWT filter, `JwtService`, `RagPrincipal` |
| `bootstrap` | Startup seeders (e.g. e2e admin), safety validators |
| `domain` | Domain enums and types (framework-free top-level package) |
| `domain.knowledge` | Knowledge System domain: `WorkspaceDocument`, `MaterializationStrategy`, artifact types, snapshot scope, snapshot/reindex hashes (no JPA in this package) |
| `domain.config.capability` | `Capability`, `CapabilitySet`, `CapabilityGroup` (activation / presence for resolution) |
| `domain.config.rules` | Declarative `CompatibilityRule` implementations (evaluated by `CompatibilityValidator`) |
| `domain.config.runtime` | `ResolvedRuntimeConfig`, `ResolvedConfigSnapshot`, provenance / profile types for resolution |
| `domain.config` (merge helpers) | `RagConfigurationMerge`, `PresetProfilePayloadMerge` (pure JSON/map merge; **invoked only from** `ConfigResolver` in production) |
| `domain.config.indexing` | `ReindexImpact` / `ReindexImpactLevel` (semantic reindex preview) |
| `domain.config.prompt` | `SystemPromptLayers` and related prompt-layer types (composition in `SystemPromptComposer`) |
| `domain.runtime` | Effective RAG config / feature snapshots used during a query |
| `infrastructure.persistence` | Spring Data JPA repositories, `ConversationRuntimeOverrideLoaderImpl`, custom persistence adapters |
| `infrastructure.persistence.jpa` | JPA entities and entity factories |
| `infrastructure.persistence.mapper` | `ResolvedConfigSnapshotEntityMapper` (sole read/write shape for `resolved_config_snapshot` JSON columns); `KnowledgeIndexSnapshotMapper`, `ReindexEventMapper` (knowledge domain ↔ JPA) |
| `infrastructure.classifier` | HTTP clients to **classifier-service** (`ClassifierLabClient`, `QueryClassifier`, etc.) |
| `service.query` | `ProcessQueryService` / `SimpleProcessQueryService` façade → `application.service.runtime` (`ExecutionContextFactory`, `RagExecutionOrchestrator`, workflows) |
| `application.service.runtime` | Runtime engine: orchestrator, workflow selector, full-corpus assembly |
| `application.service.runtime.advisor` | P10 Advisor Core: `AdvisorPolicyResolver`, `AdvisorStrategy`, `RetrievalAdvisor`, `ContextPackingAdvisor` (orchestrated only from `RagExecutionOrchestrator`) |
| `application.service.runtime.retrieval` | Advanced retrieval: `AdvancedRetrievalPipeline` (single entrypoint), dense/sparse/hybrid strategies, RRF fusion, deterministic rerank/filter/compression, metadata appendix loader |
| `application.service.runtime.query` | Runtime query understanding: `QueryUnderstandingPipeline` and adapters/resolvers producing `QueryPlan` |
| `application.service.runtime.clarification` | **P11:** `ClarificationStateResolver`, `ClarifiedQueryRefiner`, `ClarificationPolicyResolver`, `ClarificationQuestionGenerator`, `ClarificationStrategy` (sole mutator caller for `PendingClarificationStore`); deterministic policy + templates; runs after QU, before `WorkflowSelector` |
| `application.service.runtime.memory` | **P12:** runtime-owned conversational memory stage: history loader, fixed slice selector, LLM-backed single-call condensation with deterministic fallback; runs after P11 clarification pre-processing and before QU |
| `application.service.runtime.routing` | **P13:** adaptive routing stage: deterministic route-family selection (`AdaptiveRoutingPolicyResolver`) + single entrypoint (`AdaptiveRoutingStrategy`) + capability evaluator + gate builder; runs after clarification policy and before any downstream execution family |
| `application.service.runtime.judge` | **P14:** post-answer judge stage: bounded evaluation + at most one bounded repair attempt; orchestrated only from `RagExecutionOrchestrator` after the selected route family produces a candidate answer |
| `application.service.runtime.tracepersistence` | **P15:** runtime trace persistence (best-effort). Writes one canonical `runtime_execution_trace` row per completed orchestrated turn by consuming the finalized `ExecutionTrace` (no re-derivation). Invoked at the runtime boundary (facade), not from controllers or workflows. |
| `application.service.runtime.tracequery` | **P16:** runtime trace query surfaces (read-only). `RuntimeTraceQueryService` is the single owner for reading the canonical persisted trace artifact (`runtime_execution_trace`) and applying owner-scoped authorization + paging/filter rules. REST controllers must not query repositories directly. |
| `application.service.runtime.traceexport` | **P17:** runtime trace export core (read-only). `RuntimeTraceExportService` is the single owner for generating bounded, deterministic ZIP exports of persisted runtime traces by consuming P16 query surfaces only (no repository bypass, no DB writes, no replay/analytics). |
| `application.service.runtime.tracereplay` | **P18:** internal-only bounded replay core. `RuntimeTraceReplayService` is the single owner; helpers `RuntimeTraceReplayEligibilityResolver`, `RuntimeTraceReplayInputLoader`, `RuntimeTraceReplayStrategy`. Reads traces only via `RuntimeTraceQueryService`; no `runtime_execution_trace` writes; no REST in P18. Domain types live under `domain.runtime.tracereplay`. |
| `application.service.runtime.tracecomparison` | **P19:** internal-only replay comparison core. `RuntimeTraceReplayComparisonService` is the single owner; `RuntimeTraceReplayComparator` performs bounded dimensional diff after `REPLAY_SUCCEEDED`. Reads originals via `RuntimeTraceQueryService` and replay via `RuntimeTraceReplayService` only; no persistence, no export inputs, **no REST endpoints in P19**. Domain types live under `domain.runtime.tracecomparison`. |
| `application.service.runtime.tool` | Deterministic tools: `DeterministicToolStrategy` (sole entrypoint), resolver, executor, result mapper, `MeetingMinutesToolExecutionCore` (shared tool business execution) — invoked only from `RagExecutionOrchestrator` for P7 |
| `application.service.runtime.functioncalling` | P9 function calling: `FunctionCallingStrategy`, `FunctionCallingPolicyResolver`, `FunctionCallingToolRegistry`, `FunctionCallingExecutor`, `FunctionCallingResultMapper` — FC execution only from `RagExecutionOrchestrator` |
| `domain.runtime.engine` | `ExecutionContext`, `RagExecutionResult`, `ExecutionTrace`, snapshot selection records |
| `domain.runtime.advisor` | P10 advisor domain: `AdvisorKind`, `AdvisorDecision`, `AdvisorExecutionResult`, `PackedContextSet`, `PackedContextBlock`, `AdvisorOutcome`, suppression reasons |
| `domain.runtime.retrieval` | Advanced retrieval domain: `RetrievalRequest`, `RetrievalCandidate`, `CuratedContextSet`, `RetrievalDiagnostics`, `RetrievalMode`, fusion/rerank/compression outcomes |
| `domain.runtime.query` | Query understanding domain: `QueryPlan`, normalization/entities/rewrite results, intent/shape/ambiguity enums |
| `domain.runtime.clarification` | **P11:** `ClarificationOutcome`, `ClarificationQuestionKind`, `PendingClarificationState`, `ClarificationQuestion`, `ClarificationDecision`, `ClarificationExecutionResult` |
| `domain.runtime.memory` | **P12:** memory domain: mode/outcome/decision, history turn/slice, memory execution result and trace fragments |
| `domain.runtime.routing` | **P13:** routing domain: `AdaptiveRouteKind`, `AdaptiveRoutingOutcome`, `AdaptiveRoutingDecision`, `RouteExecutionGate`, `AdaptiveRoutingExecutionResult` |
| `domain.runtime.judge` | **P14:** judge domain: `JudgeCandidateSource`, `JudgeDecision`, `JudgeEvaluation`, `JudgeExecutionResult`, `JudgeOutcome` |
| `domain.runtime.tool` | Deterministic tool domain: `DeterministicToolKind`, `DeterministicToolDecision`, `DeterministicToolExecutionResult`, outcomes |
| `domain.runtime.functioncalling` | P9 FC domain: `FunctionCallingMode`, `FunctionCallingOutcome`, `FunctionCallingDecision`, `FunctionCallingExecutionResult` |
| `service.query.pipeline` | Preparation, synthesis, answer kernel (legacy; deterministic tools are not routed here) |
| `service.retriever` | Vector / corpus retrieval implementations |
| `service.config` | `ConfigResolver` (cascade merge owner), user/project configuration, sanitization; `JpaConfigurationSourceAdapter` implements `ConfigurationSourcePort` |
| `service.chat` | Chat-oriented orchestration helpers (if present) |
| `service.document` | Ingestion pipeline for project documents |
| `service.evaluation` | Minute evaluation and related services |
| `service.async` | Async tasks (e.g. lab jobs) |
| `service.preset` | RAG preset CRUD |
| `service.project` | Project-level operations |
| `service.model` | Allowed models / Ollama alignment |
| `observability` | Tracing helpers around evaluation |

**Webapp map:** App Router and API client live under `webapp/src/`; run `cd webapp && npm run doc` for TypeDoc.
