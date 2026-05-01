# ADR 0009 — Unified product and Lab execution engine

## Status

Accepted

## Context

The repository implements a **product** API and **Lab** features (evaluations, async jobs, classifier proxy). Diverging execution stacks would make benchmark results **incomparable** with production behaviour.

## Decision

1. **Product** and **Lab/benchmark** share the **same RAG runtime execution semantics** ([rag-runtime-architecture.md](../architecture/rag-runtime-architecture.md)).
2. Differences are expressed through **configuration**, **datasets**, and **entrypoints**, not duplicate orchestrators.
3. Temporary **shims** are allowed only if documented and scheduled for removal.

## Consequences

- Lab code must call the same core services as the product path for equivalent scenarios.
- **`ExecutionTrace`** should capture enough structure to reproduce runs ([rag-runtime-architecture.md](../architecture/rag-runtime-architecture.md)).
- Performance or isolation needs require a new ADR if they imply a second engine.

## References

- [target-architecture.md](../architecture/target-architecture.md)
- [0010-rag-scenario-ladder-s0-to-s4.md](0010-rag-scenario-ladder-s0-to-s4.md)
