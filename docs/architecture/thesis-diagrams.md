# Diagrams for thesis or external documentation

All **source** diagrams live in this directory as `.mmd` files (Mermaid). They are designed to be **exported** to PNG or SVG and embedded in Word, LaTeX, or PDF without duplicating operational detail from module READMEs.

## Principles

- Diagrams show **structure and data flow**. Command lines, env file lists, and port tables stay in [../../docker/README.md](../../docker/README.md), [../../docker/scripts/README.md](../../docker/scripts/README.md), [../../observability/README.md](../../observability/README.md), and service READMEs.
- After changing **topology** (new service, new OTLP hop, new Ollama mode), update the relevant `.mmd` and mention the PR in your thesis change log if applicable.

## Index of diagrams

Suggested grouping for the thesis chapter outline: **context** → **services** → **webapp** → **Docker** → **Ollama** → **observability** → **behaviour**.

| File | Use in document |
| ------ | ----------------- |
| [context-level.mmd](context-level.mmd) | System context optional observability |
| [service-runtime-integrations.mmd](service-runtime-integrations.mmd) | Inter-service protocols |
| [webapp-edge-routing.mmd](webapp-edge-routing.mmd) | Nginx path split |
| [webapp-config-layers.mmd](webapp-config-layers.mmd) | Next public env vs runtime port |
| [compose-overlays.mmd](compose-overlays.mmd) | Compose layering from base |
| [compose-overlay-families.mmd](compose-overlay-families.mmd) | Dev Ollama obs prod families |
| [docker-build-contexts.mmd](docker-build-contexts.mmd) | Image build paths |
| [deployment-compose.mmd](deployment-compose.mmd) | Full host topology merged |
| [persistence-volumes.mmd](persistence-volumes.mmd) | Named volumes |
| [deployment-modes.mmd](deployment-modes.mmd) | Hybrid vs full vs prod-local |
| [ollama-topology.mmd](ollama-topology.mmd) | Ollama placement overview |
| [ollama-compose-matrix.mmd](ollama-compose-matrix.mmd) | Ollama compose variants |
| [observability-pipeline.mmd](observability-pipeline.mmd) | Traces metrics logs Grafana |
| [observability-states.mmd](observability-states.mmd) | When OTLP is active |
| [rag-request-flow.mmd](rag-request-flow.mmd) | RAG path Spring |
| [backend-logical-layers.mmd](backend-logical-layers.mmd) | Backend package families application domain infrastructure |
| [security-api-boundaries.mmd](security-api-boundaries.mmd) | Public JWT admin |

## Export with Mermaid CLI

Install once (Node/npm):

```bash
npm i -g @mermaid-js/mermaid-cli
```

From `docs/architecture/`:

```bash
mmdc -i context-level.mmd -o export/context-level.png -b transparent
mmdc -i context-level.mmd -o export/context-level.svg
```

Create `export/` for build artifacts. The repository **ignores** `docs/architecture/export/` so thesis binaries stay local unless you choose otherwise.

### Batch (POSIX shell)

```bash
mkdir -p export
for f in *.mmd; do
  base="${f%.mmd}"
  mmdc -i "$f" -o "export/${base}.png"
done
```

## Export from VS Code / GitHub

- Preview `.mmd` in a Markdown code fence (`mermaid`) for quick reviews.
- For publication quality, prefer **mmdc** for consistent size and fonts.

## Captions

Suggested pattern for thesis: **Figure N —** short title; one sentence in the body referencing this repo path (`docs/architecture/<file>.mmd`) for reproducibility.

## Related

- Architecture index: [README.md](README.md)
- Documentation governance: [../development/documentation-guidelines.md](../development/documentation-guidelines.md)
