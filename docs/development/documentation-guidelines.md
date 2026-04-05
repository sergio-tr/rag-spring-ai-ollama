# Documentation governance

This repository separates **global, conceptual documentation** (`docs/`) from **module-specific how-to** (README files next to code). Follow these rules for every change.

## Layers

| Layer | Location | Content |
|-------|----------|---------|
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

## Related

- Documentation hub: [../README.md](../README.md)
- Governance strategy: [documentation-governance-strategy.md](documentation-governance-strategy.md)
