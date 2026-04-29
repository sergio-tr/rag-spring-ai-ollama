# RAG Spring AI Ollama

RAG (Retrieval-Augmented Generation) system with Spring Boot, Spring AI, Ollama and PostgreSQL (PgVector). Includes a query-type classifier exposed as an HTTP service (classifier-service).

**Target architecture (frozen model):** [RAG runtime](../docs/architecture/rag-runtime-architecture.md), [configuration & resolution](../docs/architecture/configuration-resolution-model.md). **P56** locks regression-suite **global** run list/execute/delete and **global** run ZIP export/import/preview controllers against **definition-scoped** run/ZIP routes; **P57** locks **global definition-document ZIP** (**P38**/**P39**/**P40**) against **`RuntimeTraceRegressionSuiteDefinitionController`** run surfaces (**`/runs/`** discriminant for **P53**/**P54**/**P55**); **P58** locks the three **definition-document ZIP** **`@Service`** adapters (**export**/**import**/**import preview**) against **run** ZIP/persistence **`@Service`** types (**ArchUnit** + **`T-P58-svc-zip`**, carried **P57** WebMvc); **P59** locks the **nine** **`@RestController`** HTTP mappings for the **`runtime-trace-regression-suite-*`** path families (**tracked** **`p59-runtime-trace-regression-suite-http-inventory.md`**, **`T-P59-collision`** / **handler-count** / **prefix-ownership** / **controller-beans** / **mapping-style**); **P60** (**Microphase 5.52**) freezes **observable** HTTP status/header/body contracts per matrix rows **`P60-M-xx`** via **`RuntimeTraceRegressionSuiteP60EndToEndContractTest`** (**MockMvc** / **`@WebMvcTest`**, **`T-P60-e2e`** / **`T-P60-errors`**) — **P59** stays the **route inventory** authority; **P61** (**Microphase 5.53**) is the **terminal docs-only closure** — frozen-module declaration + **change-control checklist** below (**`T-P61-carry-p56-p60`** = full **`rag-service`** **`mvn test`** gate). See **P56**–**P61** in `rag-runtime-architecture.md`, this README (**P61** subsection), and `BACKEND_PACKAGES.md` **`interfaces.rest`** row.

