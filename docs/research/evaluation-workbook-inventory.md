# Evaluation workbook inventory — Phase 0

**Branch:** `eval-models-and-presets`  
**Artifact:** [`rag-service/src/main/resources/evaluation/rag_experiment_datasets_and_protocols.xlsx`](../../rag-service/src/main/resources/evaluation/rag_experiment_datasets_and_protocols.xlsx)  
**SHA256:** `4c38774e73cd801abdaa8c20c917bbbb84dec1dda3181d2ab5cd3f40a5ae4ac6`  
**Generated:** 2026-05-01 (Phase 0 repo truth; inspection via `openpyxl`, read-only).

This note records sheet structure and row counts for parser design (Phase 1). It does **not** replace module README how-tos.

## Workbook summary

| Sheet | Header columns (row 1) | Data rows (excl. header) | Notes |
|-------|--------------------------|---------------------------|--------|
| README | Item, Decision | 6 | Protocol prose / decisions |
| corpus_documents | document_id, source_file, date, place, start_time, end_time, attendees_count, president, secretary, topics | 5 | Synthetic acta metadata |
| chunk_registry | chunk_id, document_id, chunk_type, gold_evidence_text | 30 | Gold chunks |
| llm_reader_questions | id, question, context_text, expected_answer, query_type, difficulty, answer_mode, source_document_id, gold_evidence, unanswerable, evaluation_method | 36 | LLM baseline sheet |
| embedding_retrieval_queries | id, query, query_variant_type, query_type, difficulty, expected_answer, gold_document_ids, gold_chunk_ids, must_retrieve_any, must_retrieve_all, notes | 60 | Embedding / retrieval eval |
| rag_preset_questions_enriched | id, question, expected_answer, query_type, difficulty, answer_mode, gold_document_ids, gold_chunk_ids, expected_evidence_count, unanswerable, requires_multi_document, … | 60 | RAG preset questions (wide header) |
| llm_candidates | candidate_id, model, role, priority, expected_fit, hardware_note, protocols | 5 | Model names for thesis |
| embedding_candidates | candidate_id, model, role, priority, expected_fit, profile_notes, protocols | 4 | Embedding models |
| rag_preset_catalog_P0_P14 | preset_id, family, name, retrieval, query_understanding, tools, memory, judges, main_or_complement, objective, dataset_policy | 15 | **P0–P14** definitions |
| metric_spec | metric_id, scope, description, primary_for, formula_or_rule | 17 | Metric definitions |
| result_schema | field, type, required, description | 15 | Export schema hints |
| summary_counts | Dataset, Rows, Purpose, Primary branch | 5 | Cross-sheet counts |

**Empty sheets:** none (all listed sheets contain at least one data row).

**summary_counts vs scans:** summary lists `llm_reader_questions` = 36 rows, `embedding_retrieval_queries` / `rag_preset_questions_enriched` = 60 — consistent with row scans above.

**Inconsistencies / cautions:**

- `README` uses columns Item/Decision — not the same schema as data sheets (expected).
- `rag_preset_questions_enriched` header row is wide (14+ columns); confirm full header list in Phase 1 when parsing by name.
- Semicolon-separated lists appear in `gold_document_ids` / `gold_chunk_ids` (e.g. `ACTA_1;ACTA_6`).

## Sample rows (truncated)

- **llm_reader_questions:** `LLM-001` — GET_FIELD, LOW, EXACT_ENTITY, evaluation_method `normalized_exact_match`.
- **embedding_retrieval_queries:** `EMB-001` — COUNT_DOCUMENTS, gold chunks `ACTA_1_ELEVATOR_PAINT;ACTA_6_ELEVATOR`.
- **rag_preset_catalog_P0_P14:** P0 — Direct LLM, retrieval NONE; P14 — judges column references sufficiency/faithfulness/stop/continue.

## Legacy classpath workbook (removed from prod)

The historical single-sheet **`evaluation/evaluation_dataset.xlsx`** is **not** shipped under `rag-service/src/main/resources` for runtime Lab benchmarks (**Phase L**, 2026-05-04). Typed evaluation uses the internal reference workbook only + user uploads validated against templates; see [`rag-service/README.md`](../../rag-service/README.md) Lab section.

**Gold sentinel:** workbook rows may use `NONE` in `gold_chunk_ids`; validators treat it as “no chunk” (not a registry lookup).

## Phase 2 / Phase L (2026-05-04)

[`EvaluationReferenceBundleLoader`](../../rag-service/src/main/java/com/uniovi/rag/application/evaluation/workbook/EvaluationReferenceBundleLoader.java) parses `REFERENCE_BUNDLE`, exposes counts and validation for `/lab/status`. Legacy Map projection via **`DatasetMinuteEvaluationService`** was removed; **`LegacyQuestionsAdapter`** is test/historical-only where retained.

## References

- Master plan: [`.cursor/plans/eval_models_and_presets.plan.md`](../../.cursor/plans/eval_models_and_presets.plan.md) (Phase 0 completed 2026-05-01).
