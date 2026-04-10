# RAG runtime architecture

**Purpose:** Canonical **target** vocabulary for the **RAG Runtime Engine**. Each identifier below is **normative** for future code and documentation. Behaviour remains defined by code until aligned. See [ADR 0005](../adr/0005-target-rag-architecture-and-runtime-center.md).

**Related:** [target-architecture.md](target-architecture.md), [configuration-resolution-model.md](configuration-resolution-model.md), [knowledge-system-model.md](knowledge-system-model.md), [integration-flows.md](integration-flows.md).

## Orchestration and context

| Component | Responsibility | Conceptual inputs | Conceptual outputs / effects |
|-----------|----------------|-------------------|------------------------------|
| `ExecutionContext` | Aggregates per-request identity, workspace scope, **resolved runtime configuration**, active policies, and hooks for tracing. | Auth principal, project id, resolved config snapshot | Passed through pipelines and strategies |
| `PromptStack` | Ordered logical layers of messages/prompts feeding the LLM; aligns with `SystemPromptComposer` and ADR 0008. | Composed prompt layers, history slices | Normalized stack for model call |
| `RagExecutionOrchestrator` | Top-level coordinator for one RAG execution (product or lab). | `ExecutionContext`, user query | Final answer stream or structured result + `ExecutionTrace` |
| `ExecutionWorkflow` | Declared graph or sequence of steps for one execution kind (preset/route). | Selected workflow id, context | Ordered stages (understanding → retrieval → …) |
| `WorkflowSelector` | Chooses `ExecutionWorkflow` from **resolved config** using a deterministic matrix. | `ResolvedRuntimeConfig` | Workflow binding |
| `ExecutionTrace` | Structured record of decisions, stages, and timings for observability and lab reproducibility. | Events from orchestrator and pipelines | Trace export / logs / metrics |

## Pipelines

| Component | Responsibility | Typical placement in workflow |
|-----------|----------------|------------------------------|
| `QueryUnderstandingPipeline` | Builds a deterministic, structured `QueryPlan` (normalization → classification → entity extraction → structured rewrite → intent/shape/ambiguity). It is a mandatory runtime stage but has **no routing authority**. | Early stage |
| `RetrievalPipeline` | Selects strategies (vector, metadata, hybrid) and fetches context from the Knowledge System. **Implemented as** `AdvancedRetrievalPipeline` (single entrypoint for retrieval-capable workflows). | Mid stage |
| `PostRetrievalPipeline` | Ranks, compresses, or filters context; may invoke judges sufficiency before generation. | After retrieval, before / during generation |

## Tools and policy

| Component | Responsibility |
|-----------|----------------|
| `DeterministicToolStrategy` | Executes tools with fixed contracts (e.g. meeting-minutes metadata tools) without open-ended model tool loops. Sole deterministic tool entrypoint; invoked only from `RagExecutionOrchestrator` when P13 adaptive routing selects the deterministic-tool route family. |
| `MeetingMinutesToolExecutionCore` | Shared business execution for the five meeting-minutes `DeterministicToolKind` tools; used by deterministic execution and by P9 function calling after model tool selection (no duplicate semantics in FC). |
| `FunctionCallingStrategy` | P9: bounded Spring AI function-calling (single tool-enabled round, optional follow-up answer generation without tools). Sole FC entrypoint; invoked only from `RagExecutionOrchestrator` when config and `QueryPlan` gates pass. Final FC trace fields are written only by the orchestrator. |
| `FunctionCallingPolicyResolver` | Exposes the whitelisted tool subset from `QueryPlan` when FC is eligible; no LLM calls. |
| `ToolPolicy` | (Target) Allowed tools, rate limits, safety gates, and fallbacks per workflow / tenant / project. |

## Advisor, clarification, memory, routing

