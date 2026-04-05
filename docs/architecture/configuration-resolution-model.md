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
4. **Output:** materialize `ResolvedRuntimeConfig`; optional **persisted** row in `resolved_config_snapshot` via `ResolvedConfigSnapshotApplicationService` (insert-only, `config_hash`, §6.1 in [DATA_MODEL.md](DATA_MODEL.md)).
5. **Prompts:** `SystemPromptComposer` produces the **effective system prompt** as part of resolved semantics (not “just UX copy”).
6. **Index impact:** if change affects chunking, embedding model, or indexed fields, `ReindexImpactAnalyzer` flags required knowledge operations.

## Effective system prompt (runtime configuration, not UX-only)

For each LLM call at the architectural level:

- **Logical inputs:** `effective system prompt` + `user query`.

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

- **`ResolvedConfigSnapshot` (domain)** exists; **persisted** snapshots are written from product `POST …/config/resolved-snapshots` (see [DATA_MODEL.md](DATA_MODEL.md) §6.1). Lab runs continue to reference `resolved_config_snapshot.id` where the evaluation model already supports it.
- Documented mapping from **every** governance-relevant flag to **capabilities** may still evolve (future microphase).

### What is still missing

- Uniform **snapshot** row attached to **ExecutionTrace** for all execution paths (runtime engine follow-up).
- Broader user-visible compatibility matrix surfacing beyond current rule engine output.
