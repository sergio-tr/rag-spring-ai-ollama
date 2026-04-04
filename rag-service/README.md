# RAG Spring AI Ollama

RAG (Retrieval-Augmented Generation) system with Spring Boot, Spring AI, Ollama and PostgreSQL (PgVector). Includes a query-type classifier exposed as an HTTP service (classifier-service).

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

| Variable / property              | Description                                    | Example / default        |
|----------------------------------|------------------------------------------------|---------------------------|
| `OLLAMA_BASE_URL`                | Ollama URL                                     | `http://localhost:11434` |
| `SPRING_AI_OLLAMA_CHAT_MODEL` / `SPRING_AI_OLLAMA_EMBEDDING_MODEL` | Models used by Spring AI | e.g. `gemma3:4b`, `mxbai-embed-large` |
| `rag.ollama.auto-pull-enabled`   | Pull missing models via Ollama HTTP API on startup | `true` (set `false` offline / air-gapped) |
| `rag.ollama.pull-read-timeout-ms`| Per-`pull` timeout (large models) | `1800000` (30 min) |
| `SPRING_DATASOURCE_URL`          | PostgreSQL JDBC URL (same DB as in db/.env)    | `jdbc:postgresql://localhost:5432/vectordb` or `postgres:5432` in Compose |
| `SPRING_DATASOURCE_USERNAME`     | DB user (must match db/.env)                   | `postgres`                |
| `SPRING_DATASOURCE_PASSWORD`     | DB password (must match db/.env)               | —                         |
| `rag.classifier.service.url` | Classifier service URL (backend)               | `http://localhost:8000`   |
| `RAG_CONFIG_V2_ENABLED` / `rag.config.v2.enabled` | Use `ResolvedRuntimeConfig` resolution in the chat path (aligned with `POST /config/preview`) | `false` |
| `rag.runtime.workflow-schema-version` | Semver of the RAG execution stage graph (Lab/eval reproducibility) | `1.0.0` |
| `rag.runtime.legacy-advisor-with-post-retrieval` | Allow `QuestionAnswerAdvisor` when post-retrieval is on (legacy; not recommended) | `false` |
| `rag.runtime.memory-max-turns` / `rag.runtime.memory-max-chars` | Caps for product “full” conversation memory when injected into prompts | `20` / `8000` |
| `rag.evaluation.persistence.enabled` | Persist canonical `evaluation_run` / `evaluation_result` from Lab async handlers | `true` |
| `rag.evaluation.storage-root` | Filesystem root for uploaded evaluation datasets (empty → temp dir) | — |
| `rag.evaluation.max-upload-bytes` | Max size per dataset binary (v1 cap) | `26214400` (25 MB) |
| `rag.account.export-storage-dir` / `RAG_ACCOUNT_EXPORT_STORAGE_DIR` | Filesystem root for GDPR-style account export ZIPs (per-user subfolders) | `${java.io.tmpdir}/rag-account-export` |
| `rag.account.export-ttl-hours` / `RAG_ACCOUNT_EXPORT_TTL_HOURS` | Hours until a READY export expires (scheduler may delete the file) | `24` |
| `rag.account.cleanup-interval-ms` / `RAG_ACCOUNT_CLEANUP_INTERVAL_MS` | Fixed delay between expired-export sweeps | `3600000` (1 h) |

**Product hub (`{product}/me/*`):** canonical JSON stores `GET/PUT …/me/preferences` and `…/me/personalization` (with `schema_version` in DB), `GET …/me/summary`, `GET …/me/documents`, `POST …/me/account/export` and `…/deletion` (HTTP **202** + `async_task`), poll `GET …/me/account/jobs/{id}`, download `GET …/me/account/export/{exportId}/download`. **Admin** defaults for new users: `GET/PUT /api/admin/system-defaults`. Legacy `GET/PUT {product}/config/user` remains but is marked **deprecated** in OpenAPI in favor of `/me/*` where overlaps exist.

### Lab benchmarks

