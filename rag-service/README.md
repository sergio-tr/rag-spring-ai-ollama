# RAG Spring AI Ollama

RAG (Retrieval-Augmented Generation) system with Spring Boot, Spring AI, Ollama and PostgreSQL (PgVector). Includes a query-type classifier exposed as an HTTP service (classifier-service).

**Target architecture (frozen model):** [RAG runtime](../docs/architecture/rag-runtime-architecture.md), [configuration & resolution](../docs/architecture/configuration-resolution-model.md).

**Query execution:** chat and legacy query entry points use `ProcessQueryService` / `SimpleProcessQueryService` as a thin façade over `ExecutionContextFactory` → `RagExecutionOrchestrator`. After the factory builds `ExecutionContext`, the runtime order is frozen: **P11 clarification pre-processing** (load/merge pending clarification into `preMemoryPlanningInputText`) → **P12 memory stage** (bounded history slice + at most one LLM-backed condensation call with deterministic fallback; sets final `effectivePlanningInputText`) → the orchestrator runs **`QueryUnderstandingPipeline` once** on that final `effectivePlanningInputText`, then **P11 clarification policy**. On `ASKED_*`, the turn stops with `workflowName=clarification` and **does not** run adaptive routing or any downstream execution families. Otherwise the orchestrator runs **P13 adaptive routing** (when `rag.features.adaptive-routing-enabled` / `adaptiveRoutingEnabled` is true) to select exactly one primary execution-family route (`DIRECT_WORKFLOW_ROUTE`, `RETRIEVAL_WORKFLOW_ROUTE`, `DETERMINISTIC_TOOL_ROUTE`, `FUNCTION_CALLING_ROUTE`, `ADVISOR_ROUTE`), enforces route-family exclusivity, and applies at most one deterministic workflow fallback when a selected non-workflow route does not terminate the turn. When `rag.features.judge-enabled` / `judgeEnabled` is true, the orchestrator then runs **P14 judge** after the selected route family has produced a candidate answer (bounded evaluation + at most one repair attempt, no upstream stage re-runs, no route-family changes). `WorkflowSelector` is invoked only inside workflow-capable routes (and after successful advisor execution to choose a retrieval-capable workflow). When adaptive routing is disabled, the orchestrator derives a single compatibility workflow route and executes workflow only. Unsupported combinations return **422** with `UNSUPPORTED_RUNTIME_CONFIGURATION`; missing ACTIVE knowledge snapshots for knowledge workflows return `KNOWLEDGE_SNAPSHOT_UNAVAILABLE`. Successful runs log `workflow`, snapshot ids, and `correlationId` at INFO. **P15** persists the finalized `ExecutionTrace` as one best-effort `runtime_execution_trace` row after the turn completes (no effect on response on failure). **P16** adds minimal read-only query surfaces for those persisted traces (summary list per conversation + detail by trace id / message id), owner-scoped and layered through a single application service. **P17** adds minimal read-only export endpoints layered over a single application service that generates bounded, deterministic ZIP exports of persisted traces (single-trace or per-conversation bundle), without adding replay or analytics/reporting semantics. **P18** adds an internal-only bounded replay core (`RuntimeTraceReplayService` under `application.service.runtime.tracereplay`): reads traces via `RuntimeTraceQueryService` only, pins `resolved_config_snapshot_id` from the persisted trace row, performs no writes, and does not invoke the orchestrator or `ProcessQueryService` (no REST surface in P18). **P19** adds an internal-only replay comparison core (`RuntimeTraceReplayComparisonService` under `application.service.runtime.tracecomparison`): compares one P16 trace detail to one P18 replay result in memory with closed outcomes and bounded fields; it does not add REST endpoints. **P20** adds **two** owner-scoped **GET** routes (`RuntimeTraceReplayComparisonController` → `RuntimeTraceReplayComparisonService` only): replay comparison by trace id or by conversation+message id under `rag.api.product-base-path`; path-only (any query string returns **400**); not-found / inaccessible original → **404** (not **403**); bounded JSON via `interfaces.rest.dto.tracecomparison`; synchronous (no **202**); no batch, export, or second comparison owner. **P21** adds **two** **GET** ZIP download routes (`.../replay-comparison/export`) via `RuntimeTraceReplayComparisonExportController` → **`RuntimeTraceReplayComparisonExportService`** only (`application.service.runtime.tracecomparisonexport`), which calls **`RuntimeTraceReplayComparisonService.compare`** only. Each response is a **2 MiB**-capped ZIP (`manifest.json` + `comparison.json`); larger payloads return **413**. Same query-string and **404** rules as P20; synchronous; **no** P17 trace export coupling. **P22** adds **two** owner-scoped **GET** routes for **standalone** replay (`RuntimeTraceReplayController` → **`RuntimeTraceReplayService`** only): `.../runtime-traces/{traceId}/replay` and `.../conversations/{conversationId}/messages/{messageId}/runtime-trace/replay` under `rag.api.product-base-path`. Path-only (**any query string ⇒ 400**); missing/inaccessible persisted trace for the user (**`NotFoundException`** from the P16 read path inside replay) **⇒ 404**; **200** + bounded JSON for every replay outcome returned by P18 (including **`UNSUPPORTED_*`**), never **403**/**202**. Responses are synchronous and may re-execute the pinned runtime path — treat as an operator/debug surface, not a cheap read. **P23** adds **two** parallel **`GET`** ZIP downloads (`.../replay/export`) via `RuntimeTraceReplayExportController` → **`RuntimeTraceReplayExportService`** only (`application.service.runtime.tracereplayexport`), which calls **`RuntimeTraceReplayService.replay` once** and packages **`manifest.json`** + **`replay.json`** (same bounded projection as P22). **2 MiB** max ZIP (**413** if the assembled archive exceeds the cap — rare if P22 string caps hold); same path-only **400**/**404** rules as P22; **no** P17 persisted-trace export or P21 comparison coupling. **P24** adds an internal-only **`RuntimeTraceReplayComparisonBatchService`** (`application.service.runtime.tracecomparisonbatch`) that runs **up to 50** sequential P19 **`compare`** calls per batch (**`BY_TRACE_IDS`** or **`BY_CONVERSATION`** selection via P16 listing) — **no** HTTP, **no** persistence, **no** export; useful for tooling; worst case latency scales with the number of compares. **P25** adds **two** **`POST`** batch routes under `rag.api.product-base-path` (`RuntimeTraceReplayComparisonBatchController` → **`RuntimeTraceReplayComparisonBatchService#execute` only**): `.../runtime-traces/replay-comparisons/batch` (body: `traceIds` only) and `.../conversations/{conversationId}/runtime-traces/replay-comparisons/batch` (optional `createdAtFrom` / `createdAtTo` / `workflowName`). Strict JSON (unknown keys ⇒ **400**); **`NOT_ATTEMPTED`** ⇒ **400**; bounded DTOs in `interfaces.rest.dto.tracecomparisonbatch` (items **≤50**, counters-only).

