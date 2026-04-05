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

## Related

- Diagrams and package maps: [../architecture/README.md](../architecture/README.md)
- Governance: [../development/documentation-guidelines.md](../development/documentation-guidelines.md)
