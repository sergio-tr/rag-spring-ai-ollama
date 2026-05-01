# Experimental design matrix

**Normative rule:** No comparative row is executed until its **hypothesis id**, **IV deltas**, **DV definitions**, and **accept/reject rules** are written in this table. Maximum **three** independent factors may change per comparative wave; all others stay frozen per [`protocol-reproducibility.md`](protocol-reproducibility.md).

**Scenario ladder reference (terminology only):** [`docs/adr/0010-rag-scenario-ladder-s0-to-s4.md`](../adr/0010-rag-scenario-ladder-s0-to-s4.md) — map each run to the **actual** benchmark kind and config fields used, not the label alone.

---

## 1. Research questions

| ID | Research question |
| --- | --- |
| RQ1 | Does the configured RAG pipeline improve **context sufficiency** vs a **no-retrieval** LLM judge baseline on the chosen dataset? |
| RQ2 | For a fixed snapshot, does changing **retrieval breadth** (e.g. top-k) change **retrieval-centric** scores without breaking latency budgets? |

Replace RQ1/RQ2 with project-specific questions if needed; keep them stable for the whole comparative wave.

---

## 2. Hypotheses (template — replace `…` with measurable predictions)

| ID | Hypothesis | Linked RQ |
| --- | --- | --- |
| **H1** | For dataset `D`, **RAG_PRESET_END_TO_END** with snapshot `S` yields **higher** aggregate judge “context sufficiency” than **LLM_JUDGE_QA** on the same items, while **p95 latency** stays below **L** ms (from micro-benchmark gate or Lab timing columns if available). | RQ1 |
| **H2** | Increasing top-k from `k1` to `k2` (other fields frozen) **monotonically** increases a defined retrieval score from **EMBEDDING_RETRIEVAL** without exceeding **L** ms p95. | RQ2 |

**Mapping rule:** Each hypothesis must have **at least one baseline row** and **at least one ablation row** in §5.

---

## 3. Independent variables (IV)

| Code | Factor | Levels (examples) | Instrument (how it is set) |
| --- | --- | --- | --- |
| `IV-BENCH` | Benchmark kind | `LLM_JUDGE_QA`, `EMBEDDING_RETRIEVAL`, `RAG_PRESET_END_TO_END` | Lab `POST …/lab/benchmarks/{kind}/runs` |
| `IV-SNAP` | Resolved configuration | snapshot id `resolved_config_snapshot_id` | `POST {product}/config/resolved-snapshots` then pin in RAG benchmark body per README |
| `IV-MODEL` | Generator / embedder | chat model tag, embedding model tag | env / Spring AI properties |
| `IV-RETR` | Retrieval breadth | e.g. `k ∈ {3, 5, 10}` | only via fields exposed in project RAG JSON / preset — **do not** claim changes not represented in resolved config |
| `IV-DATA` | Evaluation items | subset id `D-pilot`, full `D-full` | dataset upload + manifest |

**Constraint:** `IV-BENCH`, `IV-SNAP`, `IV-MODEL`, `IV-RETR`, `IV-DATA` — at most **three** may differ across rows inside one **comparative** wave; document frozen levels for the others in the wave header.

---

## 4. Dependent variables (DV)

| Code | Definition | Instrument |
| --- | --- | --- |
| `DV-JUDGE-SUFF` | Aggregate “context sufficiency” (or nearest available judge dimension) | Lab export columns / JSON |
| `DV-JUDGE-CORR` | “Correctness” or nearest equivalent | Lab export |
| `DV-RETR` | Retrieval score from **EMBEDDING_RETRIEVAL** | Lab export |
| `DV-LAT-P50` | Latency p50 | micro-benchmark report `benchmark-report-v1` (transport documented in `tests/performance/README.md`) |
| `DV-LAT-P95` | Latency p95 | same |
| `DV-ERR` | HTTP error rate (sanity) | stack integration or benchmark script outcome |

**Normative separation:** `DV-JUDGE-*` and `DV-RETR` come from **Lab** exports. `DV-LAT-*` from **Python micro-benchmarks** are **not** substitutes for `DV-JUDGE-*` when the claim is about answer quality.

---

## 5. Baselines and ablations (matrix shell)

**Baseline types (all comparative waves):**

- **B-REF:** Full system under test (all components enabled per research intent).
- **B-SUB-X:** **Subtract** component X (e.g. no retrieval path → use `LLM_JUDGE_QA` or configured-off retrieval only if explicitly supported and documented).

| Row id | Hypothesis | IV-BENCH | IV-SNAP | IV-RETR | IV-DATA | Primary DV | Secondary DV |
| --- | --- | --- | --- | --- | --- | --- | --- |
| A1 | H1 | `RAG_PRESET_END_TO_END` | `S` | frozen | `D` | `DV-JUDGE-SUFF` | `DV-LAT-P95` |
| A2 | H1 | `LLM_JUDGE_QA` | n/a | n/a | `D` | `DV-JUDGE-SUFF` | `DV-LAT-P95` |
| A3 | H2 | `EMBEDDING_RETRIEVAL` | `S` | `k=k1` | `D` | `DV-RETR` | `DV-LAT-P95` |
| A4 | H2 | `EMBEDDING_RETRIEVAL` | `S` | `k=k2` | `D` | `DV-RETR` | `DV-LAT-P95` |

Replace `S`, `D`, `k1`, `k2` with concrete ids before execution. Add rows only within the **three-IV** limit.

---

## 6. Acceptance criteria (wave-level)

A wave **passes** methodology review when:

1. Every executed row has a completed run sheet ([`run-record-template.md`](run-record-template.md)).
2. **G-build** passed on the wave SHA before any Lab result is labelled “final”.
3. Primary DVs are computed with the **same** formula / script for all rows in the matrix.
4. Stochastic models: either **N≥3** repetitions or explicit “exploratory” flag with no causal language.

---

## 7. Rejection conditions (do not draw strong conclusions)

Reject or downgrade claims when any of the following holds:

1. **Mixed SHA / model / dataset** between rows without a repeated `B-REF` on the new anchor.
2. **Infrastructure failures** counted as quality outcomes.
3. **Dataset items changed** mid-wave without restarting the matrix.
4. **p95 latency** above budget `L` on **B-REF** — fix infra or adjust `L` in a **new** wave; do not compare quality across violated latency gates.

---

## 8. Execution order

**Normative default:** fixed order `A2 → A1` (baseline without retrieval before full RAG) for H1-style questions to avoid **carry-over** surprises; for H2, increase k monotonically (`k1` then `k2`). Document any deviation.

---

## 9. Link to waves

| Wave file | Role |
| --- | --- |
| [`wave-pilot.md`](wave-pilot.md) | Smaller `D-pilot`, validates traceability. |
| [`wave-comparative.md`](wave-comparative.md) | Executes §5 rows on `D-full` or approved subset. |
