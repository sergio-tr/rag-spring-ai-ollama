# System context

Clients use a **browser** with the **Next.js** product UI. Traffic may pass through **Nginx** (production-style Compose) or hit **Spring** and optional **webapp** ports directly during development. The backend persists in **PostgreSQL (pgvector)**, generates embeddings and chat through **Ollama**, and calls **classifier-service** over HTTP for lab and query-type workflows.

## Diagrams

| Focus | Source |
| ------- | -------- |
| End-to-end context with optional observability and logs | [context-level.mmd](context-level.mmd) |
| Protocol-level wiring browser → edge → apps → data | [service-runtime-integrations.mmd](service-runtime-integrations.mmd) |
| Where Ollama runs (host vs Docker vs remote) | [ollama-topology.mmd](ollama-topology.mmd) |
| Ollama variants in Compose (GPU container, remote, dev overlays) | [ollama-compose-matrix.mmd](ollama-compose-matrix.mmd) |
| Telemetry and dashboards | [observability-pipeline.mmd](observability-pipeline.mmd) |
| When OTLP is active in Spring | [observability-states.mmd](observability-states.mmd) |

Export for documentation: [diagram-export.md](diagram-export.md).

## Without repeating READMEs

- **Compose merge rules and scripts:** [../../docker/README.md](../../docker/README.md), [../../docker/scripts/README.md](../../docker/scripts/README.md)
- **OTLP ports and Grafana:** [../../observability/README.md](../../observability/README.md)
- **Ollama GPU image:** [../../ollama/README.md](../../ollama/README.md)
- **Classifier API:** [../../classifier-service/README.md](../../classifier-service/README.md)

## Related

- [deployment-model.md](deployment-model.md) - deployment patterns
- [integration-flows.md](integration-flows.md) - auth and RAG integration
- Hub: [../README.md](../README.md)
