# Pilot wave — W-PILOT-001

**Objective:** Validate **traceability** end-to-end (anchors recorded, artefacts locatable) on a **small** surface before scaling to comparative loads. Success is **operational**, not maximum benchmark scores.

**Frozen git SHA:** `7098c40d975803a2ddb30fe897d8d8b8d98d8100` (recorded at pilot authoring time).

**Primary hypothesis under test:** H1 (see [`experimental-design-matrix.md`](experimental-design-matrix.md)) on a **pilot subset** of items only after Lab execution is available.

**Dataset (pilot, historical):** `EVAL-XLSX-CLASSPATH-V1` pointed at a **legacy** single-sheet workbook (`evaluation_dataset.xlsx`) removed from prod classpath in **Phase L** (2026-05-04). Current waves should anchor on `rag_experiment_datasets_and_protocols.xlsx` + typed uploads; keep this pilot row as **frozen historical** evidence only.

**Gates before Lab metrics:**

| Gate | Result | Evidence |
| --- | --- | --- |
| G-build (`./mvnw clean verify` in `rag-service/`) | **PASS** | Run **RUN-PILOT-G-BUILD-001** below; executed 2026-04-19, exit code 0 (~55s). |
| G-integration | **Not required** for this pilot protocol row | Documented limitation: stack integration not executed as part of this documentation commit. |
| G-bench-schema | **Not required** until micro-benchmarks are run | N/A for first pilot slice. |

---

## Run record — RUN-PILOT-G-BUILD-001

| Field | Value |
| --- | --- |
| `run_id` | RUN-PILOT-G-BUILD-001 |
| `wave_id` | W-PILOT-001 |
| `git_sha_full` | `7098c40d975803a2ddb30fe897d8d8b8d98d8100` |
| `operator` | repository automation / maintainer |
| `started_at_utc` | 2026-04-19 (session) |
| `finished_at_utc` | 2026-04-19 (session) |
| `hypothesis_ids` | *(methodology gate — no hypothesis test)* |
| `benchmark_kind` | `MAVEN_VERIFY` (methodology analogue to G-build) |

**Environment**

| Field | Value |
| --- | --- |
| Spring profiles | `test` (Maven test phase); not a deployed Lab stack |
| Ollama | Not invoked (stubbed/mocked per test harness) |
| Command | `cd rag-service && ./mvnw clean verify` |

**Outcome**

| Field | Value |
| --- | --- |
| `outcome` | `SUCCESS` |
| Primary metric | Maven exit code `0` |
| Notes | Full Surefire + JaCoCo gate per `pom.xml`; confirms code quality baseline for this SHA. |

**Artefacts**

| Artefact | Location |
| --- | --- |
| Surefire / JaCoCo | `rag-service/target/` (local rebuild; not committed) |

---

## Run record — RUN-PILOT-LAB-001 (pre-execution checklist)

**Purpose:** Single Lab smoke run on **subset** or full classpath-derived evaluation once stack is up. All **required** fields are filled; Lab-specific ids remain **TBD** until execution.

| Field | Value |
| --- | --- |
| `run_id` | RUN-PILOT-LAB-001 |
| `wave_id` | W-PILOT-001 |
| `git_sha_full` | `7098c40d975803a2ddb30fe897d8d8b8d98d8100` |
| `operator` | *(assign before run)* |
| `benchmark_kind` | `LLM_JUDGE_QA` *(recommended first smoke — no retrieval dependency)* or `RAG_PRESET_END_TO_END` if snapshot already created |

**Dataset manifest**

| Field | Value |
| --- | --- |
| `dataset_id` | EVAL-XLSX-CLASSPATH-V1 |
| `content_sha256` | `4b525f4341cf57fb6275e709555dd6030318e516ade60abef420cee4f97b5b3d` |
| `domain_notes` | Minutes-style items; language and domain bias per workbook content — declare in thesis. |

**Execution identifiers (fill on run)**

| Field | Value |
| --- | --- |
| Lab `evaluation_run.id` | `TBD` |
| Export `sha256` / path | `TBD` |

**Outcome**

| Field | Value |
| --- | --- |
| `outcome` | `PARTIAL` *(run sheet complete; Lab metrics pending operator execution on a live stack)* |
| Notes | Execute against Docker stack per `docker/README.md`; poll `GET {product}/lab/jobs/{jobId}`; then export run. After execution, set `outcome` to `SUCCESS` or `INFRA_FAILED` and fill `evaluation_run.id` plus export `sha256`. |

---

## Reproducibility lessons (pilot)

1. **Anchor first:** Recording `git_sha_full` and dataset `content_sha256` before running Lab avoids post-hoc confusion when branches move.
2. **Separate quality gates from science metrics:** `mvn verify` success does not imply Lab success — keep run ids distinct (**RUN-PILOT-G-BUILD-001** vs **RUN-PILOT-LAB-001**).
3. **Ollama readiness:** Compose readiness vs liveness — document which probe you waited on (`rag-service/README.md` notes `readiness` vs `liveness`).
4. **Stochasticity:** If the pilot uses a non-deterministic chat model, set **N≥3** on the first real Lab execution or mark results exploratory only.
5. **Export preservation:** Keep `#META:` header line with CSV exports — thesis appendices should store immutable copies.

---

## Pilot closure checklist

- [x] Inventory and protocol cross-linked from [`README.md`](README.md).
- [x] G-build evidence recorded for SHA `7098c40d975803a2ddb30fe897d8d8b8d98d8100`.
- [ ] Lab smoke executed and **RUN-PILOT-LAB-001** outcome updated from `PARTIAL` to final status *(operator)*.
- [ ] Export file registered with `sha256` *(operator)*.