| Component | Responsibility |
|-----------|----------------|
| `AdvisorStrategy` | **P10 (implemented):** sole orchestrated advisor **execution** entrypoint; chains snapshot-bound retrieval (`RetrievalAdvisor` → `AdvancedRetrievalPipeline`) and deterministic packing (`ContextPackingAdvisor` → `PackedContextSet`). Invoked only from `RagExecutionOrchestrator`. |
| `AdvisorPolicyResolver` | **P10:** policy only (no LLM, no retrieval); produces `AdvisorDecision` for dense workflows when `useAdvisor` and gates pass. |
| `PackedContextSet` / `PackedContextBlock` | **P10:** immutable advisor-produced packed context; optional on `ExecutionContext` for dense workflows. |
| `AdvisorMode` | Operational mode for advisory behaviour (e.g. suggest vs act) as part of resolved config. |
| `ClarificationSession` | (Target) Tracks multi-turn clarification state when the runtime must resolve ambiguity before answering. **P11 (implemented):** pending state is stored only in `conversations.pending_clarification_jsonb`; orchestration lives under `application.service.runtime.clarification`. |
| `ClarificationQuestionGenerator` | **P11:** deterministic template map from `ClarificationQuestionKind` (no LLM). |
| `ClarifiedQueryRefiner` | **P11:** frozen merge `BASE:…` + newline + `QUESTION:…` + newline + `ANSWER:…` into `ExecutionContext.effectivePlanningInputText` for the next `QueryUnderstandingPipeline` call (no LLM). |
| `ConversationMemoryPolicy` | What history is injected, summarised, or dropped across turns. **P12 (implemented):** bounded runtime-owned memory stage under `application.service.runtime.memory` that loads persisted conversation history, selects a fixed slice, executes at most one LLM-backed condensation call, and produces the final `effectivePlanningInputText` for QU. |
| `AdaptiveRoutingStrategy` / `AdaptiveRoutingPolicyResolver` | **P13 (implemented):** deterministic, runtime-owned route-family selection stage that runs after clarification policy and before any execution-family stage. Produces a single `AdaptiveRoutingDecision` + `RouteExecutionGate` that the orchestrator consumes to enforce route-family exclusivity. |
| `AdaptiveRouteKind` / `AdaptiveRoutingOutcome` | **P13:** closed route-family set and terminal routing outcome set; summary is written only by `RagExecutionOrchestrator` into `ExecutionTrace`. |

## Judges

| Component | Responsibility |
|-----------|----------------|
| `JudgeStrategy` / `JudgePolicyResolver` | **P14 (implemented):** runtime-owned, orchestrator-only post-answer judge stage. Runs after the selected route family produces a candidate answer; can accept it or request exactly one bounded repair attempt (workflow candidates only in P14). Does not re-run clarification, memory, QU, or routing; does not change route family. |
| `JudgeCandidateSource` / `JudgeOutcome` | **P14:** candidate-source classification and closed terminal judge outcome set; summary fields are written only by `RagExecutionOrchestrator` into `ExecutionTrace`. |
| *(Target) Retrieval judges* | Future optional judges (e.g. retrieval sufficiency, faithfulness) belong as explicit runtime stages under orchestrator ownership, not hidden inside workflows or controllers. |

## Interactions (prose)

