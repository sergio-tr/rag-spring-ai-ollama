package com.uniovi.rag.domain.evaluation.workbook;

/**
 * Experimental workbook classification for parsing and validation.
 * Distinct from persisted {@link com.uniovi.rag.domain.EvaluationDatasetType} (RAG / LLM_ONLY / CLASSIFIER).
 */
public enum ExperimentalDatasetType {
    /** Full internal bundle with all protocol sheets. */
    REFERENCE_BUNDLE,
    /** Primary sheet {@code llm_reader_questions}. */
    LLM_MODEL_BASELINE,
    /** {@code embedding_retrieval_queries} + {@code chunk_registry} (+ optional corpus). */
    EMBEDDING_MODEL_BASELINE,
    /** {@code rag_preset_questions_enriched} (+ optional registry/corpus). */
    RAG_PRESET_BENCHMARK,
    /** Classifier contract: columns Question + QueryType (typically one sheet). */
    CLASSIFIER_DATASET
}