**Query execution:** chat and legacy query entry points use `ProcessQueryService` / `SimpleProcessQueryService` as a thin façade over `ExecutionContextFactory` → `RagExecutionOrchestrator`. After the factory builds `ExecutionContext`, the runtime order is frozen: **P11 clarification pre-processing** (load/merge pending clarification into `preMemoryPlanningInputText`) → **P12 memory stage** (bounded history slice + at most one LLM-backed condensation call with deterministic fallback; sets final `effectivePlanningInputText`) → the orchestrator runs **`QueryUnderstandingPipeline` once** on that final `effectivePlanningInputText`, then **P11 clarification policy**. On `ASKED_*`, the turn stops with `workflowName=clarification` and **does not** run adaptive routing or any downstream execution families. Otherwise the orchestrator runs **P13 adaptive routing** (when `rag.features.adaptive-routing-enabled` / `adaptiveRoutingEnabled` is true) to select exactly one primary execution-family route (`DIRECT_WORKFLOW_ROUTE`, `RETRIEVAL_WORKFLOW_ROUTE`, `DETERMINISTIC_TOOL_ROUTE`, `FUNCTION_CALLING_ROUTE`, `ADVISOR_ROUTE`), enforces route-family exclusivity, and applies at most one deterministic workflow fallback when a selected non-workflow route does not terminate the turn. When `rag.features.judge-enabled` / `judgeEnabled` is true, the orchestrator then runs **P14 judge** after the selected route family has produced a candidate answer (bounded evaluation + at most one repair attempt, no upstream stage re-runs, no route-family changes). `WorkflowSelector` is invoked only inside workflow-capable routes (and after successful advisor execution to choose a retrieval-capable workflow). When adaptive routing is disabled, the orchestrator derives a single compatibility workflow route and executes workflow only. Unsupported combinations return **422** with `UNSUPPORTED_RUNTIME_CONFIGURATION`; missing ACTIVE knowledge snapshots for knowledge workflows return `KNOWLEDGE_SNAPSHOT_UNAVAILABLE`. Successful runs log `workflow`, snapshot ids, and `correlationId` at INFO. **P15** persists the finalized `ExecutionTrace` as one best-effort `runtime_execution_trace` row after the turn completes (no effect on response on failure). **P16** adds minimal read-only query surfaces for those persisted traces (summary list per conversation + detail by trace id / message id), owner-scoped and layered through a single application service. **P17** adds minimal read-only export endpoints layered over a single application service that generates bounded, deterministic ZIP exports of persisted traces (single-trace or per-conversation bundle), without adding replay or analytics/reporting semantics. **P18** adds an internal-only bounded replay core (`RuntimeTraceReplayService` under `application.service.runtime.tracereplay`): reads traces via `RuntimeTraceQueryService` only, pins `resolved_config_snapshot_id` from the persisted trace row, performs no writes, and does not invoke the orchestrator or `ProcessQueryService` (no REST surface in P18). **P19** adds an internal-only replay comparison core (`RuntimeTraceReplayComparisonService` under `application.service.runtime.tracecomparison`): compares one P16 trace detail to one P18 replay result in memory with closed outcomes and bounded fields; it does not add REST endpoints. **P20** adds **two** owner-scoped **GET** routes (`RuntimeTraceReplayComparisonController` → `RuntimeTraceReplayComparisonService` only): replay comparison by trace id or by conversation+message id under `rag.api.product-base-path`; path-only (any query string returns **400**); not-found / inaccessible original → **404** (not **403**); bounded JSON via `interfaces.rest.dto.tracecomparison`; synchronous (no **202**); no batch, export, or second comparison owner. **P21** adds **two** **GET** ZIP download routes (`.../replay-comparison/export`) via `RuntimeTraceReplayComparisonExportController` → **`RuntimeTraceReplayComparisonExportService`** only (`application.service.runtime.tracecomparisonexport`), which calls **`RuntimeTraceReplayComparisonService.compare`** only. Each response is a **2 MiB**-capped ZIP (`manifest.json` + `comparison.json`); larger payloads return **413**. Same query-string and **404** rules as P20; synchronous; **no** P17 trace export coupling. **P22** adds **two** owner-scoped **GET** routes for **standalone** replay (`RuntimeTraceReplayController` → **`RuntimeTraceReplayService`** only): `.../runtime-traces/{traceId}/replay` and `.../conversations/{conversationId}/messages/{messageId}/runtime-trace/replay` under `rag.api.product-base-path`. Path-only (**any query string ⇒ 400**); missing/inaccessible persisted trace for the user (**`NotFoundException`** from the P16 read path inside replay) **⇒ 404**; **200** + bounded JSON for every replay outcome returned by P18 (including **`UNSUPPORTED_*`**), never **403**/**202**. Responses are synchronous and may re-execute the pinned runtime path — treat as an operator/debug surface, not a cheap read. **P23** adds **two** parallel **`GET`** ZIP downloads (`.../replay/export`) via `RuntimeTraceReplayExportController` → **`RuntimeTraceReplayExportService`** only (`application.service.runtime.tracereplayexport`), which calls **`RuntimeTraceReplayService.replay` once** and packages **`manifest.json`** + **`replay.json`** (same bounded projection as P22). **2 MiB** max ZIP (**413** if the assembled archive exceeds the cap — rare if P22 string caps hold); same path-only **400**/**404** rules as P22; **no** P17 persisted-trace export or P21 comparison coupling. **P24** adds an internal-only **`RuntimeTraceReplayComparisonBatchService`** (`application.service.runtime.tracecomparisonbatch`) that runs **up to 50** sequential P19 **`compare`** calls per batch (**`BY_TRACE_IDS`** or **`BY_CONVERSATION`** selection via P16 listing) — **no** HTTP, **no** persistence, **no** export; useful for tooling; worst case latency scales with the number of compares. **P30** adds **`RuntimeTraceRegressionSuiteService`** (`application.service.runtime.traceregressionsuite`): **internal-only** orchestration of an **ordered** list of **≤20** suite entries, each delegating to **`RuntimeTraceReplayComparisonBatchService#execute` once** sequentially (reuses P24 batch rules; **no** REST, **no** persistence, **no** export, **no** async). **P31** adds **two** **`POST`** routes (`.../runtime-traces/regression-suite` and `.../conversations/{conversationId}/runtime-traces/regression-suite`) via **`RuntimeTraceRegressionSuiteController` → `execute` only**; strict JSON (**unknown keys ⇒ 400**); **`NOT_ATTEMPTED` ⇒ 400** empty body; **`EMPTY_SUITE` ⇒ 200**; **no** **404** on these suite POSTs; DTOs in `interfaces.rest.dto.traceregressionsuite`. **P32** adds **two** parallel **`POST`** ZIP downloads (`.../regression-suite/export`) via **`RuntimeTraceRegressionSuiteExportController` → `RuntimeTraceRegressionSuiteExportService` only** (`application.service.runtime.traceregressionsuiteexport`), which calls **`RuntimeTraceRegressionSuiteService#execute` once** and packages **`manifest.json`** (`exportKind: REGRESSION_SUITE`) + **`suite.json`** (P31 DTO only). **2 MiB** max ZIP (**413**); P31 request bodies reused; **`NOT_ATTEMPTED` ⇒ 400** (no ZIP); **no** route-level **404** on export POSTs. **P33** adds internal-only persisted **suite definitions** (`runtime_trace_regression_suite_*`, Flyway V32): **`RuntimeTraceRegressionSuiteDefinitionService`** (`application.service.runtime.traceregressionsuitedefinition`) is the sole owner for CRUD + **materialize** to **`RuntimeTraceRegressionSuiteRequest`** without calling **`RuntimeTraceRegressionSuiteService`**; **no** HTTP routes for definitions in P33 (execution stays **P30**). **P34** adds **two** **`GET`** routes (`…/runtime-trace-regression-suite-definitions` list + `…/{definitionId}` detail) via **`RuntimeTraceRegressionSuiteDefinitionController` → `RuntimeTraceRegressionSuiteDefinitionService` only**; **no** query string (**`400`** empty body), list JSON **`{"definitions":[…]}`**, missing/wrong owner detail **`404`** (`definition not found`); **no** suite execution or export on these GETs. **P25** adds **two** **`POST`** batch routes under `rag.api.product-base-path` (`RuntimeTraceReplayComparisonBatchController` → **`RuntimeTraceReplayComparisonBatchService#execute` only**): `.../runtime-traces/replay-comparisons/batch` (body: `traceIds` only) and `.../conversations/{conversationId}/runtime-traces/replay-comparisons/batch` (optional `createdAtFrom` / `createdAtTo` / `workflowName`). Strict JSON (unknown keys ⇒ **400**); **`NOT_ATTEMPTED`** ⇒ **400**; bounded DTOs in `interfaces.rest.dto.tracecomparisonbatch` (items **≤50**, counters-only). **P26** adds **two** **`POST`** batch ZIP downloads (`RuntimeTraceReplayComparisonBatchExportController` → **`RuntimeTraceReplayComparisonBatchExportService`** only, `application.service.runtime.tracecomparisonbatchexport`): `.../batch/export` parallel to P25 paths; **`manifest.json`** + **`batch.json`** (P25 DTO only); **2 MiB** max (**413**); same **400**/**404** matrix as P25 for batch semantics (**`NOT_ATTEMPTED`** ⇒ **400**, no ZIP). **P27** adds an internal-only **`RuntimeTraceReplayBatchService`** (`application.service.runtime.tracereplaybatch`) that runs **up to 50** sequential P18 **`replay`** calls per batch (**`BY_TRACE_IDS`** or **`BY_CONVERSATION`** via P16 listing) — **no** HTTP in P27, **no** persistence, **no** export, **no** comparison-batch coupling; conversation-scope **`NotFoundException`** from the listing call propagates from the batch service (the **P28** HTTP adapter maps it to **404**). **P28** adds **two** **`POST`** routes under `rag.api.product-base-path` (`RuntimeTraceReplayBatchController` → **`RuntimeTraceReplayBatchService#execute` only**): `.../runtime-traces/replays/batch` (body: `traceIds` only) and `.../conversations/{conversationId}/runtime-traces/replays/batch` (optional `createdAtFrom` / `createdAtTo` / `workflowName`). Strict JSON (unknown keys ⇒ **400**); **`NOT_ATTEMPTED`** ⇒ **400**; bounded DTOs in `interfaces.rest.dto.tracereplaybatch` (items **≤50**); trace-id route **never** route-level **404** for per-trace access — same **200**/**400**/**404** discipline as **P25** for batch replay comparison. **P29** adds **two** parallel **`POST`** ZIP downloads (`.../replays/batch/export`) via `RuntimeTraceReplayBatchExportController` → **`RuntimeTraceReplayBatchExportService`** only (`application.service.runtime.tracereplaybatchexport`), which calls **`RuntimeTraceReplayBatchService#execute` once** and packages **`manifest.json`** (`exportKind: REPLAY_BATCH`) + **`batch.json`** (P28 DTO only). **2 MiB** max ZIP (**413**); P28 request DTOs reused; same **400**/**404** matrix as P28 for batch semantics (**`NOT_ATTEMPTED`** ⇒ **400**, no ZIP).

