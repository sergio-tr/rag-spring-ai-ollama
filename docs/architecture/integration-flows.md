# Integration flows (conceptual)

## Webapp and reverse proxy

How the browser hits Spring vs Next by path: [webapp-edge-routing.mmd](webapp-edge-routing.mmd). How public API URL is baked at build vs listen port at runtime: [webapp-config-layers.mmd](webapp-config-layers.mmd).

## Authentication

The product API expects **JWT**-based auth for protected routes; public routes include health, auth login/register, OpenAPI (non-prod), and any explicitly configured permit-all paths. Visual overview: [security-api-boundaries.mmd](security-api-boundaries.mmd).

**Detail:** Spring configuration and endpoints - [../../rag-service/README.md](../../rag-service/README.md); web client behaviour - [../../webapp/README.md](../../webapp/README.md).

## RAG query path

Orchestration from the product API through configuration resolution and `RagExecutionOrchestrator` to **Ollama** and optional **classifier-service**: [rag-request-flow.mmd](rag-request-flow.mmd).

**Detail:** Package map - [BACKEND_PACKAGES.md](BACKEND_PACKAGES.md); classifier - [../../classifier-service/README.md](../../classifier-service/README.md).

## Chat (async job)

User messages: **`POST {product}/conversations/{id}/messages`** → **HTTP 202** + `CHAT_MESSAGE` async task. Progress and streamed answer text: poll **`GET {product}/lab/jobs/{id}`** or SSE **`GET …/lab/jobs/{id}/events`** (same job infrastructure as Lab). Implementation: `MessageStreamController`, `ChatMessageApplicationService`, `ChatMessageJobHandler`.

**Detail:** [../../rag-service/README.md](../../rag-service/README.md), [../../webapp/README.md](../../webapp/README.md).

## Observability of requests

Pipeline: [observability-pipeline.mmd](observability-pipeline.mmd). Profile and endpoint states: [observability-states.mmd](observability-states.mmd). **Ports and env:** [../../observability/README.md](../../observability/README.md).
