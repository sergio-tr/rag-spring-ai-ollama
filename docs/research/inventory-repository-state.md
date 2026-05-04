# Repository state — empirical research baseline

**Recorded date (UTC):** 2026-04-19  
**Git commit (full SHA):** `7098c40d975803a2ddb30fe897d8d8b8d98d8100`  
**Git short SHA:** `7098c40`

**Method:** Inventory derived from repository tree and cross-checked against module READMEs and ADRs at the commit above. Re-run this section when starting a new experimental **wave** if the branch has moved.

---

## Already implemented (verified)

| Area | Evidence (repository) |
| --- | --- |
| **Lab benchmarks (JWT, `{product}/lab`)** | `rag-service/README.md` — `LLM_JUDGE_QA`, `EMBEDDING_RETRIEVAL`, `RAG_PRESET_END_TO_END`, `CLASSIFIER_METRICS`; export `GET /lab/runs/{id}/export` (`csv` / `json`); persistence `evaluation_run` / `evaluation_result`; async `async_task` with job polling. |
| **Legacy combinatorial evaluation** | Documented as legacy in same README; `GET …/evaluate/all` — not primary structured science evidence. |
| **Typed Lab evaluation datasets** | Internal reference workbook `evaluation/rag_experiment_datasets_and_protocols.xlsx` + `EvaluationReferenceBundleLoader`; uploads/templates via `/lab/experimental-datasets` and `/lab/dataset-templates/{kind}`; async handlers resolve via `ExperimentalDatasetResolver` (**Phase L** retired classpath `evaluation_dataset.xlsx` + `DatasetMinuteEvaluationService`). |
| **Project scope for runs** | ADR `docs/adr/0003-evaluation-async-project-scope-and-dataset-dedup.md` — optional `project_id` on `evaluation_run` and `async_task`. |
| **Unified engine principle** | ADR `docs/adr/0009-unified-product-and-lab-execution-engine.md`. |
| **Micro-benchmarks (latency sample, not load)** | `tests/performance/` — `retrieval_benchmark.py`, `llm_benchmark.py`, `infra_probe.py`; schema `tests/performance/schema/benchmark-report-v1.schema.json`; overview `docs/performance/README.md`. |
| **Load testing** | Gatling under `tests/gatling/` — distinct role from micro-benchmarks. |
| **Stack HTTP integration** | `tests/integration/` — lab async job contract, product paths; see `tests/integration/README.md`. |
| **Persisted execution traces (product path)** | Domain `ExecutionTrace`, P15–P16 persistence narrative in `rag-service/README.md`; services under `application.service.runtime.trace*`. |
| **Quality hub** | `docs/quality/README.md` — canonical `./mvnw clean verify`, mock policies, coverage gates. |
| **External test harness** | `docs/testing/external-test-harness.md` — Ollama, classifier HTTP, OTLP mocking norms. |
| **Thesis minimum scope** | `docs/overview/thesis-scope.md`. |
| **Implementation roadmap (canonical blocks)** | `docs/architecture/implementation-roadmap.md` — block 9 “Experimentation / Lab” noted as partial. |
| **Spring AI RAG supporting docs** | `docs/ai/spring-ai-rag-inventory.md`, `docs/ai/spring-ai-rag-pipeline-contracts.md`, `docs/ai/README.md`. |

---

## Pending (not closed for thesis-grade evidence)

| Gap | Implication |
| --- | --- |
| **Formal run registry in-repo** | This `docs/research/` tree establishes templates and waves; operators must attach **stored exports** (paths/URLs) per run sheet — the repository does not store large CSV/ZIP blobs by default. |
| **Uniform S0–S4 labelling across all Lab surfaces** | `implementation-roadmap.md` states scenario ladder mapping is not yet uniform — comparative claims must name **exact** benchmark kind and config snapshot IDs. |
| **Explicit “Lab vs product” parity proof per scenario** | ADR 0009 is architectural intent; each thesis claim needs a **verification row** in `wave-*.md` or design matrix. |
| **Block 9 closure criteria in CI** | Roadmap: explicit closure criteria per block still evolving — gates use **documented** manual preconditions (G-build, optional stack integration) rather than a single new CI job. |

---

## To verify (do not assume; assign owner before comparative waves)

| Question | Suggested check |
| --- | --- |
| **Same resolved config + snapshots for Lab vs chat** | Create `resolved_config_snapshot` via `{product}/config/resolved-snapshots`, pin in RAG end-to-end benchmark; compare with product chat using same snapshot id (document both run ids). |
| **Numeric reproducibility (Ollama)** | Same model tag, temperature, seed (if exposed); run **N≥3** repetitions for stochastic generator; record variance in run sheet. |
| **`ExecutionTrace` coverage for each Lab benchmark kind** | Inspect export headers and persisted rows; document gaps where trace is placeholder-only. |
| **Token metrics in Python micro-benchmarks** | Heuristic vs real tokenizer — document limitation in every thesis table that cites `benchmark-report-v1` estimated tokens. |

---

## Out of scope for documentation work

- Product authentication UX, Keycloak, webapp features.
- Detailed design of advisors/tools/agentic loops (may appear as **blocked factors** in matrices only).
- Editing `docs/architecture/DATA_MODEL.md` or `docs/architecture/configuration-resolution-model.md`.
