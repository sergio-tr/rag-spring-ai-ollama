# Deployment model (conceptual)

## What the diagrams cover

| Topic | Diagram |
| ------- | --------- |
| Logical stacking of Compose files | [compose-overlays.mmd](compose-overlays.mmd) |
| Dev / Ollama / telemetry / prod groupings | [compose-overlay-families.mmd](compose-overlay-families.mmd) |
| Service → repo folder for `build:` contexts | [docker-build-contexts.mmd](docker-build-contexts.mmd) |
| Durable named volumes | [persistence-volumes.mmd](persistence-volumes.mmd) |
| Containers and optional obs/logs/infra on one host | [deployment-compose.mmd](deployment-compose.mmd) |
| Developer hybrid vs all-in-Docker vs prod-local | [deployment-modes.mmd](deployment-modes.mmd) |
| Inter-service calls at runtime | [service-runtime-integrations.mmd](service-runtime-integrations.mmd) |

For **export** to PNG/SVG, see [diagram-export.md](diagram-export.md).

## Environments (narrative)

- **Developer hybrid:** parts of the stack in Docker (often Postgres) while **Spring** and/or **Next.js** run on the host — fast feedback, fewer images.
- **Full Compose:** backend-dev, webapp, classifier, optional Ollama, observability, Loki stack, node-exporter — driven by `docker/scripts` flags and compose overlays under `docker/`.
- **Prod-local:** reverse proxy and hardened exposure; still a **local** pattern for integration testing, not a cloud reference architecture.

Exact **flags**, **env files**, and **profiles** live in module documentation:

- [../../docker/README.md](../../docker/README.md)
- [../../docker/scripts/README.md](../../docker/scripts/README.md)

## Ollama

- Overview (mutually exclusive placement): [ollama-topology.mmd](ollama-topology.mmd)
- Compose files and defaults (`host.docker.internal`, `http://ollama:11434`, remote overlays, `compose.gpu.yml`): [ollama-compose-matrix.mmd](ollama-compose-matrix.mmd)

Operational detail:

- [../../ollama/README.md](../../ollama/README.md)
- [../../rag-service/README.md](../../rag-service/README.md) (`SPRING_AI_OLLAMA_BASE_URL`)

## Observability

- Data path: [observability-pipeline.mmd](observability-pipeline.mmd)
- **When** OTLP is enabled vs default `docker` profile: [observability-states.mmd](observability-states.mmd)

Configuration and ports:

- [../../observability/README.md](../../observability/README.md)

## Web application at the edge

- Path routing at Nginx: [webapp-edge-routing.mmd](webapp-edge-routing.mmd)
- Build-time vs runtime configuration: [webapp-config-layers.mmd](webapp-config-layers.mmd)

## Images and CI

High-level release notes: [../operations/release-and-deploy.md](../operations/release-and-deploy.md).

## Related

- [system-context.md](system-context.md)
- [../operations/environments.md](../operations/environments.md)