**Advanced retrieval (dense workflows):** When P10 advisor packing is **not** used for a turn, `DocumentDenseRagWorkflow`, `ChunkDenseRagWorkflow`, and `ChunkDenseMetadataWorkflow` call `AdvancedRetrievalPipeline` only. When `advisorPackedContextSet` is present, they use that packed context and **do not** call `AdvancedRetrievalPipeline` again. Retrieval uses `QueryPlan.rewrittenQueryText`, supports dense-only or hybrid (dense + PostgreSQL FTS with RRF fusion), then deterministic rerank, filter, and extractive compression. Optional JSON override: `advancedRetrievalMaxContextChars` (default `24000`, clamped with `naiveFullCorpusMaxChars` in the same config merge). Traces: substages `retrieval_*` plus `ExecutionTrace.retrievalDiagnostics`; P10 adds `advisor_policy`, `advisor_retrieval`, `advisor_context_pack` stages and advisor summary fields on `ExecutionTrace`.

**Layering:** Product REST controllers live under `com.uniovi.rag.interfaces.rest` and delegate to **application services** (`com.uniovi.rag.application..`) for persistence; JPA and repositories stay in `infrastructure.persistence`. Legacy RAG adapters (`interfaces.rest.legacy`, base path `rag.api.legacy-base-path`) are excluded from ArchUnit rules until refactored. See `src/test/java/com/uniovi/rag/architecture/LayeredArchitectureTest.java`.

