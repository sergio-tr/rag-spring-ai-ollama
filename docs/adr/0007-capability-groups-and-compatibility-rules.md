# ADR 0007 — Capability groups and compatibility rules

## Status

Accepted

## Context

The codebase uses **feature flags** and layered configuration. A flat flag forest leads to **inconsistent** combinations and unmaintainable conditionals, harming reproducibility for Lab.

## Decision

1. **Governing semantics** for runtime behaviour are expressed through **capability groups**, **capabilities**, **compatibility rules**, **resolved runtime configuration**, and **resolved configuration snapshots** ([configuration-resolution-model.md](../architecture/configuration-resolution-model.md)).
2. Raw **flags** may remain as **inputs** to resolution but must not be the **primary architectural vocabulary** for describing behaviour.
3. **`ReindexImpactAnalyzer`** (or equivalent) is the conceptual gate for changes that affect indices, embeddings, or metadata materialization.

## Consequences

- Configuration UI and APIs should converge on **capabilities and presets** over time.
- Invalid combinations must be **rejected or degraded** with explicit traces, not silent fallthrough.
- Alternate HTTP paths without real use remain subject to removal policy in [target-architecture.md](../architecture/target-architecture.md).

## References

- [configuration-resolution-model.md](../architecture/configuration-resolution-model.md)
- [knowledge-system-model.md](../architecture/knowledge-system-model.md)
- [DATA_MODEL.md](../architecture/DATA_MODEL.md)
