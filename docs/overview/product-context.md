# Product context

This monorepo implements a **RAG (Retrieval-Augmented Generation)** product: Spring Boot provides HTTP APIs, document storage, and orchestration; **PostgreSQL + pgvector** stores vectors and metadata; **Ollama** serves embedding and chat models; a **classifier-service** (FastAPI) predicts query types to route tools and prompts.

## In scope (repository)

- Multi-tenant-style **product API** (`RAG_API_PRODUCT_BASE_PATH` / `NEXT_PUBLIC_RAG_API_PREFIX`) alongside a separate **legacy RAG query** surface (`RAG_API_LEGACY_BASE_PATH`); defaults are defined in Spring configuration and `webapp/.env.example`.
- Optional **observability** (OpenTelemetry, Jaeger, Prometheus, Grafana) when Compose profiles include it.
- **Webapp** (Next.js) for authenticated product workflows; static and runtime configuration must stay aligned with Spring path properties.

## Out of scope for prose “truth”

- Exact behaviour is defined by **code**, **Flyway migrations**, **OpenAPI** (`/v3/api-docs`), and **CI**. This file is orientation only.

## Where to read next

- Folder index: [README.md](README.md)
- Thesis / degree **minimum scope** (official): [thesis-scope.md](thesis-scope.md) (includes **deploy path**: GitHub Actions → VM → Docker Compose — see [../operations/runbook-docker-vm.md](../operations/runbook-docker-vm.md))
- Module map and links: [../README.md](../README.md)
- Architecture diagrams: [../architecture/README.md](../architecture/README.md)
- Backend operations: [../../rag-service/README.md](../../rag-service/README.md)
- Docker modes: [../../docker/README.md](../../docker/README.md)
