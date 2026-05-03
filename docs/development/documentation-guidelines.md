# Documentation governance

This repository separates **global, conceptual documentation** (`docs/`) from **module-specific how-to** (README files next to code). Follow these rules for every change.

## Layers

| Layer | Location | Content |
| ------- | ---------- | --------- |
| Global | `docs/` | What the system is, why it is shaped this way, architecture at a high level, domain overview, operational concepts, ADRs. **Summaries and links only.** |
| Module | `*/README.md` (e.g. `rag-service/`, `webapp/`, `docker/`) | How to build, run, configure, test **that** module: commands, env vars, ports, troubleshooting. |

## DO

- Keep **one canonical source** per topic. If it is operational detail for Docker, it belongs in `docker/README.md` or `docker/scripts/README.md`, not duplicated in `docs/`.
- In `docs/`, prefer **short narrative + table of links** to module READMEs, OpenAPI, or diagrams.
- When you change a **public contract** (API path, auth rule, required env var, Compose topology), update the **module README** first; update `docs/` only if the **system story** (boundaries, actors, deployment model) changes.
- Add an **ADR** (`docs/adr/NNNN-title.md`) when making a **structural** decision that others must understand months later.

## DO NOT

- Copy long command blocks, flag matrices, or `.env` key lists from module READMEs into `docs/`.
- Place draft or exploratory analysis in official `docs/` paths without maintainer agreement — use issues or team workflow for work-in-progress notes.

## Agents and contributors

- Before adding a **new** `.md` under `docs/` (except clearly agreed maintenance), confirm with maintainers.
- After code changes, update documentation in the **same PR** when behaviour visible to operators or integrators changes.
- For “where should this paragraph go?”: if it explains **how to run X**, use the module README; if it explains **what role X plays in the system**, use `docs/` with a link to the module.

## Normative destinations (what goes where)

| Destination | Purpose |
| ------------- | --------- |
| **Repository root [`README.md`](../../README.md)** | First contact: what the repo is, minimal quick start, badges, **one** pointer to [`docs/README.md`](../README.md). Avoid duplicating long CI matrices if the hub already lists them—link instead. |
| **`docs/architecture/`** | System shape: context diagrams, deployment **concepts**, data model summary, integration narratives, diagram sources (`*.mmd`). Operational command matrices stay in **`docker/`** READMEs—link, do not copy. |
| **`docs/adr/`** | Structural decisions with long-lived consequences (security boundaries, identity, compatibility model, major data conventions). Format: Context / Decision / Consequences; index in [`docs/adr/README.md`](../adr/README.md). |
| **`docs/` elsewhere** (`domain`, `overview`, `operations`, `testing`, …) | Topic hubs: short prose + tables of links to canonical module READMEs and ADRs. |

### Must not appear in authoritative `docs/` paths

- The Unicode section sign for cross-references—use Markdown links and stable heading anchors instead ([`DATA_MODEL.md`](../architecture/DATA_MODEL.md) uses ids such as `#dm-s6-1`).
- Claims of **strong SaaS-grade multi-tenant isolation** unless explicitly qualified—see [ADR 0002](../adr/0002-multitenancy-assumption.md).
- Undocumented **branch or PR naming policies** presented as governance unless the team publishes them outside this repo’s normative docs.

See also: [documentation inventory](documentation-inventory.md).

---

## Naming and editorial style

- **Filenames:** `kebab-case` for Markdown (e.g. `documentation-guidelines.md`). Diagram sources in `docs/architecture/`: descriptive `*.mmd` (`rag-request-flow.mmd`).
- **Titles:** Use clear nouns (“Data model”, “Deploy workflow”), not sprint or wave codes in official hub paths.
- **Cross-references:** Prefer `[descriptive label](path.md#anchor)`; add explicit HTML anchors in long reference pages where headings might change wording.
- **Enhancement / archive:** Material under [`docs/enhancement/`](../enhancement/) is **non-normative**—see [`docs/enhancement/README.md`](../enhancement/README.md).

---

## Documentation governance outcomes

Ongoing expectations: documentation inventory kept current ([`documentation-inventory.md`](documentation-inventory.md)); ADR index and [ADR 0002](../adr/0002-multitenancy-assumption.md) terminology aligned with implementation; no section-sign cross-references in normative `docs/` (except archival [`docs/enhancement/`](../enhancement/)); [`docs/enhancement/README.md`](../enhancement/README.md) framing for non-canonical notes; Mermaid sources validated against [`docs/architecture/README.md`](../architecture/README.md).

---

## Related

- Documentation hub: [../README.md](../README.md)
- Governance strategy: [documentation-governance-strategy.md](documentation-governance-strategy.md) (Section 8 — canonical layers and forbidden content)
- Documentation inventory: [documentation-inventory.md](documentation-inventory.md)