## Build and run

### Backend (Spring Boot)

```bash
mvn -B package
java -jar target/rag-spring-ai-ollama-*.jar
```

By default the backend listens on port **9000**. Database, Ollama and classifier service URL are configured via environment variables or `application.properties` (see Key variables).

### Classifier service (query classifier)

See [classifier-service/README.md](../classifier-service/README.md) for running the classifier (FastAPI). Environment variables: `PORT`, `MODEL_PATH`, `LABELS_PATH`.

### Backend in Docker (dev hot-reload)

To exercise a **container** environment similar to production but with the repo bind-mounted and automatic reload (Maven compile + Spring Boot DevTools), use the **`backend-dev`** service — **not** the default `backend` service from `docker-compose.yml`. From the repo root:

```bash
./docker/scripts/up.sh dev --rag --gpu --obs --classifier   # example
```

This starts **`backend-dev`** (`docker/compose.dev.yml` + `Dockerfile.dev`): bind-mount `rag-service/` → `/app`, Maven cache in the `rag_m2_cache` volume. If Ollama runs in the same Compose stack, set `SPRING_AI_OLLAMA_BASE_URL=http://ollama:11434` in `rag-service/.env`. Watcher poll interval: `RAG_DEV_POLL_INTERVAL` (default `2` seconds).

**Faster restarts:** the entrypoint skips **`mvn compile`** on start when `target/classes` already contains `.class` files (e.g. run `./mvnw compile` once on the host, or reuse a previous build). Set **`RAG_DEV_FORCE_INITIAL_COMPILE=1`** in `rag-service/.env` to always compile on startup (old behaviour).

**Startup:** while Ollama downloads models (`gemma3:4b`, `mxbai-embed-large`, etc.), **`/actuator/health/readiness`** may return **503** — that is expected. The Docker **healthcheck** uses **`/actuator/health/liveness`** (JVM is up). **`/api/**`** routes stay blocked until the pull finishes (`OLLAMA_PROVISIONING`). Use readiness when you want to verify the app is ready for traffic: `curl -s http://localhost:9000/actuator/health/readiness`.

### With Docker Compose

Each component has its own `.env`: **db/.env**, **rag-service/.env**, **classifier-service/.env**, **webapp/.env** (the default `docker-compose.yml` includes **webapp**), and **observability/.env** when using observability. Create defaults from the repo root:

```bash
./docker/scripts/create-env-all.sh
./docker/scripts/up.sh prod
```

Canonical compose flags live in [**docker/scripts/up.sh**](../docker/scripts/up.sh) and [**docker/scripts/docker-compose.sh**](../docker/scripts/docker-compose.sh). The file **`rag-service/scripts/up.sh`** only **delegates** to `docker/scripts/up.sh` — do not duplicate `-f` chains here.

The `postgres` and `backend` services load **db/.env** for DB credentials. Port and app defaults are in the compose file. For observability, see [observability/README.md](../observability/README.md); run with `--env-file ../observability/.env` when using `compose.obs.yml`.

## Key variables

