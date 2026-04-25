# Runtime configuration and resolution model

**Purpose:** Canonical **target** model for how configuration becomes **effective runtime behaviour** and how **system prompts** are composed. Raw feature flags remain allowed as **inputs** but do not define the architecture ([ADR 0007](../adr/0007-capability-groups-and-compatibility-rules.md), [ADR 0008](../adr/0008-system-prompt-stack-and-composition.md)).

**Related:** [target-architecture.md](target-architecture.md), [rag-runtime-architecture.md](rag-runtime-architecture.md), [knowledge-system-model.md](knowledge-system-model.md), [DATA_MODEL.md](DATA_MODEL.md) (persistence of presets and layers).

## Core entities

| Entity | Meaning |
|--------|---------|
| `CapabilityGroup` | Groups related toggles/capabilities for UX and validation (e.g. retrieval family, tools family). |
| `Capability` | Atomic feature the runtime can enable when compatible (may map from flags after resolution). |
| `CompatibilityRule` | Declares valid combinations of capabilities, models, and workflows; rejects or downgrades invalid resolved config. |
| `ResolvedRuntimeConfig` | **The** effective configuration for one request or execution: models, workflows, tool policy hooks, prompt composition inputs, scenario hints. |
| `ResolvedConfigSnapshot` | Immutable record of `ResolvedRuntimeConfig` used for reproducibility (lab runs, audits, debugging). |
| `ReindexImpactAnalyzer` | Determines whether a configuration or knowledge-definition change requires reindex, embedding refresh, or metadata rebuild; drives operator/lab warnings. |
| `SystemPromptComposer` | Builds the **effective system prompt** from the four layers below plus any workflow/preset additions required by ADR 0008. |

## Resolution flow (conceptual)

Production resolution follows a single **ResolutionPipeline** orchestrated by `ConfigResolverService` and `ConfigResolver`: load persisted layers (system, user including prefs/personalization, project, optional preset+profiles from DB, optional conversation `runtime_override_jsonb`), apply terminal request JSON, then validate (`CompatibilityValidator`), reindex preview (`ReindexImpactAnalyzer`), and compose prompts (`SystemPromptComposer`). **JSON merge** for configuration layers is owned by `ConfigResolver` / `RagConfigurationMerge` (+ `PresetProfilePayloadMerge` for preset+profile maps); adapters **load only**.

