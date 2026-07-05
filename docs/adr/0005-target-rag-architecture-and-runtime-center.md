# ADR 0005 - Target RAG architecture and runtime center

## Status

Accepted

## Context

The system combines a **product API**, a **webapp**, **Lab/benchmark** flows, configuration layers, and a **knowledge** pipeline. Without an explicit centre of gravity, “configuration” or “UI” risks defining architecture by accident, fragmenting orchestration and observability.

## Decision

1. The **RAG Runtime Engine** described in [rag-runtime-architecture.md](../architecture/rag-runtime-architecture.md) is the **normative centre** of the platform’s execution story.
2. Other subsystems in [target-architecture.md](../architecture/target-architecture.md) exist to **feed**, **configure**, **observe**, or **govern** that runtime - not to duplicate competing orchestration semantics.
3. Product and Lab must align to the **same** runtime vocabulary ([ADR 0009](0009-unified-product-and-lab-execution-engine.md)).

## Consequences

- Refactors and new features must preserve or update the **named runtime components** in lockstep with `rag-runtime-architecture.md`.
- New orchestration paths require an ADR if they change boundaries or semantics of the runtime centre.
- Documentation and product narrative should treat “RAG execution” as the primary technical thread.

## References

- [rag-runtime-architecture.md](../architecture/rag-runtime-architecture.md)
- [target-architecture.md](../architecture/target-architecture.md)
- [0009-unified-product-and-lab-execution-engine.md](0009-unified-product-and-lab-execution-engine.md)