Use **product** routes under `{product}/lab` (JWT). Canonical runs live in `evaluation_run` + `evaluation_result`; `async_task` is operational (poll `/lab/jobs/{asyncTaskId}`).

| Goal (TFG / product) | Use | Legacy (not primary SCIENCE evidence) |
|----------------------|-----|----------------------------------------|
| LLM judge QA (no retrieval) | `POST /lab/benchmarks/LLM_JUDGE_QA/runs` | — |
| Embedding / retrieval only | `POST /lab/benchmarks/EMBEDDING_RETRIEVAL/runs` | — |
| Full RAG (preset + snapshots) | `POST /lab/benchmarks/RAG_PRESET_END_TO_END/runs` | — |
| Classifier metrics | `POST /lab/benchmarks/CLASSIFIER_METRICS/runs` (multipart) | — |
| Combinatorial feature-flag matrix | — | `GET {legacy}/evaluate/all` → body includes `legacyEvaluationMode: LEGACY_COMBINATORIAL` |

Export: `GET /lab/runs/{id}/export?format=csv` (first line `#META:` + JSON run header, then CSV rows) or `format=json`.

### Configuration layout (two main files + tests)

| File | Role |
|------|------|
| `application.properties` | **Application:** RAG/AI/features, DB, actuator defaults, OTLP off by default; profile **`dev`** (DevTools, logs, CORS, JPA `update`) |
| `application-infra.properties` | **Environment / infra:** profile **`docker`** (no OTLP inside container by default), profile **`infra`** (OTLP on, collector URLs, relaxed container health, probes) |
| `application-test.properties` | Test profile only (no real Ollama/OTLP) |

Edit **`application.properties`** for product behaviour; **`application-infra.properties`** for Docker/OTLP/actuator wiring. Override with environment variables (e.g. `SPRING_DATASOURCE_URL`, `OTEL_EXPORTER_OTLP_ENDPOINT`) or extra profiles.

You can use environment variables with placeholders `${VAR_NAME:default}`. Override via `.env` or the environment (e.g. `SPRING_DATASOURCE_URL`, `OLLAMA_BASE_URL`, `RAG_CLASSIFIER_SERVICE_URL`, `OTEL_EXPORTER_OTLP_ENDPOINT`).

## Tests and JaCoCo (`target/site/jacoco/index.html`)

- **CI / GitHub Actions:** environment variables override `src/test/resources/application-test.properties`. If **`RAG_JWT_SECRET`** is set (repo or org) to an empty or **too short** value (JWT signing requires at least **32** characters), `JwtService` fails at startup and `@SpringBootTest` classes error with `Failed to load ApplicationContext`. The repo workflows set a dedicated test secret for `mvn verify`. **`SafeTestSecretsApplicationContextInitializer`** (`META-INF/spring.factories` on the test classpath) patches invalid secrets and OTLP URLs. **`TestJdbcEnvironmentApplicationContextInitializer`** (same `spring.factories`) sets `spring.datasource.*` early for tests (GitHub service Postgres vs Testcontainers) so JPA can create `entityManagerFactory`. Full-stack smoke tests (`SpringIntoAiApplicationTests`, OpenAPI export, Rag stabilization) also set **`@SpringBootTest(properties = …)`** so `rag.jwt.secret` and OTLP endpoints win over process env even when org defaults set **`SPRING_PROFILES_ACTIVE=prod`** (which used to skip the initializer before the `test` profile was applied).
- **`mvn verify`** runs unit/integration tests and **JaCoCo**; the build fails if the **global** bundle (classes included in the report) is below **80% line** coverage (`pom.xml`).
- **`index.html` does not list test classes** — it shows **coverage of production code** (classes/packages). Surefire XML reports under `target/surefire-reports/` are the test execution results; JaCoCo is a separate report.
- **Excluded packages** (see `<excludes>` under `jacoco-maven-plugin` in `pom.xml`) — e.g. `com.uniovi.rag.tool/**`, `configuration`, `model`, large services — **do not appear** in the HTML/XML coverage report. Tests for those packages still run and appear in Surefire, but lines are **not** counted toward JaCoCo.
- **Per-package check:** open `target/site/jacoco/index.html` after `mvn verify` (no bundled Python checker in this repo; add one under `rag-service/scripts/` if you need automation).

