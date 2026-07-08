# Product context

This monorepo implements a **RAG (Retrieval-Augmented Generation)** product: Spring Boot provides HTTP APIs, document storage, and orchestration; **PostgreSQL + pgvector** stores vectors and metadata; **Ollama** serves embedding and chat models; a **classifier-service** (FastAPI) predicts query types to route tools and prompts.

**Data isolation (normative):** the product is **multi-user** with **project-scoped** access in a **single** PostgreSQL database (application rules and foreign keys). It is **not** SaaS-grade multi-tenancy (dedicated DB/RLS per external tenant) unless a future ADR says so - see [ADR 0002 - user/project isolation](../adr/0002-multitenancy-assumption.md).

## In scope (repository)

- A single **product** HTTP API namespace (`RAG_API_PRODUCT_BASE_PATH` / `NEXT_PUBLIC_RAG_API_PREFIX`); defaults are defined in Spring configuration and `webapp/.env.example`. (The word *product* here refers to the **URL path family**, not a separate database tenant.)
- Optional **observability** (OpenTelemetry, Jaeger, Prometheus, Grafana) when Compose profiles include it.
- **Webapp** (Next.js) for authenticated product workflows; static and runtime configuration must stay aligned with Spring path properties.

## Out of scope for prose “truth”

- Exact behaviour is defined by **code**, **Flyway migrations**, **OpenAPI** (`/v3/api-docs`), and **CI**. This file is orientation only.

## Where to read next

- Folder index: [README.md](README.md)
- **Minimum scope** (official): [minimum-scope.md](minimum-scope.md) (includes **deploy path**: GitHub Actions → VM → Docker Compose - see [../operations/runbook-docker-vm.md](../operations/runbook-docker-vm.md))
- Module map and links: [../README.md](../README.md)
- Architecture diagrams: [../architecture/README.md](../architecture/README.md)
- Backend operations: [../../rag-service/README.md](../../rag-service/README.md)
- Docker modes: [../../docker/README.md](../../docker/README.md)
