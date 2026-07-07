# Architecture Decision Records (ADR)

ADRs capture **significant, stable** structural decisions (API surface, observability strategy, security boundaries, data conventions).

## When to add an ADR

- The change affects **how the system is built or operated** long-term (not a one-off bugfix).
- Several readers need **one** place for rationale (security, compatibility, data boundaries).
- The decision is **hard to infer** from OpenAPI or code alone.

**Skip an ADR** for routine tasks or anything that belongs only in a module `README.md` (how to run or test one service).

## File and index rules

- **One file per decision:** `NNNN-short-title.md` (four digits, kebab-case English slug).
- **Numbers are immutable:** supersede with a newer ADR that references the old file; **do not renumber** existing documents.
- **Display titles** (Markdown `H1` and index table) may be clarified; **filenames** change only with maintainer approval (breaks external links).

## Format

- Keep bodies short: **context**, **decision**, **consequences**, optional links to PRs/issues.

## Index

Add new rows when you add files.

| ID | Title | Status |
| --- | --- | --- |
| [0001](0001-lab-promotion-modes.md) | Research Lab promotion modes (A / B / C) | Accepted |
| [0002](0002-multitenancy-assumption.md) | Multi-user ownership and project-scoped isolation (single PostgreSQL DB); **not** SaaS-grade multi-tenancy - see ADR body | Accepted |
| [0003](0003-evaluation-async-project-scope-and-dataset-dedup.md) | Project scope for evaluation/async_task; dataset dedup by owner+sha256 | Accepted |
| [0004](0004-react-testing-library-behavior-first.md) | React component tests: behavior-first with Testing Library | Accepted |
| [0005](0005-target-rag-architecture-and-runtime-center.md) | Target RAG architecture: runtime engine as normative center | Accepted |
| [0006](0006-keycloak-identity-and-https-foundation.md) | Keycloak identity and HTTPS foundation | Accepted |
| [0007](0007-capability-groups-and-compatibility-rules.md) | Capability groups, compatibility rules, resolved config | Accepted |
| [0008](0008-system-prompt-stack-and-composition.md) | System prompt stack and effective composition | Accepted |
| [0009](0009-unified-product-and-lab-execution-engine.md) | Unified product and Lab execution engine | Accepted |
| [0010](0010-rag-scenario-ladder-s0-to-s4.md) | RAG scenario ladder S0–S4 | Accepted |
| [0011](0011-knowledge-system-artifacts-snapshots-and-materialization.md) | Knowledge artefacts, snapshots, materialization | Accepted |
| [0012](0012-backend-refactoring-governance.md) | Backend refactoring governance and slice policy | Accepted |
| [0013](0013-agentic-patterns-adoption-gate.md) | Gate for additional agentic patterns beyond the orchestrated runtime | Accepted |

## Related

- Diagrams and package maps: [../architecture/README.md](../architecture/README.md)
- Governance: [../development/documentation-guidelines.md](../development/documentation-guidelines.md)
