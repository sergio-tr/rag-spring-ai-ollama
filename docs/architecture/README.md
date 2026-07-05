# Architecture (`docs/architecture/`)

**Conceptual** architecture: Mermaid **sources** (`.mmd`), navigation maps for packages and domain, and links to module READMEs for operational detail. Behaviour is still defined by **code**, **Flyway**, and **CI**.

## Diagrams and publication

- **[diagram-export.md](diagram-export.md)** - how to export `.mmd` to PNG/SVG with `mmdc`, batch tips, caption pattern.
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
| Nginx path routing | [webapp-edge-routing.mmd](webapp-edge-routing.mmd) | Product API prefix vs `/actuator`, vs default UI to Next.js |
| Build vs runtime config | [webapp-config-layers.mmd](webapp-config-layers.mmd) | `NEXT_PUBLIC_*` at image build vs `PORT` and `webapp/.env` |

### Docker and Compose

**CI vs Compose pins and PR job policy:** [../devops/README.md](../devops/README.md).

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
| RAG flow | [rag-request-flow.mmd](rag-request-flow.mmd) | Resolution, orchestration, query path, Ollama, optional classifier |
| Backend layers | [backend-logical-layers.mmd](backend-logical-layers.mmd) | High-level `com.uniovi.rag` package families (see [BACKEND_PACKAGES.md](BACKEND_PACKAGES.md)) |
| Security | [security-api-boundaries.mmd](security-api-boundaries.mmd) | Public vs JWT vs admin indicative |

## Mermaid maintenance backlog

Review higher-priority diagrams first when runtime or Compose behaviour changes. Prefer **one main message** per file and **node IDs without spaces** (see [Mermaid flowchart syntax](https://mermaid.js.org/syntax/flowchart.html)).

| Priority | Diagrams | Status | Notes |
| --- | --- | --- | --- |
| 1 | [context-level.mmd](context-level.mmd), [deployment-compose.mmd](deployment-compose.mmd) | **Done** (orientation pass) | System context and container topology; matches Linux-first stack narrative. |
| 2 | [service-runtime-integrations.mmd](service-runtime-integrations.mmd), [security-api-boundaries.mmd](security-api-boundaries.mmd) | **Queued** | Revisit on JWT or admin boundary changes. |
| 3 | [rag-request-flow.mmd](rag-request-flow.mmd), [backend-logical-layers.mmd](backend-logical-layers.mmd) | **Done** (2026-05) | Product-only ingress; chat via async `CHAT_MESSAGE` jobs. |
| 4 | [webapp-edge-routing.mmd](webapp-edge-routing.mmd), [docker-build-contexts.mmd](docker-build-contexts.mmd) | **Queued** | Align legends with `NEXT_PUBLIC_*` and Docker contexts. |
| 5 | [compose-overlays.mmd](compose-overlays.mmd), [observability-pipeline.mmd](observability-pipeline.mmd), [ollama-topology.mmd](ollama-topology.mmd) | **Queued** | Revisit on Compose profiles or OTLP ports. |

## Backend refactoring governance

Incremental refactors of `rag-service` (naming, layers, statics, ArchUnit gates, slice template): **[`docs/backend/refactoring-governance.md`](../backend/refactoring-governance.md)** - formal pointer [ADR 0012](../adr/0012-backend-refactoring-governance.md).

## Narrative pages (short, link out)

| File | Role |
| --- | --- |
| [target-architecture.md](target-architecture.md) | Target subsystems, global rules, product domain pointer |
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
| [docs/backend/README.md](../backend/README.md) | Backend norms index: [refactoring-governance.md](../backend/refactoring-governance.md) |
| [FRONTEND_MODULES.md](FRONTEND_MODULES.md) | Next.js areas |
| [DATA_MODEL.md](DATA_MODEL.md) | ER summary aligned with Flyway |

## How to render Mermaid

- **GitHub / VS Code:** open `.mmd` or paste into a fenced `mermaid` block.
- **Print quality:** `@mermaid-js/mermaid-cli` - see [diagram-export.md](diagram-export.md).

## Related

- Documentation hub: [../README.md](../README.md)
- Quality baseline (tests, exclusions, Sonar): [../quality/README.md](../quality/README.md)
- ADR index: [../adr/README.md](../adr/README.md)
- Docker operations: [../../docker/README.md](../../docker/README.md)
- Observability configuration: [../../observability/README.md](../../observability/README.md)
- Ollama image layout: [../../ollama/README.md](../../ollama/README.md)