| Variable / property | Description | Example / default |
| --- | --- | --- |
| `OLLAMA_BASE_URL` | Ollama URL | `http://localhost:11434` |
| `SPRING_AI_OLLAMA_CHAT_MODEL` / `SPRING_AI_OLLAMA_EMBEDDING_MODEL` | Models used by Spring AI | e.g. `gemma3:4b`, `mxbai-embed-large` |
| `rag.ollama.auto-pull-enabled` | Pull missing models via Ollama HTTP API on startup | `true` (set `false` offline / air-gapped) |
| `rag.ollama.pull-read-timeout-ms` | Per-`pull` timeout (large models) | `1800000` (30 min) |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL (same DB as in db/.env) | `jdbc:postgresql://localhost:5432/vectordb` or `postgres:5432` in Compose |
| `SPRING_DATASOURCE_USERNAME` | DB user (must match db/.env) | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | DB password (must match db/.env) | — |
| `rag.classifier.service.url` | Classifier service URL (backend) | `http://localhost:8000` |
| `RAG_CONFIG_V2_ENABLED` / `rag.config.v2.enabled` | Use `ResolvedRuntimeConfig` resolution in the chat path (aligned with `POST /config/preview`) | `false` |
| `rag.runtime.workflow-schema-version` | Semver of the RAG execution stage graph (Lab/eval reproducibility) | `1.0.0` |
| `rag.runtime.legacy-advisor-with-post-retrieval` | Allow `QuestionAnswerAdvisor` when post-retrieval is on (legacy; not recommended) | `false` |
| `rag.runtime.memory-max-turns` / `rag.runtime.memory-max-chars` | Caps for product “full” conversation memory when injected into prompts | `20` / `8000` |
| `rag.features.function-calling-enabled` | Enables P9 Spring AI function calling for meeting-minutes tools (orchestrated path; deterministic tools still run first) | `false` |
| `rag.features.clarification-enabled` | P11: deterministic clarification loop after query understanding (requires chat `conversation_id` to persist pending JSON) | `false` |
| `rag.features.memory-enabled` | P12: bounded conversational memory stage after P11 clarification pre-processing and before QU | `false` |
| `rag.features.adaptive-routing-enabled` | P13: deterministic adaptive routing stage (route-family gating after clarification, before any execution family) | `false` |
| `rag.features.judge-enabled` | P14: post-answer judge stage after the selected route family produces a candidate answer | `false` |
| `clarificationEnabled` | (RAG config JSON merge) Same flag as above, overridable per layer | inherits feature default |
| `memoryEnabled` | (RAG config JSON merge) Same flag as above, overridable per layer | inherits feature default |
| `adaptiveRoutingEnabled` | (RAG config JSON merge) Same flag as above, overridable per layer | inherits feature default |
| `judgeEnabled` | (RAG config JSON merge) Same flag as above, overridable per layer | inherits feature default |
| `advancedRetrievalMaxContextChars` | (RAG config JSON) Max characters for curated retrieval context after extractive compression | `24000` |
| `rag.evaluation.persistence.enabled` | Persist canonical `evaluation_run` / `evaluation_result` from Lab async handlers | `true` |
| `rag.evaluation.storage-root` | Filesystem root for uploaded evaluation datasets (empty → temp dir) | — |
| `rag.evaluation.max-upload-bytes` | Max size per dataset binary (v1 cap) | `26214400` (25 MB) |
| `rag.account.export-storage-dir` / `RAG_ACCOUNT_EXPORT_STORAGE_DIR` | Filesystem root for GDPR-style account export ZIPs (per-user subfolders) | `${java.io.tmpdir}/rag-account-export` |
| `rag.account.export-ttl-hours` / `RAG_ACCOUNT_EXPORT_TTL_HOURS` | Hours until a READY export expires (scheduler may delete the file) | `24` |
| `rag.account.cleanup-interval-ms` / `RAG_ACCOUNT_CLEANUP_INTERVAL_MS` | Fixed delay between expired-export sweeps | `3600000` (1 h) |

**Product hub (`{product}/me/*`):** canonical JSON stores `GET/PUT …/me/preferences` and `…/me/personalization` (with `schema_version` in DB), `GET …/me/summary`, `GET …/me/documents`, `POST …/me/account/export` and `…/deletion` (HTTP **202** + `async_task`), poll `GET …/me/account/jobs/{id}`, download `GET …/me/account/export/{exportId}/download`. **Admin** defaults for new users: `GET/PUT /api/admin/system-defaults`. Legacy `GET/PUT {product}/config/user` remains but is marked **deprecated** in OpenAPI in favor of `/me/*` where overlaps exist.

**Runtime configuration (`{product}/config/*`, JWT):** `GET …/config/schema`, `POST …/config/preview` (optional `presetId`, `conversationId`, `runtimeOverride`, reindex preview fields), `POST …/config/resolved-snapshots` (persist a resolved snapshot row for Lab reproducibility; returns `id` + `configHash`), `GET …/config/resolved-snapshots/{id}` (owner-only). Canonical persistence layout: [../docs/architecture/DATA_MODEL.md](../docs/architecture/DATA_MODEL.md) §6.1. Presets: `{product}/presets` includes optional `profileRefs` on create/update/list/get.

