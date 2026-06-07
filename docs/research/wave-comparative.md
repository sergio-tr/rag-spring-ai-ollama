# Comparative wave — W-COMP-001

**Objective:** Execute the ablation matrix in [`experimental-design-matrix.md`](experimental-design-matrix.md) §5 with **frozen** anchors (git SHA, models, dataset manifest) and mandatory **B-REF** / **B-SUB-X** baselines.

**Code freeze (normative):** Git SHA `7098c40d975803a2ddb30fe897d8d8b8d98d8100` — **no** application code changes between matrix rows except documented hotfixes (which **restart** the wave with new `wave_id`).

**Dataset freeze:** `EVAL-XLSX-CLASSPATH-V1` at SHA256 `4b525f4341cf57fb6275e709555dd6030318e516ade60abef420cee4f97b5b3d` — or replace with project upload id **before** freezing; record manifest in run sheet.

**IV freeze policy for this wave:** At most **three** of (`IV-BENCH`, `IV-SNAP`, `IV-RETR`, `IV-MODEL`, `IV-DATA`) may differ across rows. Default for W-COMP-001 template: vary **`IV-BENCH`** and **`IV-RETR`** only; keep `IV-SNAP`, `IV-MODEL`, `IV-DATA` fixed.

---

## Gate — RUN-COMP-G-BUILD-001

| Field | Value |
| --- | --- |
| `run_id` | RUN-COMP-G-BUILD-001 |
| `wave_id` | W-COMP-001 |
| `git_sha_full` | `7098c40d975803a2ddb30fe897d8d8b8d98d8100` |
| `benchmark_kind` | `MAVEN_VERIFY` |
| `outcome` | `SUCCESS` |
| Notes | Same gate as pilot: `./mvnw clean verify` in `rag-service/` passed for this SHA before comparative documentation was written. Re-run before **each** execution day on the stack. |

---

## Matrix execution log (template)

Fill `evaluation_run.id`, export path, and primary DV after each row.

| Row id | Hypothesis | IV-BENCH | IV-RETR | `evaluation_run.id` | Primary DV | `outcome` | Notes |
| --- | --- | --- | --- | --- | --- | --- | --- |
| A2 | H1 | `LLM_JUDGE_QA` | n/a | `TBD` | `TBD` | `PLANNED` | B-SUB (no retrieval path for RAG claim). |
| A1 | H1 | `RAG_PRESET_END_TO_END` | frozen | `TBD` | `TBD` | `PLANNED` | B-REF — requires `resolved_config_snapshot_id` in run sheet. |
| A3 | H2 | `EMBEDDING_RETRIEVAL` | `k=k1` | `TBD` | `TBD` | `PLANNED` | |
| A4 | H2 | `EMBEDDING_RETRIEVAL` | `k=k2` | `TBD` | `TBD` | `PLANNED` | |

**Execution order (normative):** `A2 → A1` for H1; then `A3 → A4` monotonically in `k`.

Replace `PLANNED` with `SUCCESS` / `PARTIAL` / `INFRA_FAILED` after runs. Do not leave `TBD` ids in published tables.

---

## Baseline repetition rule

If **any** of SHA, chat model tag, embedding model tag, or dataset hash changes:

1. Increment wave id to `W-COMP-002`.
2. Re-execute **full** row `A1` (B-REF) before interpreting deltas on H2 rows.

---

## Comparative wave closure checklist

- [x] Freeze SHA and dataset manifest documented.
- [x] G-build recorded for entry (`RUN-COMP-G-BUILD-001`).
- [ ] All matrix rows `A1–A4` executed with exports archived *(operator)*.
- [ ] [`final-evaluation-synthesis.md`](final-evaluation-synthesis.md) updated with numeric summary and threats.
