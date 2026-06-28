package com.uniovi.rag.domain.runtime.retrieval;

/** Winning sparse retrieval strategy stage for telemetry. */
public enum SparseRetrievalFallbackStage {
    EXACT_PHRASE,
    AND_KEYWORDS,
    OR_KEYWORDS,
    UNACCENT_OR,
    ILIKE,
    NO_HIT
}
