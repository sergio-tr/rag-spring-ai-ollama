# Architecture (`docs/architecture/`)

**Conceptual** architecture: Mermaid **sources** (`.mmd`), navigation maps for packages and domain, and links to module READMEs for operational detail. Behaviour is still defined by **code**, **Flyway**, and **CI**.

## Thesis and publication

- **[thesis-diagrams.md](thesis-diagrams.md)** — how to export `.mmd` to PNG/SVG with `mmdc`, batch tips, caption pattern.
- Diagram sources are plain text: version them in git; generate bitmaps locally into `export/` (ignored) for Word/LaTeX.

## Diagram catalogue

### System and services

| Diagram | File | What it shows |
| --- | --- | --- |
| System context | [context-level.mmd](context-level.mmd) | Users, webapp, proxy, Spring, classifier, Postgres, Ollama, optional OTEL/Loki/Grafana |
| Service integrations | [service-runtime-integrations.mmd](service-runtime-integrations.mmd) | Who calls whom JDBC HTTP SSE between browser edge apps data LLM |

### Web application

| Diagram | File | What it shows |
| --- | --- | --- |
| Nginx path routing | [webapp-edge-routing.mmd](webapp-edge-routing.mmd) | Legacy vs product API prefixes, `/actuator`, vs default UI to Next.js |
| Build vs runtime config | [webapp-config-layers.mmd](webapp-config-layers.mmd) | `NEXT_PUBLIC_*` at image build vs `PORT` and `webapp/.env` |

### Docker and Compose

| Diagram | File | What it shows |
| --- | --- | --- |
| Overlay tree from base | [compose-overlays.mmd](compose-overlays.mmd) | Files under `docker/` that layer on `docker-compose.yml` |
| Overlay families | [compose-overlay-families.mmd](compose-overlay-families.mmd) | Dev vs Ollama/GPU vs telemetry vs prod groupings |
| Image build contexts | [docker-build-contexts.mmd](docker-build-contexts.mmd) | Service image sources mapping to repo folders |
| Container topology | [deployment-compose.mmd](deployment-compose.mmd) | One plausible merged stack apps data obs logs infra |
| Named volumes | [persistence-volumes.mmd](persistence-volumes.mmd) | `postgres_data` and `ollama_data` durability |
| Execution modes | [deployment-modes.mmd](deployment-modes.mmd) | Hybrid dev vs full Docker vs prod-local conceptual |

### Ollama

| Diagram | File | What it shows |
| --- | --- | --- |
| Placement | [ollama-topology.mmd](ollama-topology.mmd) | Host vs container vs remote exclusive paths |
| Compose matrix | [ollama-compose-matrix.mmd](ollama-compose-matrix.mmd) | Host default GPU service remote dev overlays `compose.gpu.yml` |

### Observability

| Diagram | File | What it shows |
| --- | --- | --- |
| Data pipeline | [observability-pipeline.mmd](observability-pipeline.mmd) | OTLP collector Jaeger Prometheus Grafana Loki |
| Enablement states | [observability-states.mmd](observability-states.mmd) | Default docker profile vs `compose.obs` vs `rag-dev-obs` |

### Application behaviour

| Diagram | File | What it shows |
| --- | --- | --- |
| RAG flow | [rag-request-flow.mmd](rag-request-flow.mmd) | Product vs legacy config pipeline Ollama classifier |
| Security | [security-api-boundaries.mmd](security-api-boundaries.mmd) | Public vs JWT vs admin indicative |

## Narrative pages (short, link out)

| File | Role |
| --- | --- |
| [target-architecture.md](target-architecture.md) | Target subsystems, global rules, thesis domain pointer |
| [rag-runtime-architecture.md](rag-runtime-architecture.md) | Canonical RAG runtime vocabulary (orchestration, pipelines, judges) |
| [configuration-resolution-model.md](configuration-resolution-model.md) | Capabilities, resolution, snapshots, prompt composition |
| [knowledge-system-model.md](knowledge-system-model.md) | Workspace documents, artefacts, snapshots, materialization |
| [implementation-roadmap.md](implementation-roadmap.md) | Canonical implementation blocks (1–13) after freeze |
| [system-context.md](system-context.md) | Text wrapper around context diagrams |
| [deployment-model.md](deployment-model.md) | Deployment concepts + links to `docker/` |
| [integration-flows.md](integration-flows.md) | Auth, RAG, SSE at integration level |
| [references.md](references.md) | Topic → canonical README |

## Package and module maps (navigation)

| File | Role |
| --- | --- |
| [BACKEND_PACKAGES.md](BACKEND_PACKAGES.md) | `com.uniovi.rag.*` one-liner map |
| [FRONTEND_MODULES.md](FRONTEND_MODULES.md) | Next.js areas |
| [DATA_MODEL.md](DATA_MODEL.md) | ER summary aligned with Flyway |

## How to render Mermaid

- **GitHub / VS Code:** open `.mmd` or paste into a fenced `mermaid` block.
- **Print quality:** `@mermaid-js/mermaid-cli` — see [thesis-diagrams.md](thesis-diagrams.md).

## Related

- Documentation hub: [../README.md](../README.md)
- ADR index: [../adr/README.md](../adr/README.md)
- Docker operations: [../../docker/README.md](../../docker/README.md)
- Observability configuration: [../../observability/README.md](../../observability/README.md)
- Ollama image layout: [../../ollama/README.md](../../ollama/README.md)
