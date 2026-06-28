# ADR 0010 — RAG scenario ladder S0 to S4

## Status

Accepted

## Context

The project requires a **defensible experimental ladder** covering baseline RAG through adaptive and judge-heavy behaviour. Informal stage naming risks **S3–S4** being treated as optional extras.

## Decision

1. Scenarios **S0–S4** are **architecturally frozen** as a ladder ([implementation-roadmap.md](../architecture/implementation-roadmap.md) blocks **5–8**).
2. **S0–S2** form the **core spine** of the **primary benchmark** narrative.
3. **S3–S4** are **first-class** architecturally: they must be **implementable** and **documented** with the same rigour as S0–S2; the difference is **emphasis in the main benchmark story**, not optional existence.
4. Mapping from scenarios to **`ExecutionRoute`** / workflows is defined during runtime implementation phases.

## Consequences

- Lab suites and metrics must **tag** runs with ladder levels once implemented.
- Skipping S3–S4 requires a **new ADR**, not silent scope shrink.
- [rag-runtime-architecture.md](../architecture/rag-runtime-architecture.md) tool and judge components align to **S3** and **S4** expectations.

## References

- [rag-runtime-architecture.md](../architecture/rag-runtime-architecture.md)
- [implementation-roadmap.md](../architecture/implementation-roadmap.md)
