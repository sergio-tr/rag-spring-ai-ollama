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

1. **Inputs:** system defaults, user-level settings, project-level settings, selected preset/profile, optional **flags** as raw inputs, workflow selection hints.
2. **Normalization:** map raw inputs to **capabilities** and preset identifiers.
3. **Validation:** apply `CompatibilityRule` set; fail fast or degrade with explicit trace.
4. **Output:** materialize `ResolvedRuntimeConfig` and optionally persist `ResolvedConfigSnapshot` for the run.
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

- **CapabilityGroup** / **Capability** / **CompatibilityRule** naming and explicit rule engine may not exist as such; validation may be ad hoc.
- **`ResolvedConfigSnapshot`** as an explicit persisted artefact for every lab run may be partial or implicit.
- **`ReindexImpactAnalyzer`** may be implicit (operator knowledge) rather than a named component.
- **`SystemPromptComposer`** may be spread across builders/templates without a single documented seam.

### What is still missing

- Explicit compatibility matrix and user-visible errors when combinations are invalid.
- Uniform **snapshot** of resolved config attached to **ExecutionTrace** / lab results.
- Documented mapping from **every** governance-relevant flag to **capabilities** (future microphase).
- Single place in code identified as `SystemPromptComposer` matching this document.
