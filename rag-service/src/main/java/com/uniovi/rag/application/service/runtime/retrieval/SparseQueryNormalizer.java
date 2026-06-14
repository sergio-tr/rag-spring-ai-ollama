package com.uniovi.rag.application.service.runtime.retrieval;

/**
 * Normalizes user/rewritten query text before PostgreSQL full-text sparse retrieval.
 */
public final class SparseQueryNormalizer {

    private SparseQueryNormalizer() {}

    public static String normalize(String raw) {
        return SpanishRetrievalTextSupport.normalize(raw);
    }
}
