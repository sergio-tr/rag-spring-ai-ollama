# ADR 0003 — Project scope for evaluation/async rows and dataset deduplication

## Context

The platform stores **two** concepts of “runs”:

- **`evaluation_run`**: structured batch evaluation linked to `evaluation_dataset` and `evaluation_result` (historical / reporting).
- **`async_task`**: HTTP 202 Lab and admin jobs (LLM/RAG eval, classifier train/eval, Ollama pull) with JSON payloads.

Product and observability benefit from optional **project** traceability without breaking user-global jobs. **`evaluation_dataset`** may receive duplicate uploads (same bytes) from different users or the same user.

## Decision

1. **Nullable FK `project_id` → `projects(id)`** on **`evaluation_run`** and **`async_task`**, `ON DELETE SET NULL`. Indexes on `project_id` for filtered queries. Existing rows remain valid with `NULL` (global/user-scoped jobs).
2. **Dataset deduplication policy (application-level):** treat rows as distinct per upload; **logical** deduplication for reuse is **`(owner_id, sha256)`** when `sha256` is present — no mandatory unique constraint in the database in the thesis scope (allows deliberate duplicates and backfills).
3. **Lab async API:** optional query parameter `projectId` on async Lab endpoints; when present and owned by the user, persist `async_task.project_id`.

## Consequences

- Flyway migration adds columns; JPA maps them as optional `@ManyToOne` to `ProjectEntity`.
- `AsyncTaskService` resolves the project via `ProjectAccessService` when `projectId` is supplied.
- Reporting can filter jobs/runs by project without scanning JSON payloads alone.

## Related

- [DATA_MODEL.md — Section 10 (`evaluation_run` vs `async_task`)](../architecture/DATA_MODEL.md#dm-s10)
- [ADR 0002 — data isolation model](0002-multitenancy-assumption.md)