**Knowledge System (`{product}/projects/{projectId}/knowledge/*`, JWT):** canonical ingest and snapshot APIs for corpus indexing (not RAG query execution). **Ingest** document uploads use `POST …/knowledge/ingest` with `corpusScope` (`PROJECT_SHARED` or `CHAT_LOCAL`) and, for chat-local scope, `conversationId`. Each ingest persists a default `resolved_config_snapshot` row and links new `knowledge_index_snapshot` rows to it (`resolved_config_snapshot_id` + `resolved_config_hash`, see [../docs/architecture/DATA_MODEL.md](../docs/architecture/DATA_MODEL.md) §6.2). **Config-aware rebuild:** `POST …/knowledge/rebuild/preview` (JSON body: `corpusScope`, optional `conversationId`, optional `presetId`, `runtimeOverride`, `touchedProfileTypes`, `correlationId`) returns materialization/chunk/embedding fields, `reindexImpact`, precomputed `reindexDecision`, and preview `configHash` without writing snapshots. `POST …/knowledge/rebuild/execute` accepts the same fields plus optional `explicitResolvedConfigSnapshotId` (pin execute); response includes `resolvedConfigSnapshotId` and, when a pipeline runs, `knowledgeSnapshotId` / `reindexEventId`. **Legacy alias:** `POST …/knowledge/reindex` (query params only) delegates to execute with empty overrides (same scope rules). **Read APIs:** `GET …/knowledge/snapshots`, `GET …/knowledge/snapshots/{snapshotId}` use the same `corpusScope` / optional `conversationId` rules. Listing and deleting **project** documents remain on `GET/DELETE {product}/projects/{projectId}/documents`; use **knowledge** routes for uploads and reindex (not `POST` on `/documents` for new binaries). Conversation overlay uploads may also use `POST {product}/projects/{projectId}/conversations/{conversationId}/documents` (delegates to the same ingestion service). See [../docs/architecture/knowledge-system-model.md](../docs/architecture/knowledge-system-model.md), [../docs/architecture/DATA_MODEL.md](../docs/architecture/DATA_MODEL.md) §6.2.

### Lab benchmarks

Use **product** routes under `{product}/lab` (JWT). Canonical runs live in `evaluation_run` + `evaluation_result`; `async_task` is operational (poll `/lab/jobs/{asyncTaskId}`).

| Goal (TFG / product) | Use | Legacy (not primary SCIENCE evidence) |
| --- | --- | --- |
| LLM judge QA (no retrieval) | `POST /lab/benchmarks/LLM_JUDGE_QA/runs` | — |
| Embedding / retrieval only | `POST /lab/benchmarks/EMBEDDING_RETRIEVAL/runs` | — |
| Full RAG (preset + snapshots) | `POST /lab/benchmarks/RAG_PRESET_END_TO_END/runs` | — |
| Classifier metrics | `POST /lab/benchmarks/CLASSIFIER_METRICS/runs` (multipart) | — |
| Combinatorial feature-flag matrix | — | `GET {legacy}/evaluate/all` → body includes `legacyEvaluationMode: LEGACY_COMBINATORIAL` |

Export: `GET /lab/runs/{id}/export?format=csv` (first line `#META:` + JSON run header, then CSV rows) or `format=json`.

### Configuration layout (two main files + tests)

| File | Role |
| --- | --- |
| `application.properties` | **Application:** RAG/AI/features, DB, actuator defaults, OTLP off by default; profile **`dev`** (DevTools, logs, CORS, JPA `update`) |
| `application-infra.properties` | **Environment / infra:** profile **`docker`** (no OTLP inside container by default), profile **`infra`** (OTLP on, collector URLs, relaxed container health, probes) |
| `application-test.properties` | Test profile only (no real Ollama/OTLP) |

Edit **`application.properties`** for product behaviour; **`application-infra.properties`** for Docker/OTLP/actuator wiring. Override with environment variables (e.g. `SPRING_DATASOURCE_URL`, `OTEL_EXPORTER_OTLP_ENDPOINT`) or extra profiles.