1. **Inputs:** system defaults, user-level settings (including `user_preferences` / `user_personalization`), project-level settings, optional persisted preset + linked profiles, optional conversation runtime JSON, optional request JSON, optional **flags** as raw inputs.
2. **Normalization:** map raw inputs to **capabilities** and preset identifiers.
3. **Validation:** apply `CompatibilityRule` set; fail fast or degrade with explicit trace.
4. **Output:** materialize `ResolvedRuntimeConfig`; optional **persisted** row in `resolved_config_snapshot` via `ResolvedConfigSnapshotApplicationService` (insert-only, `config_hash`, [Section 6.1](DATA_MODEL.md#dm-s6-1) in [DATA_MODEL.md](DATA_MODEL.md)). Knowledge execute-without-pin persists the same row shape plus **`knowledgeBuildProjection`** under `payload_jsonb` and a `config_hash` that includes that nested map ([DATA_MODEL.md — Section 6.1](DATA_MODEL.md#dm-s6-1)).
5. **Prompts:** `SystemPromptComposer` produces the **effective system prompt** as part of resolved semantics (not “just UX copy”).
6. **Index impact:** if change affects chunking, embedding model, or indexed fields, `ReindexImpactAnalyzer` flags required knowledge operations.

## Effective system prompt (runtime configuration, not UX-only)

For each LLM call at the architectural level:

- **Logical inputs:** `effective system prompt` + `user query`.

The runtime introduces `QueryUnderstandingPipeline` which consumes `ResolvedRuntimeConfig` (via `ExecutionContext.resolved()`) to deterministically build a `QueryPlan`. **P11:** QU normalizes `ExecutionContext.effectivePlanningInputText` (merged continuation text when `pending_clarification_jsonb` is active); `QueryPlan.rawUserQuery` stays the literal latest user turn; the canonical query text used for tools / retrieval / generation remains `QueryPlan.rewrittenQueryText` (not the raw user input).

**Clarification gating:** `clarificationEnabled` is part of the materialized `RagConfig` (from `rag.features.clarification-enabled` and optional JSON key `clarificationEnabled` in configuration `values` maps). When false or when no persistable conversation scope exists, the runtime records `DISABLED_BY_CONFIG` on the clarification trace without persisting pending state.

**Memory gating and planning input flow (P12):** `memoryEnabled` is part of the materialized `RagConfig` (from `rag.features.memory-enabled` and optional JSON key `memoryEnabled` in configuration `values` maps). When enabled and a persistable `conversation_id` exists, the runtime executes a bounded memory stage after P11 clarification pre-processing and before QU:

- `ExecutionContext.userQuery` remains the literal latest user turn (trace-only).
- `ExecutionContext.preMemoryPlanningInputText` is the clarification-refined planning input (after pending clarification merge, before memory).
- Memory selects a fixed recent history slice and executes at most one LLM-backed condensation call; on failure it deterministically falls back to `preMemoryPlanningInputText`.
- `ExecutionContext.effectivePlanningInputText` becomes the final memory-aware planning input and is the **only** text normalized by QU.

**Adaptive routing gating (P13):** `adaptiveRoutingEnabled` is part of the materialized `RagConfig` (from `rag.features.adaptive-routing-enabled` and optional JSON key `adaptiveRoutingEnabled` in configuration `values` maps). When enabled, the runtime executes a deterministic routing stage after P11 clarification policy and before any downstream execution-family stage; when disabled, the orchestrator derives a single compatibility workflow route without attempting routing.

**Judge gating (P14):** `judgeEnabled` is part of the materialized `RagConfig` (from `rag.features.judge-enabled` and optional JSON key `judgeEnabled` in configuration `values` maps). When enabled, the runtime executes a post-answer judge stage after the selected execution family produces a candidate answer; the judge performs at most one evaluation and at most one bounded repair attempt and never re-runs clarification, memory, QU, or adaptive routing.

**Layers of `effective system prompt` (all four are mandatory concepts):**

1. `base system prompt` — platform-wide baseline.
2. `account-level prompt` — user/account overlay.
3. `project-level prompt` — project overlay.
4. `workflow/preset prompt` — scenario- or preset-specific overlay.

The **UI** may edit underlying fields, but **canonical semantics** live under **Runtime Configuration** and `SystemPromptComposer`. The effective system prompt is part of **resolved** configuration for the request.

## Experimental studies

- **Phase-I style studies:** system prompt layers may be **intentionally varied** as experimental factors.
- **Later RAG studies:** a **stabilized** system prompt may serve as a **baseline** while other factors (retrieval, judges, routing) vary — without changing the four-layer composition architecture.

## Alignment with the repository (current state)

### What already exists

- Layered effective RAG configuration (system → user → project) described in [DATA_MODEL.md](DATA_MODEL.md) and services under `service.config`, `domain.runtime`, presets (`service.preset`).
- Feature flags and Spring configuration in `configuration` package; preset CRUD and sanitization.

### What is partial

- **`ResolvedConfigSnapshot` (domain)** exists; **persisted** snapshots are written from product `POST …/config/resolved-snapshots` and from knowledge flows (`KnowledgeConfigurationIntegrationService` / ingestion default snapshot) (see [DATA_MODEL.md — Section 6.1](DATA_MODEL.md#dm-s6-1)). Lab runs continue to reference `resolved_config_snapshot.id` where the evaluation model already supports it.
- **P18 runtime trace replay (internal only; see [rag-runtime-architecture.md](rag-runtime-architecture.md))** materializes `ResolvedRuntimeConfig` only from the persisted `resolved_config_snapshot` row referenced by `runtime_execution_trace.resolved_config_snapshot_id`. Replay does **not** call `RuntimeConfigResolutionService.resolveForOrchestratedExecute` and does **not** merge current conversation `runtime_override_jsonb` for semantic replay inputs.
- Documented mapping from **every** governance-relevant flag to **capabilities** may still evolve.

### What is still missing

- Uniform **snapshot** row attached to **ExecutionTrace** for all execution paths (runtime engine follow-up).
- Broader user-visible compatibility matrix surfacing beyond current rule engine output.
