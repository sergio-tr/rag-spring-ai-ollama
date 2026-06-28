# ADR 0012 — Backend refactoring governance (layer naming, statics, incremental cleanup)

## Status

Accepted

## Context

The `rag-service` codebase mixes established hexagonal packages (`domain`, `application`, `infrastructure`, `interfaces.rest`) with transitional areas (`service.*`, `controller`, `api.*`). Without a written contract, refactors risk name collisions across layers, hidden dependency growth, accidental large-bang changes, and silent contract regressions next to frozen HTTP and ArchUnit expectations.

## Decision

1. **Single normative reference** for incrementally refactoring the backend: [`docs/backend/refactoring-governance.md`](../backend/refactoring-governance.md) (Action Plan). Individual refactor slices must cite it and scope one bounded context or anchor package unless an emergency exception is documented.
2. **Pragmatic Clean Architecture / DDD**: domain types stay framework-free where rules already apply; application services orchestrate use cases and ports; infrastructure holds adapters; REST stays in `interfaces.rest`. No mandate to rewrite working pre-refactor areas in one pass; use **slices** with explicit return on investment.
3. **Layer naming and policies** (including static methods, rich domain vs services, `package-info.java`, and FQCN avoidance) follow the tables in that document — not ad hoc renames.
4. **Quality gates** remain anchored in [Gates](../quality/README.md): `FD-single-gate-command` (`./mvnw clean verify` from `rag-service/` for production-impacting work). Documentation-only phases do not replace that baseline.
5. **Frozen documentation** listed in the governance doc **denylist** must not be edited as part of style governance work unless a separate ADR or change request authorizes it.

## Consequences

- Refactor plans and MR descriptions can point reviewers to one document for naming and dependency rules.
- New `package-info.java` files and type renames align with a repeatable checklist, reducing cross-layer name clashes.
- Contradicting this ADR requires a new ADR that supersedes or amends 0012 explicitly.

## References

- [`docs/backend/refactoring-governance.md`](../backend/refactoring-governance.md)
- [`docs/architecture/BACKEND_PACKAGES.md`](../architecture/BACKEND_PACKAGES.md)
- [`docs/quality/README.md`](../quality/README.md)
- [`rag-service/src/test/java/com/uniovi/rag/architecture/LayeredArchitectureTest.java`](../../rag-service/src/test/java/com/uniovi/rag/architecture/LayeredArchitectureTest.java)