You can use environment variables with placeholders `${VAR_NAME:default}`. Override via `.env` or the environment (e.g. `SPRING_DATASOURCE_URL`, `OLLAMA_BASE_URL`, `RAG_CLASSIFIER_SERVICE_URL`, `OTEL_EXPORTER_OTLP_ENDPOINT`).

## Tests and JaCoCo (`target/site/jacoco/index.html`)

### Replicate CI locally (backend `mvn verify`)

The **Backend (Java)** job in [`.github/workflows/ci.yml`](../.github/workflows/ci.yml) uses:

- Service image **`pgvector/pgvector:0.8.2-pg16-bookworm`**, database **`vectordb`**, user/password **`postgres`**
- JDBC from the job to **`localhost:5432`** (`SPRING_DATASOURCE_*`)
- A **prepare** step implemented by the reusable action [`.github/actions/prepare-postgres-for-rag-tests`](../.github/actions/prepare-postgres-for-rag-tests/action.yml): extensions `vector`, `hstore`, `uuid-ossp` on `vectordb`; database **`testdb`** + [`src/test/resources/test-init.sql`](src/test/resources/test-init.sql) (for JDBC integration tests). The same action is used by Sonar, stack integration, and E2E fullstack jobs so Postgres state matches `mvn verify` locally.
- Env: `RAG_JWT_SECRET` (≥32 chars), `RAG_TEST_USE_TESTCONTAINERS_DATASOURCE=false`, `INTEGRATION_JDBC_URL=jdbc:postgresql://localhost:5432/testdb`
- Command: `./mvnw -B clean verify` from **`rag-service/`** (`clean` avoids stale `target/classes/db/migration` files after renamed Flyway scripts)

**Easiest:** run the helper script (Docker required; uses the same image and SQL as CI):

| Shell | From repository root |
| --- | --- |
| **Bash** (Linux, macOS, WSL, Git Bash) | `./.github/local/ci-like-verify.sh` |

Options: `ci-like-verify.sh --stop-after` removes the container after verify; `--prepare-only` only starts Postgres and applies SQL (then set the env vars yourself and run `./mvnw -B verify`). PowerShell: `-StopAfter`, `-PrepareOnly`.

Default container name: **`rag-ci-postgres`** (override with env `RAG_CI_POSTGRES_CONTAINER`). If **port 5432 is already in use**, stop the other Postgres or change the host port in the script for local experiments.

**Postgres unreachable / Docker half-broken (common on WSL):** full-stack `@SpringBootTest` classes and `DocumentPersistenceJdbcIntegrationTest` use `@EnabledIf` via `TestEnvironment`. They are **skipped** (not failed) when `SPRING_DATASOURCE_URL` is unset, nothing listens on `localhost:5432/vectordb`, and the Docker daemon does not respond to a client ping — so plain `./mvnw verify` without a running CI-style Postgres no longer produces `ApplicationContext` errors. Run `./.github/local/ci-like-verify.sh` or `./.github/local/ci-like-sonar.sh` first so the container and env vars match CI.

- **CI / GitHub Actions:** environment variables override `src/test/resources/application-test.properties`. If **`RAG_JWT_SECRET`** is set (repo or org) to an empty or **too short** value (JWT signing requires at least **32** characters), `JwtService` fails at startup and `@SpringBootTest` classes error with `Failed to load ApplicationContext`. The repo workflows set a dedicated test secret for `mvn verify`. **`SafeTestSecretsApplicationContextInitializer`** (`META-INF/spring.factories` and `META-INF/spring/org.springframework.context.ApplicationContextInitializer.imports` on the test classpath) patches invalid secrets and OTLP URLs and sets `spring.datasource.*` early (GitHub service Postgres vs Testcontainers) so JPA can create `entityManagerFactory`. **`application-test.properties`** also pins JDBC literals as a fallback when org env clears datasource variables. Full-stack smoke tests (`SpringIntoAiApplicationTests`, OpenAPI export, Rag stabilization) also set **`@SpringBootTest(properties = …)`** so `rag.jwt.secret` and OTLP endpoints win over process env even when org defaults set **`SPRING_PROFILES_ACTIVE=prod`** (which used to skip the initializer before the `test` profile was applied).
- **`mvn verify`** runs unit/integration tests and **JaCoCo**; the build fails if the **global** bundle (classes included in the report) is below **80% line** coverage (`pom.xml`).
- **`index.html` does not list test classes** — it shows **coverage of production code** (classes/packages). Surefire XML reports under `target/surefire-reports/` are the test execution results; JaCoCo is a separate report.
- **Excluded packages** (see `<excludes>` under `jacoco-maven-plugin` in `pom.xml`) — e.g. `com.uniovi.rag.tool/**`, `configuration`, `model`, large services — **do not appear** in the HTML/XML coverage report. Tests for those packages still run and appear in Surefire, but lines are **not** counted toward JaCoCo.
- **Per-package check:** open `target/site/jacoco/index.html` after `mvn verify` (no bundled Python checker in this repo; add one under `rag-service/scripts/` if you need automation).

