# Conceptual model

## Core notions

- **User** — authenticates to the product API; owns **RAG configuration** and **projects**.
- **Project** — groups **documents** and **conversations**; may override RAG settings.
- **Document** — ingested content for retrieval; stored with embeddings in PostgreSQL / pgvector (see ER for table naming).
- **Conversation / message** — chat history; streaming responses may use SSE from the webapp to the backend.
- **Query** — user question; classified (via **classifier-service**) to steer tools and retrieval; answered using **Ollama** and corpus context.

## Canonical detail

- Entity-relationship diagram and migration alignment: [../architecture/DATA_MODEL.md](../architecture/DATA_MODEL.md)
- Ingestion and retrieval behaviour: [../../rag-service/README.md](../../rag-service/README.md)

This page intentionally avoids field-level duplication of the ER or DTO shapes.
