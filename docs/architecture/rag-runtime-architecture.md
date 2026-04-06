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
| `WorkflowSelector` | Chooses `ExecutionWorkflow` from **resolved config** plus query-understanding signals. | `ResolvedRuntimeConfig`, classifier/QU signals | Workflow binding |
| `ExecutionTrace` | Structured record of decisions, stages, and timings for observability and lab reproducibility. | Events from orchestrator and pipelines | Trace export / logs / metrics |

## Pipelines

| Component | Responsibility | Typical placement in workflow |
|-----------|----------------|------------------------------|
| `QueryUnderstandingPipeline` | Normalizes intent, extracts constraints, may invoke classifier client; feeds routing and retrieval planning. | Early stage |
| `RetrievalPipeline` | Selects strategies (vector, metadata, hybrid) and fetches context from the Knowledge System. | Mid stage |
| `PostRetrievalPipeline` | Ranks, compresses, or filters context; may invoke judges sufficiency before generation. | After retrieval, before / during generation |

## Tools and policy

| Component | Responsibility |
|-----------|----------------|
| `DeterministicToolStrategy` | Executes tools with fixed contracts (e.g. metadata tools) without open-ended model tool loops. |
| `FunctionCallingToolStrategy` | Uses model-driven tool calls where appropriate; bounded by `ToolPolicy`. |
| `ToolPolicy` | Allowed tools, rate limits, safety gates, and fallbacks per workflow / tenant / project. |

## Advisor, clarification, memory, routing

| Component | Responsibility |
|-----------|----------------|
| `AdvisorMode` | Operational mode for advisory behaviour (e.g. suggest vs act) as part of resolved config. |
| `ClarificationSession` | Tracks multi-turn clarification state when the runtime must resolve ambiguity before answering. |
| `ClarificationQuestionGenerator` | Produces candidate clarification questions. |
| `ClarifiedQueryRefiner` | Merges user replies into a refined query for retrieval / generation. |
| `ConversationMemoryPolicy` | What history is injected, summarised, or dropped across turns. |
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

- `WorkflowSelector` uses `ExecutionContext` (especially resolved config and understanding signals) to bind an `ExecutionWorkflow`.
- `RagExecutionOrchestrator` drives pipelines and strategies in workflow order, appending to `ExecutionTrace`.
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

### Microphase 4.1 (S0–S1 runtime engine base)

- **Implemented in code:** `com.uniovi.rag.domain.runtime.engine.ExecutionContext` (factory-built only via `ExecutionContextFactory`), `RagExecutionOrchestrator`, `WorkflowSelector` (deterministic matrix from `RagConfig` + `MaterializationStrategy`), five `ExecutionWorkflow` beans (`DirectLlmWorkflow`, `FullCorpusWorkflow`, `DocumentDenseRagWorkflow`, `ChunkDenseRagWorkflow`, `ChunkDenseMetadataWorkflow`), `KnowledgeRuntimeSnapshotSelector`, `SnapshotCorpusAssembler`, `SnapshotBoundRetrievalService`, `RagExecutionResult` / `ExecutionTrace`.
- **Product path:** `ProcessQueryService` is a façade: `ExecutionContextFactory` → `RagExecutionOrchestrator` → map to `QueryResponse`. Live resolution uses `ConfigResolverService.resolve` via `RuntimeConfigResolutionService.resolveForOrchestratedExecute` with the same merged conversation JSON as `ChatScopedRagConfigResolver`.
- **Errors:** `unsupported-runtime-configuration` and `knowledge-snapshot-unavailable` surface as `RagServiceException` with HTTP **422** (see `ErrorCode`).

### What is still missing

- **Persisted** `ExecutionTrace` / full lab–product parity for trace export beyond in-memory + logs; HYBRID / STRUCTURED_SEARCH / tools / advisors on the orchestrated path.
- Full **ToolPolicy** surface matching target semantics.
- All **four judges** as discrete, traceable stages if the target requires them for S4.
- Documentation-to-code traceability matrix (optional future table) without changing this canonical vocabulary.
