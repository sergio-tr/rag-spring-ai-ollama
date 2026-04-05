# Architecture Decision Records (ADR)

ADRs capture **significant, stable** structural decisions (API surface, observability strategy, security boundaries, data conventions).

## Format

- One file per decision: `NNNN-short-title.md` (four-digit number, hyphenated title).
- Keep bodies short: **context**, **decision**, **consequences**, optional links to PRs/issues.

## Index

Add new rows when you add files.

| ID | Title | Status |
| --- | --- | --- |
| [0001](0001-lab-promotion-modes.md) | Research Lab promotion modes (A / B / C) | Accepted |
| [0002](0002-multitenancy-assumption.md) | Multi-tenancy and data isolation assumption | Accepted |
| [0003](0003-evaluation-async-project-scope-and-dataset-dedup.md) | Project scope for evaluation/async_task; dataset dedup by owner+sha256 | Accepted |
| [0004](0004-react-testing-library-behavior-first.md) | React component tests: behavior-first with Testing Library | Accepted |
| [0005](0005-target-rag-architecture-and-runtime-center.md) | Target RAG architecture: runtime engine as normative center | Accepted |
| [0006](0006-keycloak-identity-and-https-foundation.md) | Keycloak identity and HTTPS foundation | Accepted |
| [0007](0007-capability-groups-and-compatibility-rules.md) | Capability groups, compatibility rules, resolved config | Accepted |
| [0008](0008-system-prompt-stack-and-composition.md) | System prompt stack and effective composition | Accepted |
| [0009](0009-unified-product-and-lab-execution-engine.md) | Unified product and Lab execution engine | Accepted |
| [0010](0010-rag-scenario-ladder-s0-to-s4.md) | RAG scenario ladder S0–S4 | Accepted |
| [0011](0011-knowledge-system-artifacts-snapshots-and-materialization.md) | Knowledge artefacts, snapshots, materialization | Accepted |

## Related

- Diagrams and package maps: [../architecture/README.md](../architecture/README.md)
- Governance: [../development/documentation-guidelines.md](../development/documentation-guidelines.md)
