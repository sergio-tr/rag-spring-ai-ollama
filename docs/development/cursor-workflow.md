# Cursor workflow (architecture-first)

**Purpose:** Rules for using Cursor (or similar agents) so **no parallel architecture** is invented in prompts, comments, or scratch docs. Implementation detail may move fast; **vocabulary and boundaries** must match the canonical documents.

**Canonical sources (precedence):** See [target-architecture.md](../architecture/target-architecture.md) and the hierarchy summarised in ADRs **0005–0011** — ADRs first, then `target-architecture.md`, `rag-runtime-architecture.md`, `configuration-resolution-model.md`, `knowledge-system-model.md`, `implementation-roadmap.md`.

## Before implementing a change

1. Read [target-architecture.md](../architecture/target-architecture.md) for affected **subsystem**.
2. If touching runtime behaviour: [rag-runtime-architecture.md](../architecture/rag-runtime-architecture.md).
3. If touching configuration or prompts: [configuration-resolution-model.md](../architecture/configuration-resolution-model.md) and [ADR 0008](../adr/0008-system-prompt-stack-and-composition.md).
4. If touching ingestion, indices, or retrieval contracts: [knowledge-system-model.md](../architecture/knowledge-system-model.md).
5. Read the **relevant ADR(s)** among 0005–0011.
6. Respect module READMEs for **how** (commands, env); do not duplicate them into `docs/`.

## Forbidden without updating canonical docs first

Do **not** introduce in agent prompts, code comments, class names, or ad-hoc plans:

- **New named runtime components**, **configuration concepts**, or **knowledge artefacts** that are **not** already defined in `rag-runtime-architecture.md`, `configuration-resolution-model.md`, or `knowledge-system-model.md` (as applicable).

If a new concept is required:

1. Update the **canonical** architecture document(s).
2. Add or update an **ADR** in the same documentation change.
3. Only then treat the idea as an agreed architectural decision in code.

## Structural changes

Changes that alter **subsystem boundaries**, **runtime semantics**, **configuration semantics**, **prompt semantics**, or **knowledge-system semantics** require an **ADR** (new or updated) plus aligned updates to `target-architecture.md` and/or the specialised architecture doc — **before** the change is considered valid for implementation.

## Implementation plans

A later **implementation plan** (e.g. a `.cursor/plans/*.plan.md` file) may refine **tasks**, **sequencing**, **files touched**, and **tests**, but it **must not** introduce **new architectural decisions** in those areas unless the canonical documents and ADRs are updated **first** in the same documentation change.

## No duplicate “truth”

- **Commands, env matrices, ports:** module `README.md` only.
- **What / why at system level:** `docs/` with links out.

See [documentation-guidelines.md](documentation-guidelines.md).