- `WorkflowSelector` is a lower-level workflow chooser used only when the selected route family permits workflow execution.
- `RagExecutionOrchestrator` drives pipelines and strategies in a single canonical runtime order, appending to `ExecutionTrace`. Order for orchestrated chat: `ExecutionContextFactory` (loads `pending_clarification_jsonb`, runs `ClarificationStateResolver` + `ClarifiedQueryRefiner`, sets `preMemoryPlanningInputText`) → **P12 memory stage** (loads persisted messages, selects a fixed slice, executes at most one LLM-backed condensation call, sets final `effectivePlanningInputText` and memory trace summaries) → **`QueryUnderstandingPipeline` (once per turn on final `effectivePlanningInputText`)** → **P11 clarification policy** (`ClarificationPolicyResolver`; on `ASKED_*` the orchestrator returns immediately with `workflowName=clarification` and does **not** run routing or any downstream execution families) → **P13 adaptive routing** (`AdaptiveRoutingStrategy`) → execute exactly one primary route family (workflow route, deterministic tools, function calling, or advisor) with at most one deterministic workflow fallback when the selected non-workflow family does not terminate the turn → **P14 judge** (post-answer, bounded evaluation + at most one repair attempt, never changes route family or re-runs upstream stages) → finalize response. `WorkflowSelector` is invoked only inside workflow-capable routes (direct/retrieval) and after successful advisor execution to choose a retrieval-capable workflow. Downstream non-selected families do not execute for that turn.
- **P15 trace persistence (implemented):** after the turn is finalized (including judge when enabled), the runtime boundary (`ProcessQueryService` / `SimpleProcessQueryService`) invokes `RuntimeTracePersistenceService` to append one best-effort `runtime_execution_trace` row containing a bounded JSON projection of the finalized `ExecutionTrace` plus linkage identifiers (`user_id`, `project_id`, optional `conversation_id`/`message_id`, `correlation_id`, optional `resolved_config_snapshot_id`/`config_hash`). Persistence never re-runs runtime stages and must not fail the response.
- **P16 trace query surfaces (implemented):** product REST exposes minimal, read-only endpoints to list per-conversation runtime trace summaries and fetch trace detail (by trace id or message id). All reads are owner-scoped and flow through a single application-layer owner `RuntimeTraceQueryService` which queries `runtime_execution_trace` directly (no second read model, no JSON scanning, no repository access from controllers).
- **P17 trace export core (implemented):** product REST exposes minimal, read-only endpoints to export either a single persisted runtime trace (by trace id or by conversation+message id) or a bounded per-conversation bundle. Exports are generated synchronously by `RuntimeTraceExportService` which depends only on `RuntimeTraceQueryService`, produces a deterministic ZIP (`manifest.json`, `traces/index.json`, per-trace JSON files), enforces a fixed count cap for bundles (200) and a hard ZIP size cap (10 MB, reject with 413), and excludes prompts/message content/ExecutionContext/replay instructions.
- `PromptStack` is populated via **Runtime Configuration** (`SystemPromptComposer`); see [configuration-resolution-model.md](configuration-resolution-model.md).
- `ExecutionTrace` is consumed by **Platform & Ops** (observability) and **Experimentation / Lab** for runs.

## Alignment with the repository (current state)

### What already exists

- `ProcessQueryService` and `service.query.pipeline` (preparation, routing, synthesis) — see [BACKEND_PACKAGES.md](BACKEND_PACKAGES.md).
- Retrievers, rankers (`FaithfulnessRanker`, `LLMAsJudgeRanker`), reasoning strategies, metadata tools (`com.uniovi.rag.tool`).
- `ToolExecutionContext` in code (tool layer context; **not** identical to target `ExecutionContext` but related).
- Streaming / SSE product flows per [integration-flows.md](integration-flows.md).

### What is partial

- Named target components **map unevenly** to classes: several judges/rankers exist; **clarification session**, **adaptive routing engine**, and **execution trace** as unified concepts may be implicit or fragmented.
- **Workflow** selection may be spread across flags, presets, and services rather than one `WorkflowSelector` abstraction.
- **S0–S4** scenario ladder not uniformly encoded as `ExecutionRoute` values.

### S0–S1 runtime engine base

- **System base**: all implemented based on what had been developed before.
- **Implemented in code:** `com.uniovi.rag.domain.runtime.engine.ExecutionContext` (factory-built only via `ExecutionContextFactory`), `RagExecutionOrchestrator`, `WorkflowSelector` (deterministic matrix from `RagConfig` + `MaterializationStrategy`), five `ExecutionWorkflow` beans (`DirectLlmWorkflow`, `FullCorpusWorkflow`, `DocumentDenseRagWorkflow`, `ChunkDenseRagWorkflow`, `ChunkDenseMetadataWorkflow`), `KnowledgeRuntimeSnapshotSelector`, `SnapshotCorpusAssembler`, `AdvancedRetrievalPipeline` (snapshot-bound retrieval for the three dense workflows), `RagExecutionResult` / `ExecutionTrace`.
- **Product path:** `ProcessQueryService` is a façade: `ExecutionContextFactory` → `RagExecutionOrchestrator` → map to `QueryResponse`. Live resolution uses `ConfigResolverService.resolve` via `RuntimeConfigResolutionService.resolveForOrchestratedExecute` with the same merged conversation JSON as `ChatScopedRagConfigResolver`.
- **Errors:** `unsupported-runtime-configuration` and `knowledge-snapshot-unavailable` surface as `RagServiceException` with HTTP **422** (see `ErrorCode`).

