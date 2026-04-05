# RAG / chat JSON — audit for micro-benchmarks

This file is the **source of truth** for what the Python micro-benchmarks can parse from HTTP responses.

## Legacy `GET {legacy}/query`

**Response shape:** `ApiResponse<QuerySuccessPayload>` → JSON:

- `success` (boolean)
- `data` (object, when `success`): `answer`, `queryType`, `usedTool`, `toolUsed`
- `error` (when `!success`)

**Backend types:** [`QuerySuccessPayload`](../../rag-service/src/main/java/com/uniovi/rag/interfaces/rest/support/dto/QuerySuccessPayload.java), [`QueryResponse`](../../rag-service/src/main/java/com/uniovi/rag/application/model/QueryResponse.java).

**Tokens / model id:** The legacy JSON **does not** include `prompt_tokens`, `completion_tokens`, or a stable `model_id` field. Optional query param `chatModel` selects the Ollama chat model for the request; the benchmark report records this param when present.

**Estimation policy (per product decision):** micro-benchmarks compute **estimated** token counts from text length (`answer` + question heuristic), never from external billing APIs. Reports mark `estimated: true`.

## Product chat (async job)

**Submit:** `POST {product}/conversations/{conversationId}/messages` → HTTP 202 + `jobId`.

**Poll:** `GET {product}/lab/jobs/{jobId}` → `AsyncTaskStatusDto` with `result` map.

**Chat handler result keys** (success): `answer`, `queryType`, `sources`, `pipelineSteps`, `phase` — see [`ChatMessageJobHandler`](../../rag-service/src/main/java/com/uniovi/rag/service/async/chat/ChatMessageJobHandler.java).

**Tokens / model id:** Same as legacy — no token fields in `result`. Optional `llmModel` on `PostMessageRequest` if the client sends it. Assistant message persistence may store `llmModel` / `durationMs` in DB metadata — **not** required for these benchmarks.

## Project RAG flags (scenarios)

Effective feature flags for **project-scoped** chat are applied via `PUT {product}/config/project/{projectId}` with keys allowed by [`RagConfigValueSanitizer.ALLOWED_KEYS`](../../rag-service/src/main/java/com/uniovi/rag/service/config/RagConfigValueSanitizer.java) (e.g. `useRetrieval`, `nerEnabled`, `toolsEnabled`, `reasoningEnabled`, `topK`).

**Legacy `GET /query`** uses the **non–project-scoped** pipeline (`configResolver.resolve(null, null, null)`); toggling project config does **not** change legacy behaviour. Scenarios that require `useRetrieval` / NER / tools / reasoning / top-k must use **`transport: product_chat`** in the scenario YAML (plus `BENCHMARK_*` env vars).
