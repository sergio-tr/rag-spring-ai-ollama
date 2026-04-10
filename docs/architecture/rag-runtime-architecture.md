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
| `DeterministicToolStrategy` | Executes tools with fixed contracts (e.g. meeting-minutes metadata tools) without open-ended model tool loops. Only deterministic tool entrypoint and runs only inside `RagExecutionOrchestrator` after `WorkflowSelector`. |
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
| `AdaptiveRoutingEngine` | Chooses among execution routes (e.g. tool-heavy vs retrieval-only) from signals and config. |
| `ExecutionRoute` | Named path through workflows/strategies (may map to scenario ladder S0–S4). |

## Judges

| Component | Responsibility |
|-----------|----------------|
| `RetrievalSufficiencyJudge` | Decides if retrieved context is enough to answer or if retrieval should repeat or expand. |
| `FaithfulnessJudge` | Scores or filters answers against retrieved evidence. |
| `UnsupportedClaimJudge` | Flags claims not grounded in context. |
| `StopContinueJudge` | Decides termination vs further reasoning/tool rounds. |

## Interactions (prose)

- `WorkflowSelector` uses `ExecutionContext` (resolved config) to bind an `ExecutionWorkflow`.
- `RagExecutionOrchestrator` drives pipelines and strategies in workflow order, appending to `ExecutionTrace`. Order for orchestrated chat: `ExecutionContextFactory` (loads `pending_clarification_jsonb`, runs `ClarificationStateResolver` + `ClarifiedQueryRefiner`, sets `preMemoryPlanningInputText`) → **P12 memory stage** (loads persisted messages, selects a fixed slice, executes at most one LLM-backed condensation call, sets final `effectivePlanningInputText` and memory trace summaries) → **`QueryUnderstandingPipeline` (once per turn on final `effectivePlanningInputText`)** → **P11 clarification** (`ClarificationPolicyResolver` → optional `ClarificationStrategy` + `PendingClarificationStore` when the terminal outcome is `ASKED_*`; on `ASKED_*` the orchestrator returns immediately with `workflowName=clarification` and does **not** run `WorkflowSelector`, tools, FC, advisor, or workflow) → `WorkflowSelector` → `DeterministicToolStrategy` → optional P9 FC phase (`functionCallingEnabled`, ambiguity `SUFFICIENT`, non-empty FC policy) → **P10 advisor phase** (`AdvisorPolicyResolver` → `AdvisorStrategy` when gates pass, after FC and only if neither deterministic tools nor FC short-circuited the turn) → selected `ExecutionWorkflow`. Deterministic tool success suppresses FC; deterministic tool execution failure blocks FC and continues with the already selected workflow. Spring AI built-in advisors are not the architecture centre; the canonical advisor surface is `AdvisorStrategy` plus `domain.runtime.advisor` types. Dense workflows may receive `ExecutionContext.advisorPackedContextSet` (`PackedContextSet`) and must not duplicate retrieval/packing for that turn when present.
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