## API response shape (product vs legacy query)

**Product API** (`rag.api.product-base-path`) uses JWT-backed routes for projects, chat, documents, and lab.  
**Legacy** `GET {legacy}/query` (when `RAG_API_LEGACY_CONTROLLERS_ENABLED=true`) returns JSON: `{ "success": true, "data": { "answer", "queryType", "usedTool", "toolUsed" } }`.  
If the LLM backend (Ollama) cannot be reached, the service returns **503** with `{ "success": false, "error": { "code": "LLM_UNAVAILABLE", ... } }`. While models are still downloading on startup, **503** uses code **`OLLAMA_PROVISIONING`** (retry when `/actuator/health/readiness` is UP).

## API documentation (generated)

- **Javadoc:** `./mvnw javadoc:javadoc` → `target/site/apidocs/index.html`.
- **OpenAPI (springdoc):** when the app is running, **`GET /v3/api-docs`** (JSON) and **`/swagger-ui.html`** are **permitAll** in `SecurityConfiguration` — they cover `{product}` routes, `/api/auth`, and `/api/admin` in one document.
- **Export to a file (for PR diffs / CI artifacts):** with the backend listening on port 9000 (default), from the **repository root**:

  ```bash
  ./rag-service/scripts/export-openapi.sh http://127.0.0.1:9000 rag-service/target/openapi-export.json
  ```

  Defaults: `BASE_URL=http://127.0.0.1:9000`, output `rag-service/target/openapi-export.json`. Use **Git Bash**, **WSL**, or run `curl -sfS http://127.0.0.1:9000/v3/api-docs -o rag-service/target/openapi-export.json` manually.

- **`./mvnw verify`** runs tests against the application context; for a **frozen** OpenAPI snapshot in CI, start the jar or container and run the script above, then commit or attach the JSON as a build artifact.

## Smoke test

With the stack running, see [docker/scripts/README.md](../docker/scripts/README.md) (section **Smoke test**) or [scripts/README.md](../scripts/README.md) (index), or run `./rag-service/scripts/smoke-test.sh` (checks classifier, then `/actuator/health`; product endpoints require JWT).

## Observability (optional)

With `docker compose -f docker-compose.yml -f compose.obs.yml --env-file ../db/.env --env-file ../rag-service/.env --env-file ../classifier-service/.env --env-file ../observability/.env up -d` you can run OpenTelemetry Collector, Jaeger, Prometheus, and Grafana. The backend exposes `/actuator/health` and `/actuator/prometheus`. Configure the Prometheus datasource in Grafana (`http://prometheus:9090`) for dashboards.

**OTLP in Docker:** the base Compose file sets `SPRING_PROFILES_ACTIVE=docker` so the backend does **not** send OTLP to `localhost:4318` (inside the container that points at the JVM, not the collector). `compose.obs.yml` adds **`docker,infra`** and `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318` so traces/metrics export to the collector and appear in Jaeger. **`backend-dev`** with `--obs` uses **`dev,infra`** (see `compose.rag-dev-obs.yml`).

**Trace context (async / parallel):** use `com.uniovi.rag.infrastructure.observability.ContextPropagatingFutures` instead of raw `CompletableFuture.supplyAsync` / `runAsync` on the default pool. For `parallelStream()`, call `captureContext()` once, then `withSnapshot(snapshot, () -> …)` in each worker (see `AbstractMetadataTool`). This avoids orphan spans (“missing parent”) in Jaeger.

