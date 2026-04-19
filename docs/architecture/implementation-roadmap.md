# Implementation roadmap (canonical blocks)

**Purpose:** **Target** ordering and **canonical names** for implementation work after architecture freeze. These thirteen block names and their order (**1 → 13**) are **normative** for architecture and planning documents in this repository: roadmap text, milestone write-ups, and cross-references to ADRs—**unless a future ADR renames or reorders them**. (They are **not** a substitute for release or branch policy unless the team agrees that separately.)

**Related:** [target-architecture.md](target-architecture.md), [ADR 0005](../adr/0005-target-rag-architecture-and-runtime-center.md) through [ADR 0011](../adr/0011-knowledge-system-artifacts-snapshots-and-materialization.md).

## Canonical blocks (fixed order)

1. **Governance & Architecture Freeze** — Deliver and merge canonical `docs/` architecture package + ADRs 0005–0011.
2. **Platform Base** — Compose, images, environments, baseline observability; prerequisites for all runtime work.
3. **Runtime Configuration Core** — Capability model, compatibility rules, resolution, snapshots, `SystemPromptComposer`, `ReindexImpactAnalyzer` alignment.
4. **Knowledge System** — Artefacts, snapshots, reindex events, materialization strategies, structured search contracts.
5. **Runtime Engine Base (S0–S1)** — Core orchestration, context, basic pipelines, trace hooks; scenario spine start.
6. **Advanced Deterministic RAG (S2)** — Deterministic tools, stronger retrieval/post-retrieval without full agentic loop.
7. **Modular / Agentic (S3)** — Function-calling strategies, modular workflows, tool policy at scale.
8. **Adaptive / Judge-based (S4)** — Adaptive routing, full judge set, clarification flows as required by target architecture.
9. **Experimentation / Lab** — Unified engine for benchmarks, datasets, metrics; **ExecutionTrace** for runs.
10. **Identity / HTTPS / Security** — Keycloak integration, HTTPS termination, hardening aligned with [ADR 0006](../adr/0006-keycloak-identity-and-https-foundation.md).
11. **Frontend Product + Lab** — Webapp alignment with resolved config and lab surfaces.
12. **Compliance / RGPD** — Data protection, retention, DPIA-oriented documentation and controls as required.
13. **Final Evidence / Memory / Defense** — Thesis evidence package, memory baselines, defence rehearsal artefacts.

## Per-block pattern (required when executing a block)

For each block, implementation planning must state:

- **Inputs:** which ADRs and which docs at hierarchy levels 2–5 in [target-architecture.md](target-architecture.md) … [knowledge-system-model.md](knowledge-system-model.md) apply.
- **Architectural outputs:** verifiable capabilities delivered (not low-level task lists — those belong in implementation plans).
- **Dependencies:** prior blocks that must be complete.

## Dependency sketch (high level)

- **2** before heavy **5–8** and **10–11**.
- **3** and **4** before **5–9** (config and knowledge inform runtime and lab).
- **5** before **6**, **6** before **7**, **7** before **8** (scenario ladder intent).
- **9** after **5** and **3** (lab needs engine + config); tighten with **4** for reproducible retrieval.
- **10** can proceed in parallel with mid runtime blocks but must finish before production claims.
- **12** after **10–11** for user-data-heavy compliance.
- **13** last.

## Alignment with the repository (current state)

### What already exists

- Substantial code in blocks **2** (docker/compose), **5** (partial — query pipeline), **4** (partial — ingestion/vectors), **9** (partial — lab endpoints), **11** (webapp).
- ADRs 0001–0004 predate this roadmap; **0005–0011** added with new phase.

### What is partial

- Blocks **3**, **4**, **7**, **8**, **10**, **12**, **13** are unevenly covered relative to the **target** docs.
- **S0–S4** labelling and **ExecutionRoute** mapping not yet uniform.

### What is still missing

- Explicit closure criteria per block in CI/docs as the project advances.
- Full **Keycloak** and **HTTPS** (block **10**) per ADR 0006.
- Dedicated **Compliance / RGPD** deliverables (block **12**).
