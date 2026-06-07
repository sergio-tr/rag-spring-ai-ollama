package com.uniovi.rag.domain.evaluation;

import java.util.Locale;

/**
 * Lab baseline protocols (Phase 5). LLM rows resolve from {@code answer_mode}; embedding rows use pure retrieval or
 * downstream RAG based on run flags.
 */
public enum BenchmarkEvaluationProtocol {
    LLM_READER_ORACLE_CONTEXT,
    LLM_FULL_DOCUMENT_CONTEXT,
    EMBEDDING_RETRIEVAL_PURE,
    EMBEDDING_DOWNSTREAM_RAG;

    /**
     * Maps workbook {@code answer_mode} (and common synonyms) to a protocol; defaults to oracle reader context.
     */
    public static BenchmarkEvaluationProtocol fromAnswerMode(String answerMode) {
        if (answerMode == null || answerMode.isBlank()) {
            return LLM_READER_ORACLE_CONTEXT;
        }
        String n = answerMode.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        try {
            return valueOf(n);
        } catch (IllegalArgumentException ignored) {
            // Synonyms / synonym labels
            if (n.contains("FULL") && (n.contains("DOC") || n.contains("CORPUS"))) {
                return LLM_FULL_DOCUMENT_CONTEXT;
            }
            if (n.contains("ORACLE") || n.contains("READER") || n.contains("CONTEXT")) {
                return LLM_READER_ORACLE_CONTEXT;
            }
            return LLM_READER_ORACLE_CONTEXT;
        }
    }
}
