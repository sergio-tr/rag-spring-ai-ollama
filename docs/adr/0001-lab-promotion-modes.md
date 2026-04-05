# ADR 0001 — Research Lab impact on production (promotion modes)

## Status

Accepted

## Context

The Research Lab runs evaluations, classifier train/eval, and long-running jobs. Without explicit rules, automation could overwrite production **RagConfiguration**, **RagPreset**, or classifier routing, harming auditability and user trust.

The three modes below match the product expectation that Lab work must not silently overwrite production configuration.

## Decision

1. **Mode A — Report-only (default)**  
   Lab runs produce metrics and stored experiment artifacts. **No** automatic write to user/project configuration, presets, or classifier selection.

2. **Mode B — Guided promotion (optional)**  
   Applying a Lab outcome to production requires an **explicit** user or ADMIN action (e.g. create a **RagPreset** or update **PROJECT** `rag_configuration` from a frozen snapshot).

3. **Mode C — Classifier rollout (optional)**  
   Activating a trained classifier artifact requires an **explicit** policy action (ADMIN or documented scope), not completion of a Lab job alone.

**Rule:** Only **A** may happen without a dedicated promotion step. **B** and **C** are never silent side effects of asynchronous Lab jobs.

## Consequences

- Backend and frontend must not bind “job finished” to automatic config or preset writes without a separate promotion workflow.
- Auditing and UX copy should distinguish “results available” from “applied to production”.
- Future automation of B/C must remain opt-in and traceable (same PR/issue as any behaviour change).

## References

- [ADR 0003 — evaluation async, project scope, dataset dedup](0003-evaluation-async-project-scope-and-dataset-dedup.md)
- [architecture/integration-flows.md](../architecture/integration-flows.md)
- OpenAPI: `/v3/api-docs` (when enabled) or `rag-service/scripts/export-openapi.sh`
