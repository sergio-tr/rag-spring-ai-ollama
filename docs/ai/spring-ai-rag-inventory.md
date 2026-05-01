# Spring AI / RAG inventory and measurement baseline

**Purpose:** Single place for **verified** backend inventory, **how success is measured**, and **explicit freezes** so modernization work stays incremental.

**Sources of truth:** Java packages under `rag-service/src/main/java/com/uniovi/rag/`, [`rag-service/pom.xml`](../../rag-service/pom.xml), tests under `rag-service/src/test/`.

## Component inventory (verified in code)

| Area | Status | Notes |
| ------ | -------- | -------- |
| Spring AI + Ollama | Present | `spring-ai-ollama-spring-boot-starter`, `ChatClient`, `ChatModel`, `EmbeddingModel`; BOM `spring-ai.version` in `rag-service/pom.xml` |
| Vector store | Present | `PgVectorStore` via Spring AI; snapshot-bound search in dense retrieval strategies |
| Orchestrated runtime | Present | `RagExecutionOrchestrator`, route families, `ExecutionTrace` persistence (P15) |
| Advanced retrieval | Present | `AdvancedRetrievalPipeline` — sole dense-workflow retrieval entry |
| Advisors | Present | Spring AI `QuestionAnswerAdvisor` bean + custom `AdvisorPolicyResolver` / `AdvisorStrategy` (P10 packing path) |
| Deterministic tools | Present | `QueryType` tools + `MeetingMinutesToolsAdapter` (`@Tool`); FC whitelist `DeterministicToolKind` + `DefaultFunctionCallingToolRegistry` |
| Legacy synthesis path | Present | `AnswerGenerationKernel` / `ResponseSynthesisPipeline` — orchestrated product path goes through workflows; legacy remains for evaluation and transitional callers |
| Knowledge ingest | Present | `KnowledgePipelineOrchestrator` — documented sole write path in class Javadoc |
| Observability | Partial | `ObservabilitySupport` + traced decorators; Micrometer timers for workflow LLM calls (`rag.ai.llm.invoke`), ETL counters (`rag.knowledge.etl.events`) |

## Measurement baseline (normative)

Track **before vs after** each increment using the same definitions:

| Metric | Instrument | Interpretation |
| -------- | ------------ | ---------------- |
| Workflow LLM latency | Micrometer timer `rag.ai.llm.invoke` (tag `workflow`) | p95 wall time per workflow class |
| Knowledge ETL outcomes | Counter `rag.knowledge.etl.events` (tags `stage`, `outcome`) | Volume of ingest starts / successes / failures |
| Route stability | Existing `ExecutionTrace` fields (`workflow`, `advisorOutcome`, deterministic tool summaries) | Regression detection when behaviour drifts |

## Compatibility freezes

- **HTTP surface P59–P61:** runtime trace regression suite controllers and contracts documented in [`rag-service/README.md`](../../rag-service/README.md); full module test gate remains `mvn test` from `rag-service/`.
- **Orchestrated runtime order:** P11 clarification → P12 memory → `QueryUnderstandingPipeline` → P11 policy → P13 routing → execution family → P14 judge → P15 persistence — see [`rag-runtime-architecture.md`](../architecture/rag-runtime-architecture.md).

## Documentation allowlist / denylist

Edits must follow the plan allowlist. **Do not edit:** [`docs/architecture/DATA_MODEL.md`](../architecture/DATA_MODEL.md), [`docs/architecture/configuration-resolution-model.md`](../architecture/configuration-resolution-model.md).