## RAG runtime execution

**Orchestrated runtime:** `ProcessQueryService` / `SimpleProcessQueryService` are façades over `ExecutionContextFactory` → `RagExecutionOrchestrator`. The orchestrator executes `QueryUnderstandingPipeline` for every request to build an immutable `QueryPlan`, attaches it to `ExecutionContext`, then runs `WorkflowSelector`, then **`DeterministicToolStrategy`** (only tool entrypoint; rule-based selection from `QueryPlan` when `toolsEnabled` and ambiguity is sufficient). On successful deterministic tool execution, the orchestrator returns the tool answer **without** running the workflow; otherwise it runs the selected `ExecutionWorkflow`. For answer generation and retrieval prompts, workflows use **only** `QueryPlan.rewrittenQueryText` (raw user input is trace-only).

**Legacy preparation pipeline (non-orchestrated / transitional):** `service.query.pipeline` (`QueryInputPreparer`, `ResponseSynthesisPipeline`, `AnswerGenerationKernel`) remains as legacy logic but is bypassed by orchestrated execution paths. `ResponseSynthesisPipeline` does **not** perform deterministic tool routing; tools are orchestrated only in `RagExecutionOrchestrator`.

**Stage order (high level):** `config_resolve` → `context_arm` → `query_prepare` (expand → NER → classify) → `reasoning_pre` (optional) → `synthesis` → `reasoning_post` / `ranker` (optional). Span names for observability: `rag.runtime.stage.config_resolve`, … `rag.runtime.stage.synthesis`, etc. (add in application code as needed).

**Internal order inside `ResponseSynthesisPipeline.synthesizeCore` (legacy only):** metadata/date guard → LLM branch (`AnswerGenerationKernel`). Deterministic tools are **not** invoked here.

**Retrieval policy (`RetrievalPolicyResolver`):** if `postRetrievalEnabled` is true for the effective `RagConfig`, the stock `QuestionAnswerAdvisor` path is **not** used (manual retrieval + post-processing only), unless `rag.runtime.legacy-advisor-with-post-retrieval=true`. Evaluation continues to pass a `null` advisor bean when only manual retrieval is desired.

**Selection table (after `useRetrieval`; naive full-corpus handled first in the kernel):**

| Condition (evaluated in order) | Path |
| --- | --- |
| `postRetrievalEnabled` | Manual only |
| `useAdvisor` and advisor bean present, post off | `QuestionAnswerAdvisor` fast path when applicable |
| Default | Manual `ContextRetriever` |

## Ollama requirement (Docker without GPU compose)

The default backend image talks to Ollama at `SPRING_AI_OLLAMA_BASE_URL` (from `docker-compose.yml`, usually `http://host.docker.internal:11434`). **Ollama must be reachable** (host or container). On startup, the backend calls Ollama’s HTTP API (`POST /api/pull`) to **download** the chat and embedding models configured in `spring.ai.ollama.chat.model` / `spring.ai.ollama.embedding.model` if they are missing (`rag.ollama.auto-pull-enabled=true` by default). Disable auto-pull in air-gapped environments and provide models manually. Use `compose.ollama-local-gpu.yml` (NVIDIA GPU only; `./docker/scripts/up.sh … --gpu` or `--ollama`) so Ollama runs in Docker (`SPRING_AI_OLLAMA_BASE_URL=http://ollama:11434`).

## Execution modes

Quick reference and commands: [docker/README.md](../docker/README.md) (section **Execution modes**).

For **prod-local** mode (reverse proxy + hardened ports for internal services):

```bash
./docker/scripts/up.sh prod
# Optional: add --obs to include OTEL/Jaeger/Prometheus/Grafana (compose.obs.yml)
```

To stop that mode, use the **same** flags as `up` (e.g. `./docker/scripts/down.sh` or `./docker/scripts/down.sh prod --obs`). For **dev** stacks with `backend-dev`, use `./docker/scripts/down.sh dev --all` (or the same flags as `up dev`).
