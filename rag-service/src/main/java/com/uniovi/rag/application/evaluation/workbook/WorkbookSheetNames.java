package com.uniovi.rag.application.evaluation.workbook;

/** Canonical sheet names for experimental XLSX workbooks (must match templates). */
public final class WorkbookSheetNames {

    public static final String README = "README";
    public static final String CORPUS_DOCUMENTS = "corpus_documents";
    public static final String CHUNK_REGISTRY = "chunk_registry";
    public static final String LLM_READER_QUESTIONS = "llm_reader_questions";
    public static final String EMBEDDING_RETRIEVAL_QUERIES = "embedding_retrieval_queries";
    public static final String RAG_PRESET_QUESTIONS_ENRICHED = "rag_preset_questions_enriched";
    public static final String LLM_CANDIDATES = "llm_candidates";
    public static final String EMBEDDING_CANDIDATES = "embedding_candidates";
    public static final String RAG_PRESET_CATALOG_P0_P14 = "rag_preset_catalog_P0_P14";
    public static final String METRIC_SPEC = "metric_spec";
    public static final String RESULT_SCHEMA = "result_schema";
    public static final String SUMMARY_COUNTS = "summary_counts";

    private WorkbookSheetNames() {}
}
