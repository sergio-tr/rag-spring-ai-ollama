package com.uniovi.rag.application.service.runtime.retrieval;

/**
 * Frozen retrieval limits (RRF k, dense overfetch).
 */
public final class RetrievalPolicy {

    /** Reciprocal Rank Fusion {@code k} (same as dense rank proxy denominator offset). */
    public static final int RRF_K = 60;

    private RetrievalPolicy() {}

    /**
     * Dense vector search overfetch before snapshot filtering: {@code min(max(topK * 5, 50), 500)}.
     */
    public static int denseFetchLimit(int topK) {
        int tk = topK > 0 ? topK : 10;
        return Math.min(Math.max(tk * 5, 50), 500);
    }
}
