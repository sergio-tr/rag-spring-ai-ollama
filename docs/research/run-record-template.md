# Run record template (copy per execution)

**Instructions:** Copy this section into `wave-pilot.md`, `wave-comparative.md`, or a dated file under secure storage; fill every **Required** field before starting the run.

---

## Run header

| Field | Value |
| --- | --- |
| `run_id` | *(team-generated, e.g. RUN-2026-04-19-01)* **Required** |
| `wave_id` | *(e.g. W-PILOT-001)* **Required** |
| `git_sha_full` | **Required** |
| `operator` | **Required** |
| `started_at_utc` | ISO-8601 **Required** |
| `finished_at_utc` | ISO-8601 |
| `hypothesis_ids` | *(e.g. H1)* |
| `benchmark_kind` | `LLM_JUDGE_QA` / `EMBEDDING_RETRIEVAL` / `RAG_PRESET_END_TO_END` / `CLASSIFIER_METRICS` / `MICRO_BENCHMARK_V1` **Required** |

## Environment

| Field | Value |
| --- | --- |
| Spring profiles | |
| `SPRING_AI_OLLAMA_CHAT_MODEL` | |
| `SPRING_AI_OLLAMA_EMBEDDING_MODEL` | |
| `OLLAMA_BASE_URL` | *(host only in-repo; no secrets)* |
| `rag.runtime.workflow-schema-version` | |
| Compose stack? (y/n) | |
| Image digests (if Compose) | |

## Dataset / project

| Field | Value |
| --- | --- |
| `dataset_id` | **Required** |
| `dataset_manifest_sha256` | **Required** |
| `project_id` (UUID) | *(if applicable)* |
| `resolved_config_snapshot_id` | *(required for RAG_PRESET_END_TO_END parity claims)* |

## Execution identifiers

| Field | Value |
| --- | --- |
| Lab `evaluation_run.id` | |
| Lab `async_task.id` / job poll URL | |
| Micro-benchmark output path | |

## Outcome

| Field | Value |
| --- | --- |
| `outcome` | `SUCCESS` / `PARTIAL` / `INFRA_FAILED` / `ABORTED` **Required** |
| Primary metric name | |
| Primary metric value | |
| Secondary metric name | |
| Secondary metric value | |
| Notes / anomalies | |

## Artefacts

| Artefact | `sha256` | Location (path or URL) |
| --- | --- | --- |
| Lab export | | |
| Micro-benchmark JSON | | |
| Screenshots / logs | | |