**Advanced retrieval (dense workflows):** When P10 advisor packing is **not** used for a turn, `DocumentDenseRagWorkflow`, `ChunkDenseRagWorkflow`, and `ChunkDenseMetadataWorkflow` call `AdvancedRetrievalPipeline` only. When `advisorPackedContextSet` is present, they use that packed context and **do not** call `AdvancedRetrievalPipeline` again. Retrieval uses `QueryPlan.rewrittenQueryText`, supports dense-only or hybrid (dense + PostgreSQL FTS with RRF fusion), then deterministic rerank, filter, and extractive compression. Optional JSON override: `advancedRetrievalMaxContextChars` (default `24000`, clamped with `naiveFullCorpusMaxChars` in the same config merge). Traces: substages `retrieval_*` plus `ExecutionTrace.retrievalDiagnostics`; P10 adds `advisor_policy`, `advisor_retrieval`, `advisor_context_pack` stages and advisor summary fields on `ExecutionTrace`.

**Layering:** Product REST controllers live under `com.uniovi.rag.interfaces.rest` and delegate to **application services** (`com.uniovi.rag.application..`) for persistence; JPA and repositories stay in `infrastructure.persistence`. See `src/test/java/com/uniovi/rag/architecture/LayeredArchitectureTest.java`.

## Refactoring governance (slices)

**Policy and slice template:** [docs/backend/refactoring-governance.md](../docs/backend/refactoring-governance.md) — [ADR 0012](../docs/adr/0012-backend-refactoring-governance.md). Use it for incremental package/naming refactors, static/testability rules, and ArchUnit expectations per small PR.

**Verification gate for any backend refactor slice:** from this directory run `./mvnw verify` (same as the quality baseline below: unit + integration + JaCoCo `jacoco:check` on the configured bundle).

## Spring AI / RAG modernization

