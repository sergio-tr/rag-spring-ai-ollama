# Branch and pull request strategy

**Purpose:** Prescriptive rules for integrating **microphase** work, especially documentation and ADR packages that define the **target architecture**.

## Integration branch

- **`dev`** is the **repository integration branch**. Every microphase merges **first** into `dev` via pull request.
- **Promotion to `main` / `master`** (or production/release branches) is **out of scope** for individual microphases. It happens for **stabilized batches** under the repository **release policy**, not per microphase.

## One microphase = one atomic PR (default)

- Each **microphase** delivers a coherent package (e.g. architecture docs + ADRs + index updates + minimal module README links).
- Default: **one pull request** per microphase, merged into **`dev`**, **atomically** (reviewers approve the **whole** package).
- **Internal commit groups** are allowed (e.g. docs → ADRs → README links) but must **not** be split across separate merges for the same microphase unless governance explicitly allows an exception via ADR.

## Microphase 0.1 (architecture freeze)

- **Working branch name (recommended):** `docs/microphase-0.1-architecture-freeze` (or equivalent; single purpose).
- **Target of merge:** `dev` only.
- **Must not** include application feature code mixed into the same PR.

## Review requirements

- Changes under `docs/architecture/*` and `docs/adr/*` require review for:
  - Consistency with [target-architecture.md](../architecture/target-architecture.md), [rag-runtime-architecture.md](../architecture/rag-runtime-architecture.md), [configuration-resolution-model.md](../architecture/configuration-resolution-model.md), [knowledge-system-model.md](../architecture/knowledge-system-model.md), and [implementation-roadmap.md](../architecture/implementation-roadmap.md).
  - **Thirteen canonical roadmap blocks** unchanged in name/order unless an ADR says otherwise.
- Vocabulary changes for runtime or configuration pieces must include **synchronized ADR** updates in the **same** PR (0005 / 0007 / 0008 as appropriate).

## Merge policy

- Document whether the team uses **merge commit**, **squash**, or **rebase** on `dev`; pick **one** default and follow it consistently for doc PRs.

## Related

- [cursor-workflow.md](cursor-workflow.md)
- [microphase-template.md](microphase-template.md)
- [documentation-guidelines.md](documentation-guidelines.md)
