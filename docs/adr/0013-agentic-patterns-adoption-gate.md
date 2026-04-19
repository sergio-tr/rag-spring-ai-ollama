# ADR 0013: Gate for additional agentic patterns beyond the orchestrated runtime

## Context

The product runtime is already **explicitly staged**: clarification, memory, query understanding, adaptive routing, deterministic tools, function calling, advisor packing, workflows, judge, trace persistence (`RagExecutionOrchestrator` and [`rag-runtime-architecture.md`](../architecture/rag-runtime-architecture.md)).

“Agentic” stacks (multi-step autonomy, planner–executor loops, unconstrained tool cycles) introduce **tracing, safety, and determinism** risks when bolted beside this engine.

## Decision

1. **Do not** add autonomous multi-step agents or planner loops **unless** all of the following are satisfied **before** implementation:
   - **ADR** describing scope, autonomy bounds, and rollback;
   - **trace parity**: every model/tool step maps to existing or extended `ExecutionTrace` semantics (no opaque side channels);
   - **tool closure**: only whitelisted tools (`DeterministicToolKind` / registered `@Tool` names aligned with [`ToolDescriptor`](../../rag-service/src/main/java/com/uniovi/rag/configuration/ToolDescriptor.java)), with timeouts and per-turn caps unchanged or tightened;
   - **`mvn test`** in `rag-service/` passes with new tests proving limits (no infinite tool loop in happy path).

2. **Default for maintenance:** prefer **deterministic workflows** and **bounded function-calling** already owned by the orchestrator.

## Consequences

- Innovation in “agents” stays **reviewable** and **observable** inside the existing runtime boundary.
- Product routes avoid hidden autonomy that would **contradict** route-family exclusivity documented for P13.

## Related

- [ADR 0005](0005-target-rag-architecture-and-runtime-center.md)
- [`docs/ai/spring-ai-rag-inventory.md`](../ai/spring-ai-rag-inventory.md)
