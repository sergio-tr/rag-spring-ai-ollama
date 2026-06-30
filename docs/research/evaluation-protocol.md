# Evaluation protocol

**Purpose:** Normative methodology for layered evaluation before thesis campaigns: classifier → embeddings → retrieval → LLMs → presets → multiturn → final configuration.

**Status:** Gate audit 2026-06-29 (`evaluation-readiness-gate-20250629`). Classifier status frozen 2026-06-29 ([`classifier-status-freeze.md`](classifier-status-freeze.md)). **No campaigns** were run to produce this document.

**Related:** [`classifier-status-freeze.md`](classifier-status-freeze.md), [`evaluation-workbook-inventory.md`](evaluation-workbook-inventory.md), [`protocol-reproducibility.md`](protocol-reproducibility.md), [`experimental-design-matrix.md`](experimental-design-matrix.md), [`rag-service/README.md`](../../rag-service/README.md) (Lab benchmarks).

---

## Objectives

1. Establish whether each evaluation layer has **known** datasets, metrics, exports, and UI wiring before campaigns start.
2. Preserve **comparability**: fixed corpus, index snapshot, provider, models, prompts, and preset codes within a wave.
3. Record **provenance** (git SHA, dataset hash, resolved config, prompt bundle) in every export.
4. Defer full comparative claims until train/eval splits and index-per-embedding strategy are verified.

---

## Datasets

| Layer | Canonical source | Items | Notes |
| --- | --- | ---: | --- |
| Reference workbook | `rag-service/src/main/resources/evaluation/rag_experiment_datasets_and_protocols.xlsx` | LLM 36, embedding 60, RAG 60 | Parsed by `EvaluationReferenceBundleLoader` |
| Gold subset | `rag-service/src/main/resources/evaluation/gold-subset-v1.json` | 18 | Answerability / negative-evidence sentinel |
| Classifier train | `classifier-service/data/basic_dataset_qa_clasificacion.xlsx` | 46 | 12 `QueryType` classes; **leakage closed** (audit 2026-06-29) |
| Classifier eval | `classifier-service/data/evaluation_dataset.xlsx` | 60 | Balanced 5/class; held out; **0 train/eval overlaps** |
| Multiturn | `webapp/e2e/api/fixtures/e2e-multiturn-assertions.ts` | 8-turn suite | Chat E2E only; not a Lab benchmark dataset |

Record `content_sha256` per run sheet (see § Dataset manifest in [`protocol-reproducibility.md`](protocol-reproducibility.md)).

---

## Corpus

- Synthetic acta metadata: workbook sheet `corpus_documents` (5 documents).
- Gold chunks: `chunk_registry` (30 rows).
- Runtime evaluation corpus is user-bound via Lab **evaluation corpus** + **index snapshot**; readiness gated by `EvaluationCorpusReadinessService` / `CorpusAvailabilityGate`.
- Reindex is **per embedding model / snapshot** when vector rows are missing — not a global gate operation.

---

## Metrics

| Scope | Primary metrics | Export path |
| --- | --- | --- |
| Classifier | accuracy, macro-F1, weighted-F1, per-class precision/recall, confusion matrix | classifier-service `/evaluate` → Lab job result |
| Embedding retrieval | recall@1/3/5, MRR, nDCG@5, latency | `BenchmarkMvpMetricsCalculator` retrieval block |
| LLM reader (oracle context) | normalizedExactMatch, containsExpectedAnswer, semanticScore, correctness, faithfulness, hallucinationRate, latency | MVP generation block |
| RAG preset E2E | scoreGlobal, correctness, faithfulness, hallucinationRate, recall@5, MRR, nDCG@5, queryTypeMatch, abstentionCorrectness/abstentionScore, latencyMs | MVP + `EvaluationExportV1` |
| Multiturn (chat) | pass/fail per turn, reference resolution, sources, language coherence | E2E assertions only |

**Documented limitations (not blocking protocol definition):**

- `citationAccuracy` — **FUTURE_WORK** (not implemented as named export metric).
- `abstentionAccuracy` — exported as `abstentionCorrectness` / `abstentionScore` / `abstentionReason`.
- `precision@k` — `precision_at_k` in legacy aggregation; not consistently in MVP flat CSV as `precision@k`.
- `completeness` — not a named Lab rollup metric.

---

## Variables controlled (within a campaign row)

- Provider (`OPENAI_COMPATIBLE` / `OLLAMA_NATIVE`)
- Chat model, embedding model, judge model
- Classifier model id (when preset uses query understanding)
- Preset code(s) and resolved runtime capabilities
- Evaluation dataset id + SHA256
- Corpus id, index snapshot id, resolved config snapshot hash
- Prompt bundle version / SHA256
- Retrieval top-K and similarity thresholds from resolved config

---

## Variables independent (between campaign rows)

- Embedding model (requires isolated index when dimensions differ)
- Chat model
- Preset ladder (P0–P15)
- Classifier model version
- Judge model and task-specific LLM overrides

---

## Provider

- Lab model pickers use `LabEvaluationModelsService` → configured catalog for the **effective** provider (`ResolvedLlmConfig`).
- User-facing labels: **Configured API catalog** / **Local model server** (`productProviderLabel()`).
- Campaign rows must not mix providers within a single comparative matrix unless explicitly labeled as a cross-provider study.

---

## Model catalog

