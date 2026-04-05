# Microphase template

Copy the following skeleton for microphases **after** 0.1. Replace placeholders. Keep **one microphase = one atomic PR into `dev`** unless an ADR documents an exception.

---

## Title

**Microphase X.Y — &lt;short name&gt;** (`&lt;slug&gt;`)

## Objective

&lt;One paragraph: what outcome is frozen or delivered.&gt;

## Scope

- &lt;Bullet list of in-scope work.&gt;

## Out of scope

- &lt;Bullet list.&gt;

## Architecture touchpoints

Link to frozen docs that this microphase **must** respect:

- [target-architecture.md](../architecture/target-architecture.md) — &lt;subsections if needed&gt;
- [rag-runtime-architecture.md](../architecture/rag-runtime-architecture.md) — …
- [configuration-resolution-model.md](../architecture/configuration-resolution-model.md) — …
- [knowledge-system-model.md](../architecture/knowledge-system-model.md) — …
- [implementation-roadmap.md](../architecture/implementation-roadmap.md) — &lt;which canonical block(s) 1–13&gt;

## ADRs touched

- &lt;List ADR numbers to create or update; none if N/A&gt;

## Branch and PR policy

- **Working branch:** `&lt;convention&gt;/microphase-X.Y-&lt;slug&gt;`
- **Merge target:** `dev` (single atomic PR by default)
- **Promotion to main/master:** not part of this microphase’s Definition of Done

## Risks

| Risk | Mitigation |
|------|------------|
| … | … |

## Definition of Done (architectural)

- &lt;Verifiable outcomes; for doc microphases: files exist, English technical prose, no contradiction with listed ADRs/docs.&gt;
- **PR merged into `dev`** with full package.

---

## Related

- [branch-pr-strategy.md](branch-pr-strategy.md)
- [cursor-workflow.md](cursor-workflow.md)
