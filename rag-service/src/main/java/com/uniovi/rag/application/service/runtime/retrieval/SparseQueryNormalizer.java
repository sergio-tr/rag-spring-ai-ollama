package com.uniovi.rag.application.service.runtime.retrieval;

/**
 * Normalizes user/rewritten query text before PostgreSQL full-text sparse retrieval.
 */
public final class SparseQueryNormalizer {

    private SparseQueryNormalizer() {}

    public static String normalize(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        String collapsed = trimmed.replaceAll("\\s+", " ");
        return collapsed.replaceAll("^[^\\p{L}\\p{N}]+|[^\\p{L}\\p{N}]+$", "").trim();
    }
}