- **Inventory, metrics names, freezes:** [`docs/ai/spring-ai-rag-inventory.md`](../docs/ai/spring-ai-rag-inventory.md)
- **Pipeline boundaries** (orchestrator, `AdvancedRetrievalPipeline`, workflows): [`docs/ai/spring-ai-rag-pipeline-contracts.md`](../docs/ai/spring-ai-rag-pipeline-contracts.md)
- **Agentic additions gate:** [`docs/adr/0013-agentic-patterns-adoption-gate.md`](../docs/adr/0013-agentic-patterns-adoption-gate.md)
- **Implementation notes:** function-calling whitelist stubs use the same tool names as `ToolDescriptor` / `MeetingMinutesToolsAdapter`; workflow LLM calls emit Micrometer timer `rag.ai.llm.invoke`; knowledge ingest emits `rag.knowledge.etl.events`.

## Quality baseline and API paths in tests

- **Verification gate:** from this directory, `./mvnw clean verify` (Surefire + JaCoCo `jacoco:check` on the configured bundle, line coverage ≥ 80%). Operational equivalent from a parent reactor: `mvn test -pl rag-service` does not replace `verify` if you need the same gate as CI Sonar prep.
- **Runbook (all modules, same commands as CI):** [docs/testing/baseline-runbook.md](../docs/testing/baseline-runbook.md).
- **Policies and baseline table:** [docs/quality/README.md](../docs/quality/README.md) (exclusions matrix, Ollama/OTLP test notes, Sonar project key).
- **External test harness (mocks for Ollama, classifier HTTP, OTLP):** [docs/testing/external-test-harness.md](../docs/testing/external-test-harness.md).
- **JaCoCo heavy-package exit contracts** (tiers + exit conditions before shrinking `<excludes>`): [docs/testing/rag-service-heavy-package-coverage-exit-contracts.md](../docs/testing/rag-service-heavy-package-coverage-exit-contracts.md).
- **Product base path in tests:** `src/test/resources/application.properties` sets `rag.api.product-base-path` (default `/api/v5`). Use `com.uniovi.rag.testsupport.RagApiTestPaths#path` (and `productBasePath()`) for MockMvc URLs so paths track that property. For slices that intentionally use another base (e.g. `/api/test`, `/api/v1`), keep an explicit `PRODUCT_BASE` constant or `@TestPropertySource` on that class — do not use `RagApiTestPaths` there, because it always reads the shared `application.properties` file.
- **OTLP in tests:** the same `application.properties` sets harmless localhost OTLP endpoints so actuator export config does not break bootstrap when the environment supplies relative OTLP URLs; `SafeTestSecretsApplicationContextInitializer` still normalizes edge cases.

## P61 — Final closure and change control (runtime trace regression suite HTTP surface)

**P61** (**Microphase 5.53 — Runtime Trace Regression Suite Final Closure and Change-Control Protocol (P61)**) is **documentation only**: it does not add tests or change runtime code. It declares the **nine-controller** regression-suite HTTP block (**`FD-p59-controller-set`**) a **frozen maintenance module** after **P61** merge and publishes how to change that surface safely.

### Normative HTTP truth and compatibility gates

- **Route inventory + mapping collision style:** **P59** — *Microphase 5.51 — Runtime Trace Regression Suite Surface Inventory and Mapping Collision Lockdown (P59)* — tracked file **`rag-service/src/test/resources/p59-runtime-trace-regression-suite-http-inventory.md`** (repository-relative path), **`T-P59-*`**, **`RuntimeTraceRegressionSuiteP59MappingStyleArchitectureTest`**.
- **Observable HTTP contracts:** **P60** — *Microphase 5.52 — Runtime Trace Regression Suite End-to-End Contract Gate (P60)* — matrix rows **`P60-M-xx`**, **`FD-p60-matrix-row-binding`**, **`FD-p60-arch-inventory`**, **`FD-p60-test-split`**, **`RuntimeTraceRegressionSuiteP60EndToEndContractTest`**.
- **Arch / ZIP compatibility (re-evaluate when refactors touch these concerns):** **P56** — *Microphase 5.48 — Runtime Trace Regression Suite Run/Definition Surface Compatibility Lockdown (P56)*; **P57** — *Microphase 5.49 — Runtime Trace Regression Suite Definition ZIP Surface Compatibility Lockdown (P57)*; **P58** — *Microphase 5.50 — Runtime Trace Regression Suite Definition ZIP Service Compatibility Lockdown (P58)*.

### Change-control checklist (FD-p61-protocol-matrix)

