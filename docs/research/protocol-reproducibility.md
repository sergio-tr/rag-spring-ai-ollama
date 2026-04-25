# Reproducibility and freeze protocol

**Normative goal:** Every **comparable** experimental result must be replayable in principle: a reader can see **what** software, **which** models, **which** data, and **which** configuration produced the numbers.

**Primary references:** [`inventory-repository-state.md`](inventory-repository-state.md), [`docs/quality/README.md`](../quality/README.md) (G-build), [`rag-service/README.md`](../../rag-service/README.md) (Lab + `resolved-snapshots`).

---

## 1. Freeze windows (code + configuration)

1. **Name the wave:** e.g. `W-PILOT-001`, `W-COMP-001` (use the same id in all run sheets for that wave).
2. **Freeze git state:** record **full SHA** of the repository used to build/run the backend (and classifier/webapp if they participate in the experiment). No silent drift: if SHA changes mid-wave, **close the wave** and open a new one with new baselines.
3. **Freeze runtime configuration intent:** list active Spring profiles (`dev`, `docker`, `e2e`, …) and **effective** overrides from environment (names only; no secrets in this repo — store redacted copies in secure thesis annex if required).
4. **Single large policy change per wave:** do not simultaneously change application code, Ollama model tag, and evaluation dataset between rows of the same ablation matrix. If a hotfix is mandatory, **re-tag the wave**, repeat the **full baseline** row, then continue.

---

## 2. Versioning anchors (checklist per run sheet)

| Anchor | Required? | Where to record |
| --- | --- | --- |
| Git full SHA | Yes | Run sheet + `#META` export header when using Lab CSV/JSON export. |
| `rag.runtime.workflow-schema-version` | Yes if comparing runtime semantics | Environment / `application.properties` value in run sheet. |
| Ollama **chat** model id + digest (if available) | Yes for LLM-backed benchmarks | Run sheet; capture `ollama show <model>` output in thesis annex. |
| Ollama **embedding** model id + digest | Yes when retrieval/embeddings participate | Same as above. |
| Docker image digests | If using Compose images | `docker compose images -q` or inspect in run sheet annex. |
| Lab `evaluation_run.id` (UUID) | Yes for Lab-backed evidence | Run sheet + link to stored export file. |
| `resolved_config_snapshot_id` | Yes for `RAG_PRESET_END_TO_END` and product-aligned claims | Run sheet; obtain via `POST {product}/config/resolved-snapshots` per README. |
| Dataset manifest (see §3) | Yes | Run sheet + optional `docs/research/datasets/` index row (small YAML/CSV only). |

---

## 3. Dataset manifest (minimum fields)

Each dataset used for thesis evidence must have one manifest row (can live in the run sheet annex table or a small `datasets-manifest.csv` in this folder).

| Field | Description |
| --- | --- |
| `dataset_id` | Stable string chosen by the team (e.g. `EVAL-XLSX-CLASSPATH-V1`). |
| `source` | Path in repo, upload id, or external URI. |
| `content_sha256` | Hash of file bytes at import time (aligns with ADR 0003 logical dedup story). |
| `item_count` | Number of evaluation items (if known). |
| `domain_notes` | Bias / coverage statement (language, topic, legal sensitivity). |
| `owner_scope` | User/project id if project-scoped uploads apply. |

**Canonical internal reference dataset:** `evaluation/evaluation_dataset.xlsx` (classpath) — still record `content_sha256` from the built JAR or source file used in the run.

---

## 4. Artefact registration (Lab vs Python micro-benchmark)

### 4.1 Lab export

- After run completion, call `GET {product}/lab/runs/{id}/export?format=csv` or `format=json`.
- Store the file **outside** git if large; record in run sheet: `path_or_url`, `sha256`, `byte_size`.
- Preserve the **first-line `#META:`** JSON when using CSV — it is part of the evidence header.

### 4.2 Python micro-benchmark (`benchmark-report-v1`)

- Validate output against `tests/performance/schema/benchmark-report-v1.schema.json` (gate **G-bench-schema**).
- Use for **latency / estimated token** claims only — not as a substitute for Lab judge/retrieval metrics when the research question is answer quality.

---

## 5. Preconditions (gates) before interpreting results

| Gate | Command / rule | Pass criterion |
| --- | --- | --- |
| **G-build** | `cd rag-service && ./mvnw clean verify` | Exit code 0 on the **same SHA** as the wave. |
| **G-integration (optional)** | `pytest tests/integration` against a running stack | Only required if the wave protocol declares end-to-end sanity; otherwise document “not run” as a limitation. |
| **G-bench-schema** | JSON Schema validation for micro-benchmark output | Valid document when micro-benchmarks are cited. |

---

## 6. Stochastic repetition rule

When the chat model is stochastic (non-zero temperature or unspecified):

- Record **N** repetitions (minimum **N=3** recommended for pilot; justify **N=1** only with explicit “exploratory” flag and no strong comparative claim).
- Report mean and spread (min/max or stdev) for the primary metric in the wave summary.

---

## 7. Incident handling

Timeouts, `503` from Ollama pull, network errors, or aborted jobs **must not** appear in the same results table as successful quality scores without an `outcome=INFRA_FAILED` row. Retry as a **new run id** with a note linking to the failed attempt.