- Chat and embedding candidates listed in workbook sheets `llm_candidates` / `embedding_candidates`.
- Runtime catalog: admin properties + `EvaluationModelCatalogService`; no hardcoded LiteLLM model ids in Lab UI.
- Embedding compatibility: `compatibleWithCurrentVectorStore` flag per catalog entry.

**Candidate embedding models (deployment-dependent):**

- `hf.co/mixedbread-ai/mxbai-embed-large-v1:latest` / `mxbai-embed-large`
- `bge-m3`, `snowflake-arctic-embed2`, `multilingual-e5-large` (when configured)

---

## Embedding catalog

- Dimensions resolved via catalog heuristic or run entity override (`ExperimentalSnapshotFactory`).
- Prefix / normalization: **UNSUPPORTED** in experimental snapshot export (document before cross-model comparison).

---

## Prompt versions

- Bundled prompts fingerprinted via `PromptBundleFingerprint` (SHA256 + version + included groups).
- Project-level assistant instructions are separate from evaluation judge prompts (`EvaluationJudgePromptSources`).
- Exports include `promptProfileVersion`, `effectiveSystemPromptSha256`, `promptBundleSha256` when available.

---

## Preset versions

- Internal codes P0–P15 stable in DB migrations; user-facing **functional labels** via `productPresetLabel()`.
- Catalog export: `LabExperimentalPresetCatalogService` + workbook sheet `rag_preset_catalog_P0_P14`.
- Multi-turn presets (P13, P14) marked `requiresMultiTurn`; excluded from single-turn Lab RAG benchmark.

---

## Index snapshots

- Bound on `EvaluationRunEntity.indexSnapshot` with `signatureHash` / `indexProfileHash`.
- Export manifest bindings: `indexSnapshotId`, `resolvedConfigHash`, `datasetSha256`.
- `CorpusAvailabilityGate` blocks runs when vector rows missing (`SNAPSHOT_VECTOR_ROWS_MISSING`).

---

## Classifier versions

- Registry: `ClassifierModelRegistryService` + `classifier_model` table.
- Exports: `classifierModelId` on run entity and MVP flat row.
- Low-confidence / fallback: runtime `ClassifierDeterministicResolver` + `DefaultQueryClassifierAdapter`.

**Status freeze (2026-06-29):** See [`classifier-status-freeze.md`](classifier-status-freeze.md).

| Deployable model | Held-out macro-F1 | Accepted for production routing? |
| --- | ---: | --- |
| Keras `models/default` (train-only C1) | **0.013** | **No** — experimental only |
| Legacy Keras (train+eval leaked) | 0.369 | **No** — invalid inflated score |
| Offline sklearn C3 (char_wb + LinearSVC) | 0.797 | **No** — not served; research candidate |

**Campaign rule:** Embedding and LLM layers may proceed classifier-independently. RAG preset rows must disable, bypass, use deterministic-only routing, or explicitly document weak C1. No final RAG claim may attribute improvements to classifier until an accepted-model classifier campaign.

---

## Run commands

**Regression (no campaign):**

```bash
docker exec docker-backend-dev-1 sh -c 'cd /app && ./mvnw test \
  -Dtest="EvaluationExportV1Test,RagRuntimeProviderIntegrationTest,ModelRegistryServiceTest,LabEvaluationModelsServiceTest,PromptBundleFingerprintTest" \
  -Djacoco.skip=true'

cd webapp && npm test -- --run src/features/lab src/features/settings src/features/admin
```

**Lab async benchmarks (campaign — not run in readiness gate):**

- `POST {product}/lab/benchmarks/LLM_JUDGE_QA/runs`
- `POST {product}/lab/benchmarks/EMBEDDING_RETRIEVAL/runs`
- `POST {product}/lab/benchmarks/RAG_PRESET_END_TO_END/runs`
- Classifier: `POST {product}/lab/classifier/eval` (multipart Excel optional)

**Export after run:**

- `GET {product}/lab/runs/{id}/export` (JSON/CSV/ZIP v1)

---

## Export format

- Schema version **1** (`EvaluationExportV1Schema`).
- ZIP bundle: `results.json`, `summary.csv`, `evaluation_manifest.json`.
- Manifest sections: `identity`, `bindings`, `reproducibility`, `experimentalSnapshots`.
- Scores are **not recomputed** on export (`EvaluationExportV1Builder`).

---

## Decision criteria

| Verdict | When |
| --- | --- |
| **PASS** | All layers inventoried; metrics classified; export smoke green; no blocking leakage; provider audit clean |
| **CONDITIONAL_PASS** | Readiness **known** but at least one **campaign blocker** documented (e.g. classifier train/eval overlap) |
| **FAIL** | Unknown readiness, broken exports, or uninventoried datasets |

**Current gate verdict (2026-06-29):** **Classifier status freeze PASS** — embeddings and LLMs may proceed under [`classifier-status-freeze.md`](classifier-status-freeze.md) constraints. Classifier comparison campaign **postponed**.

---

## Limitations

1. Multiturn evaluation is **chat E2E only**; no Lab multiturn benchmark harness.
2. `citationAccuracy` not implemented.
3. Embedding prefix/normalization not captured in experimental snapshots.
4. Deployable classifier macro-F1 **0.013** on held-out eval — not production-quality (see classifier status freeze).
5. Dataset expansion planned (`proposed_train_expansion.json`) but **not applied**.
6. Live Lab dry-run against running stack was not executed for readiness gate (export proven via unit tests in container).

---

*Updated through Evaluation Readiness Gate and Classifier Status Freeze — audit/documentation only, no campaigns.*