| # | Change event | Must revisit / update (order recommended) |
| --- | -------------- | ------------------------------------------ |
| 1 | Add or remove a handler on any type in **`FD-p59-controller-set`** | P59 tracked inventory file + P59 plan matrix + **`T-P59-*`** expectations; P60 add/remove **`P60-M-xx`** rows and **`FD-p60-arch-inventory`** bindings; re-run **Testing plan gate** (`mvn test` from **`rag-service/`**). |
| 2 | Change observable HTTP (status, headers, body) without adding/removing handlers | P60 plan (affected **`P60-M-xx`** rows and authoritative `Class#method`); P60 tests; confirm P59 inventory still matches (`consumes` / `produces` cells if registration changed). |
| 3 | Refactor controllers/packages without changing registered mappings or HTTP observables | P56–P58 Arch tests (still apply); P59 collision / inventory if bean or mapping registration could shift; P60 only if test class locations or imports break. |
| 4 | Rename or split a P60 contract test class, or repartition **`FD-p60-arch-inventory`** across classes under **`FD-p60-test-split`** | In the **same change set**: update the P60 plan **Scope** / **`FD-p60-arch-inventory`** / **`FD-p60-test-split`** obligations per P60; keep **`P60-M-xx`** and **`FD-p60-matrix-row-binding`** aligned with actual `Class#method` and `@Test` names; update README and any allowlist docs that name the affected classes or files; re-run **Testing plan gate** with **equivalent** coverage (no weaker assertions). |
| 5 | Change definition ZIP service edges or global / definition ZIP call graph | P57 and P58 scopes (per those plans); then P60 if observables shift; then P59 if `produces` / `consumes` or paths shift. |
| 6 | Change only non-HTTP internals (domain, DB, non-REST services) with no API impact | No mandatory P59/P60/P56–P58 doc or plan update unless a downstream commit later touches rows 1–5. |

### Testing plan gate (T-P61-carry-p56-p60)

Run the full **`rag-service`** suite with working directory **`rag-service/`**: **`mvn test`** exits **0**. From a parent Maven reactor that lists **`rag-service`** as a module, **`mvn test -pl rag-service`** is equivalent. **T-P61-carry-p56-p60** requires **no weakening** of any test or Arch rule from **P56** through **P60**.

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

#### Dev seeded accounts (profile `dev`)

When running with Spring profile **`dev`** (the default for `backend-dev`), the backend seeds two users so you can use the UI immediately:

- **Admin**: `admin@dev.local` / `dev`
- **User** (non-admin): `user@dev.local` / `dev`

Override these via `rag-service/.env`:

- `RAG_DEV_SEED_ADMIN_EMAIL`, `RAG_DEV_SEED_ADMIN_PASSWORD`, `RAG_DEV_SEED_ADMIN_NAME`
- `RAG_DEV_SEED_USER_EMAIL`, `RAG_DEV_SEED_USER_PASSWORD`, `RAG_DEV_SEED_USER_NAME`

These accounts are **dev-only** and are not created with profile `prod`.

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

**Ollama URL (host, Docker service, or remote machine):** set **`OLLAMA_BASE_URL`** and **`SPRING_AI_OLLAMA_BASE_URL`** in **`rag-service/.env`** to the same HTTP origin the JVM must use (`http://localhost:11434` on the host, `http://host.docker.internal:11434` from containers to the host, `http://ollama:11434` when the **`ollama`** Compose service is running). **`./docker/scripts/up.sh … --ollama-remote`** does **not** change those variables; it only skips starting the local **`ollama`** container when combined with **`--gpu`/`--ollama`**, so your URL in `.env` should point at host or external Ollama.

## Key variables

| Variable / property | Description | Example / default |
| --- | --- | --- |
| `OLLAMA_BASE_URL` | Ollama HTTP API (used by Compose for both services) | `http://localhost:11434` (host JVM) or `http://host.docker.internal:11434` (containers → host) |
| `SPRING_AI_OLLAMA_BASE_URL` | Spring AI `spring.ai.ollama.base-url` | Same as `OLLAMA_BASE_URL` unless you intentionally split them |
| `SPRING_AI_OLLAMA_CHAT_MODEL` / `SPRING_AI_OLLAMA_EMBEDDING_MODEL` | Models used by Spring AI | e.g. `gemma3:4b`, `mxbai-embed-large` |
| `rag.ollama.auto-pull-enabled` | Pull missing models via Ollama HTTP API on startup | `true` (set `false` offline / air-gapped) |
| `rag.ollama.pull-read-timeout-ms` | Per-`pull` timeout (large models) | `1800000` (30 min) |
| `SPRING_DATASOURCE_URL` | PostgreSQL JDBC URL (same DB as in db/.env) | `jdbc:postgresql://localhost:5432/vectordb` or `postgres:5432` in Compose |
| `SPRING_DATASOURCE_USERNAME` | DB user (must match db/.env) | `postgres` |
| `SPRING_DATASOURCE_PASSWORD` | DB password (must match db/.env) | — |
| `RAG_JWT_SECRET` | HS256 key for JWTs (≥32 characters). The root `application.properties` has no default: set this env, or use Spring profile **`dev`** / **`docker`** (non-prod fallbacks in `application-dev.properties` / `application-docker.properties`). | Strong random in staging/prod |
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