- Optional **bounded** retry on Windows (if you maintain a local `.ps1`): not part of CI.

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

  Defaults: `BASE_URL=http://127.0.0.1:9000`, output `rag-service/target/openapi-export.json`. On Windows, use **Git Bash**, **WSL**, or run `curl -sfS http://127.0.0.1:9000/v3/api-docs -o rag-service/target/openapi-export.json` manually.

- **`./mvnw verify`** runs tests against the application context; for a **frozen** OpenAPI snapshot in CI, start the jar or container and run the script above, then commit or attach the JSON as a build artifact.

## Smoke test

With the stack running, see [docker/scripts/README.md](../docker/scripts/README.md) (section **Smoke test**) or [scripts/README.md](../scripts/README.md) (index), or run `./rag-service/scripts/smoke-test.sh` (checks classifier, then `/actuator/health`; product endpoints require JWT).

## Observability (optional)

With `docker compose -f docker-compose.yml -f compose.obs.yml --env-file ../db/.env --env-file ../rag-service/.env --env-file ../classifier-service/.env --env-file ../observability/.env up -d` you can run OpenTelemetry Collector, Jaeger, Prometheus, and Grafana. The backend exposes `/actuator/health` and `/actuator/prometheus`. Configure the Prometheus datasource in Grafana (`http://prometheus:9090`) for dashboards.

**OTLP in Docker:** the base Compose file sets `SPRING_PROFILES_ACTIVE=docker` so the backend does **not** send OTLP to `localhost:4318` (inside the container that points at the JVM, not the collector). `compose.obs.yml` adds **`docker,infra`** and `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4318` so traces/metrics export to the collector and appear in Jaeger. **`backend-dev`** with `--obs` uses **`dev,infra`** (see `compose.rag-dev-obs.yml`).

**Trace context (async / parallel):** use `com.uniovi.rag.infrastructure.observability.ContextPropagatingFutures` instead of raw `CompletableFuture.supplyAsync` / `runAsync` on the default pool. For `parallelStream()`, call `captureContext()` once, then `withSnapshot(snapshot, () -> …)` in each worker (see `AbstractMetadataTool`). This avoids orphan spans (“missing parent”) in Jaeger.

## RAG runtime execution

Single pipeline for product and evaluation: `QueryRuntimeComponentsFactory` builds `QueryInputPreparer` + `ResponseSynthesisPipeline` (including `AnswerGenerationKernel`). Chat-scoped config resolution is centralized in `ChatScopedRagConfigResolver` so the main turn and SSE “sources” use the same cascade as `ResolvedRuntimeConfig` when `rag.config.v2.enabled=true`.

**Stage order (high level):** `config_resolve` → `context_arm` → `query_prepare` (expand → NER → classify) → `reasoning_pre` (optional) → `synthesis` → `reasoning_post` / `ranker` (optional). Span names for observability: `rag.runtime.stage.config_resolve`, … `rag.runtime.stage.synthesis`, etc. (add in application code as needed).

**Internal order inside `ResponseSynthesisPipeline.synthesizeCore`:** metadata/date guard → `tryPreferToolForDate` → `tryMainToolsBlock` → `tryToolRoute` → LLM branch (`AnswerGenerationKernel`). Function-calling, when enabled, takes precedence over the deterministic tools adapter for those steps (see `ToolRoutingService`).

**Retrieval policy (`RetrievalPolicyResolver`):** if `postRetrievalEnabled` is true for the effective `RagConfig`, the stock `QuestionAnswerAdvisor` path is **not** used (manual retrieval + post-processing only), unless `rag.runtime.legacy-advisor-with-post-retrieval=true`. Evaluation continues to pass a `null` advisor bean when only manual retrieval is desired.

**Selection table (after `useRetrieval`; naive full-corpus handled first in the kernel):**

| Condition (evaluated in order) | Path |
|--------------------------------|------|
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