### S2: P8 advanced retrieval core

- **Implemented in code:** `AdvancedRetrievalPipeline` (`com.uniovi.rag.application.service.runtime.retrieval`) is the **only** retrieval entrypoint for `DocumentDenseRagWorkflow`, `ChunkDenseRagWorkflow`, and `ChunkDenseMetadataWorkflow`. It consumes `ExecutionContext`, `QueryPlan`, and workflow name; uses `QueryPlan.rewrittenQueryText` as the canonical retrieval query; supports `DENSE_ONLY` and `HYBRID_DENSE_SPARSE` (dense + PostgreSQL FTS, RRF-only fusion); applies deterministic rerank, filter, and extractive compression; returns `CuratedContextSet` with `RetrievalDiagnostics` and frozen substage traces (`retrieval_build_request`, `retrieval_dense`, `retrieval_sparse`, `retrieval_fuse`, `retrieval_rerank`, `retrieval_filter`, `retrieval_compress`). `RagExecutionOrchestrator` merges those traces and copies diagnostics into `ExecutionTrace.retrievalDiagnostics`.
- **Frozen constraint:** `WorkflowSelector` remains the only workflow chooser; `MaterializationStrategy.HYBRID` with retrieval routes like chunk-level retrieval (metadata flag selects chunk vs chunk+metadata workflow); `STRUCTURED_SEARCH` with retrieval remains unsupported on the orchestrated path.

### What is still missing

- **Persisted** `ExecutionTrace` / full lab–product parity for trace export beyond in-memory + logs; STRUCTURED_SEARCH on the orchestrated path; phases beyond P10 (clarification execution, memory advisor, routing advisor, judges as traceable stages).
- Full **ToolPolicy** surface matching target semantics.
- All **four judges** as discrete, traceable stages if the target requires them for S4.
- Documentation-to-code traceability matrix (optional future table) without changing this canonical vocabulary.

### S2: P5–P6 query understanding core

- **Implemented in code:** `QueryUnderstandingPipeline` as a mandatory runtime stage that produces an immutable `QueryPlan` for every orchestrated request and is executed by `RagExecutionOrchestrator` **before** `WorkflowSelector`. QU failures are non-fatal and visible in `QueryPlan.pipelineNotes` and `ExecutionTrace` stage entries.
- **Frozen constraint:** `WorkflowSelector` does not branch on `QueryPlan`; `QueryPlan` is planning metadata only.

### S2: P7 deterministic tools core

- **Implemented in code:** `DeterministicToolStrategy` (package `application.service.runtime.tool`) is the **only** deterministic tool entrypoint. `RagExecutionOrchestrator` runs it **after** `WorkflowSelector.select(...)` and **before** `workflow.execute(...)`. Resolution uses **only** `ExecutionContext`, `ResolvedRuntimeConfig`, `QueryPlan`, and the selected workflow name; it does not re-run classifier/NER or reconstruct `QueryPlan`. When ambiguity is not `SUFFICIENT` or `toolsEnabled` is false, tools are suppressed and the trace records the reason; when a tool executes successfully, the orchestrator short-circuits and returns the tool answer without invoking the workflow. `ExecutionTrace` includes `tool_resolve`, `tool_execute`, `tool_result_map` stages plus summary fields (`deterministicToolOutcome`, `deterministicToolKind`, `deterministicToolDetail`).
- **Frozen constraint:** `ExecutionWorkflow` implementations do not perform tool resolution, selection, or execution.