**Runtime configuration (`{product}/config/*`, JWT):** `GET …/config/schema`, `POST …/config/preview` (optional `presetId`, `conversationId`, `runtimeOverride`, reindex preview fields), `POST …/config/resolved-snapshots` (persist a resolved snapshot row for Lab reproducibility; returns `id` + `configHash`), `GET …/config/resolved-snapshots/{id}` (owner-only). Canonical persistence layout: [../docs/architecture/DATA_MODEL.md — Section 6.1](../docs/architecture/DATA_MODEL.md#dm-s6-1). Presets: `{product}/presets` includes optional `profileRefs` on create/update/list/get.

**Conversation move (`{product}/projects/{projectId}/conversations/{conversationId}/move`, JWT):** `POST` with query `destinationProjectId` (**204**). Reassigns the conversation to another project owned by the same user; clears `document_filter`; updates only `CHAT_LOCAL` rows in `project_documents`; aligns CONVERSATION-scoped `knowledge_index_snapshot.project_id`. Implementation: `MoveConversationApplicationService`.

**Knowledge System (`{product}/projects/{projectId}/knowledge/*`, JWT):** canonical ingest and snapshot APIs for corpus indexing (not RAG query execution). **Ingest** document uploads use `POST …/knowledge/ingest` with `corpusScope` (`PROJECT_SHARED` or `CHAT_LOCAL`) and, for chat-local scope, `conversationId`. Each ingest persists a default `resolved_config_snapshot` row and links new `knowledge_index_snapshot` rows to it (`resolved_config_snapshot_id` + `resolved_config_hash`, see [../docs/architecture/DATA_MODEL.md — Section 6.2](../docs/architecture/DATA_MODEL.md#dm-s6-2)). **Config-aware rebuild:** `POST …/knowledge/rebuild/preview` (JSON body: `corpusScope`, optional `conversationId`, optional `presetId`, `runtimeOverride`, `touchedProfileTypes`, `correlationId`) returns materialization/chunk/embedding fields, `reindexImpact`, precomputed `reindexDecision`, and preview `configHash` without writing snapshots. `POST …/knowledge/rebuild/execute` accepts the same fields plus optional `explicitResolvedConfigSnapshotId` (pin execute); response includes `resolvedConfigSnapshotId` and, when a pipeline runs, `knowledgeSnapshotId` / `reindexEventId`. **Legacy alias:** `POST …/knowledge/reindex` (query params only) delegates to execute with empty overrides (same scope rules). **Read APIs:** `GET …/knowledge/snapshots`, `GET …/knowledge/snapshots/{snapshotId}` use the same `corpusScope` / optional `conversationId` rules. Listing and deleting **project** documents remain on `GET/DELETE {product}/projects/{projectId}/documents`; use **knowledge** routes for uploads and reindex (not `POST` on `/documents` for new binaries). Conversation overlay uploads may also use `POST {product}/projects/{projectId}/conversations/{conversationId}/documents` (delegates to the same ingestion service). See [../docs/architecture/knowledge-system-model.md](../docs/architecture/knowledge-system-model.md), [../docs/architecture/DATA_MODEL.md — Section 6.2](../docs/architecture/DATA_MODEL.md#dm-s6-2).

### Lab benchmarks

Use **product** routes under `{product}/lab` (JWT). Canonical runs live in `evaluation_run` + `evaluation_result`; `async_task` is operational (poll `/lab/jobs/{asyncTaskId}`).

| Goal (product) | Use | Legacy (not primary SCIENCE evidence) |
| --- | --- | --- |
| LLM judge QA (no retrieval) | `POST /lab/benchmarks/LLM_JUDGE_QA/runs` | — |
| Embedding / retrieval only | `POST /lab/benchmarks/EMBEDDING_RETRIEVAL/runs` | — |
| Full RAG (preset + snapshots) | `POST /lab/benchmarks/RAG_PRESET_END_TO_END/runs` | — |
| Classifier metrics | `POST /lab/benchmarks/CLASSIFIER_METRICS/runs` (multipart) | — |
| Combinatorial feature-flag matrix | — | — |

Export: `GET /lab/runs/{id}/export?format=csv` (first line `#META:` + JSON run header, then CSV rows) or `format=json`.

### Thesis empirical evidence

For **degree-project** experimentation (hypotheses, freeze protocol, ablation matrices, run sheets, and closing synthesis), use the canonical folder **[`docs/research/`](../docs/research/README.md)**. Keep long CSV/JSON exports **out of git** when large; record paths and checksums in the run sheets there.

### Configuration layout (two main files + tests)

| File | Role |
| --- | --- |
| `application.properties` | **Application:** RAG/AI/features, DB, actuator defaults, OTLP off by default; profile **`dev`** (DevTools, logs, CORS, JPA `update`) |
| `application-infra.properties` | **Environment / infra:** profile **`docker`** (no OTLP inside container by default), profile **`infra`** (OTLP on, collector URLs, relaxed container health, probes) |
| `application-test.properties` | Test profile only (no real Ollama/OTLP) |
| `application-e2e.properties` | Profile **`e2e`:** Playwright/CI-only stubs, no real Ollama/classifier. Sets `rag.e2e.admin-password` (seeded admin for E2E; default `e2e`, not for production) |

Edit **`application.properties`** for product behaviour; **`application-infra.properties`** for Docker/OTLP/actuator wiring. Override with environment variables (e.g. `SPRING_DATASOURCE_URL`, `OTEL_EXPORTER_OTLP_ENDPOINT`) or extra profiles.

You can use environment variables with placeholders `${VAR_NAME:default}`. Override via `.env` or the environment (e.g. `SPRING_DATASOURCE_URL`, `OLLAMA_BASE_URL`, `RAG_CLASSIFIER_SERVICE_URL`, `OTEL_EXPORTER_OTLP_ENDPOINT`).

## Tests and JaCoCo (`target/site/jacoco/index.html`)

### Replicate CI locally (backend `mvn verify`)

Full pipeline job names, PR merge policy, and Compose parity: [`docs/devops/README.md`](../docs/devops/README.md).

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

## API response shape

**Product API** (`rag.api.product-base-path`) uses JWT-backed routes for projects, chat, documents, and lab.  
**Saved runtime trace regression suite definitions and persisted suite runs (P34–P55):** under `{product}/runtime-trace-regression-suite-definitions`, **`GET`** list/detail use the definition service read API only; **`POST`**/**`PUT`**/**`DELETE`** mutate via **`create`**/**`update`**/**`delete`** only (strict JSON on create/update via **`definitionMutationStrictObjectMapper`**; **`201`** + **`Location`** on create). **`POST …/{definitionId}/execute`** and **`POST …/conversations/{conversationId}/…/{definitionId}/execute`** materialize with **`materializeToSuiteRequest`** then run **`RuntimeTraceRegressionSuiteService#execute`** — same **`200`** JSON as P31 via **`RuntimeTraceRegressionSuiteResponseDto.fromResult`**, empty-body **`400`**/**`404`** rules per architecture doc; optional body only (**`null`** or whitespace). **P47** adds **`POST …/{definitionId}/runs`** and **`POST …/conversations/{conversationId}/…/{definitionId}/runs`** (no **`consumes`**): same optional-body rule as **`POST …/execute`**, then **`createRun`** with **`SAVED_DEFINITION`** and the path **`definitionId`** when the outcome allows — **`201`** empty + **`Location`** **`{product}/runtime-trace-regression-suite-runs/{newRunId}`** (**no** JSON response body); **`400`**/**`404`** per architecture doc — **no** bridge **`@Service`**. **P37** adds **`POST …/execute/export`** (same paths with **`/execute/export`**) returning **`application/zip`** (**`manifest.json`** + **`suite.json`**, **2 MiB** max, **`413`** when exceeded) via **`RuntimeTraceRegressionSuiteDefinitionExecutionExportController`** only. **P38** adds **`GET …/{definitionId}/export`** (no query string; malformed id **`400`**; missing/wrong owner **`404`**; ZIP **> 2 MiB** **`413`**) returning **`application/zip`** (**`manifest.json`** + **`definition.json`**) via **`RuntimeTraceRegressionSuiteDefinitionExportController`** → **`RuntimeTraceRegressionSuiteDefinitionExportService`** (**`loadByIdForUser`** only — no suite **`execute`**). **P39** adds **`POST …/import`** with raw **`application/zip`** body (no **`Content-Type`** parameters, **≤ 2 MiB**), P38-strict two-entry STORED ZIP, **`201`** + **`Location`** on success, duplicate name **`409`** empty body (same as create), validation failures **`400`** empty — **`RuntimeTraceRegressionSuiteDefinitionImportController`** → **`importDefinitionZip`** → **`create`** only. **P40** adds **`POST …/import/preview`**: same P38-strict **`application/zip`** rules, **`200`** JSON (**`RuntimeTraceRegressionSuiteDefinitionImportPreviewResponseDto`**) — **no** writes, **no** definition or import service on that chain. **P41** adds internal persistence for suite **run** snapshots (**`RuntimeTraceRegressionSuiteRunPersistenceService`** → **`runtime_trace_regression_suite_run`** / **`_entry`**; **no** HTTP in P41, **no** **`execute`** on the persistence path). **P48** adds internal owner-scoped **`deleteRunForUser`** on the same service (**database CASCADE** removes **`_entry`** rows; **no** HTTP in P48). **P49** adds **`DELETE {product}/runtime-trace-regression-suite-runs/{runId}`** (**`deleteRunForUser`** only — **`204`**/**`404`**/**`400`** empty per architecture doc; **no** bridge **`@Service`**). **P50** adds **`GET {product}/runtime-trace-regression-suite-definitions/{definitionId}/runs`** and **`GET …/runs/{runId}`** (definition **`loadByIdForUser`** gate, then scoped run list/detail — P42 run DTOs only; **no** **`execute`**/**`createRun`**). **P51** adds internal **`deleteRunForUserAndDefinition`** (same service as global delete; **no** HTTP in P51). **P52** adds **`DELETE {product}/runtime-trace-regression-suite-definitions/{definitionId}/runs/{runId}`** (definition **`loadByIdForUser`** gate, then **`deleteRunForUserAndDefinition`** only — **no** two-arg **`deleteRunForUser`**). **P53** adds **`GET {product}/runtime-trace-regression-suite-definitions/{definitionId}/runs/{runId}/export`** (**`application/zip`** — same gate + **`RuntimeTraceRegressionSuiteRunExportService#exportRunZipForDefinition`** only; **no** direct **`runPersistenceService`** on the controller path; **P43** global **`GET …/runtime-trace-regression-suite-runs/{runId}/export`** unchanged). **P54** adds **`POST {product}/runtime-trace-regression-suite-definitions/{definitionId}/runs/import`**: definition **`loadByIdForUser`** gate **before** the raw **`application/zip`** body (**1–2 MiB**), then **`RuntimeTraceRegressionSuiteRunImportService#importRunZipForDefinition`** only (**P53-shaped** manifest) — **`201`** empty + **`Location`** **`{product}/runtime-trace-regression-suite-runs/{newRunId}`**; **no** controller **`createRun`**; **P44** **`POST …/runtime-trace-regression-suite-runs/import`** unchanged. **P55** adds **`POST {product}/runtime-trace-regression-suite-definitions/{definitionId}/runs/import/preview`**: same gate **before** body, then **`RuntimeTraceRegressionSuiteRunImportPreviewService#previewImportZipForDefinition`** only — **`200`** JSON preview (**no** **`Location`**, **no** persistence, **no** **`RuntimeTraceRegressionSuiteRunImportService`** on that path); **P45** **`POST …/runtime-trace-regression-suite-runs/import/preview`** unchanged. **P42** adds **two** owner-scoped **`GET`** routes under **`{product}/runtime-trace-regression-suite-runs`** (list + **`{runId}`** detail) via **`RuntimeTraceRegressionSuiteRunController` → `RuntimeTraceRegressionSuiteRunPersistenceService` only** (**`listSummariesForUser`** / **`loadByIdForUser`**; path-only — any query string **`400`** empty body; missing/not-owned run **`404`** **`run not found`**; list body **`{"runs":[…]}`**). **P46** adds **two** **`POST`** routes on the same controller (**`POST …/runtime-trace-regression-suite-runs`** and **`POST …/conversations/{conversationId}/runtime-trace-regression-suite-runs`**, **`application/json`**): P31 **`RuntimeTraceRegressionSuiteHttpAdapter`** parse + **`RuntimeTraceRegressionSuiteService#execute`** once, then **`createRun`** with **`AD_HOC`** and empty **`definitionId`** when the outcome allows — **`201`** empty + **`Location`** **`{product}/runtime-trace-regression-suite-runs/{newRunId}`**; **`400`** empty for query string, parse failure, or **`NOT_ATTEMPTED`** — **no** bridge **`@Service`**. **P43** adds **`GET …/runtime-trace-regression-suite-runs/{runId}/export`** (**`application/zip`**: **`manifest.json`** + **`run.json`**, **2 MiB** max, **`413`** when exceeded) via **`RuntimeTraceRegressionSuiteRunExportController` → `RuntimeTraceRegressionSuiteRunExportService` → `loadByIdForUser` only**. **P44** adds **`POST …/runtime-trace-regression-suite-runs/import`** with raw **`application/zip`** (no **`Content-Type`** parameters, **1–2 MiB** body), P43-strict two-entry STORED ZIP + manifest/run coherence, **`201`** empty + **`Location`** on success, validation failures **`400`** empty — **`RuntimeTraceRegressionSuiteRunImportController` → `RuntimeTraceRegressionSuiteRunImportService` → `createRun` only** (**no** suite **`execute`**, **no** artifact run id as persisted PK). **P45** adds **`POST …/runtime-trace-regression-suite-runs/import/preview`**: same P43-strict **`application/zip`** envelope + preview checks, **`200`** JSON (**`RuntimeTraceRegressionSuiteRunImportPreviewResponseDto`**) — **no** persistence, **no** **`RuntimeTraceRegressionSuiteRunImportService`**, **no** **`Location`**. Any disallowed query string on the P37 **`POST`** export routes returns **`400`**. See [rag-runtime-architecture.md](../docs/architecture/rag-runtime-architecture.md). Verify with **`mvn test`** from this module.  
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

With the stack running, see [docker/scripts/README.md](../docker/scripts/README.md) (section **Smoke test**) or run `./rag-service/scripts/smoke-test.sh` (checks classifier, then `/actuator/health`; product endpoints require JWT).

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

The default backend image talks to Ollama at `SPRING_AI_OLLAMA_BASE_URL` (from `docker-compose.yml`, usually `http://host.docker.internal:11434`). **Ollama must be reachable** (host or container). On startup, the backend calls Ollama’s HTTP API (`POST /api/pull`) to **download** the chat and embedding models configured in `spring.ai.ollama.chat.model` / `spring.ai.ollama.embedding.model` if they are missing (`rag.ollama.auto-pull-enabled=true` by default). Disable auto-pull in air-gapped environments and provide models manually. Use **`docker-compose.yml`** with **`--profile ollama`** (NVIDIA GPU only; `./docker/scripts/up.sh … --gpu` or `--ollama`) so Ollama runs in Docker (`SPRING_AI_OLLAMA_BASE_URL=http://ollama:11434`).

## Execution modes

Quick reference and commands: [docker/README.md](../docker/README.md) (section **Execution modes**).

For **prod-local** mode (reverse proxy + hardened ports for internal services):

```bash
./docker/scripts/up.sh prod
# Optional: add --obs to include OTEL/Jaeger/Prometheus/Grafana (compose.obs.yml)
```

To stop that mode, use the **same** flags as `up` (e.g. `./docker/scripts/down.sh` or `./docker/scripts/down.sh prod --obs`). For **dev** stacks with `backend-dev`, use `./docker/scripts/down.sh dev --all` (or the same flags as `up dev`).
