package com.uniovi.rag.application.service.evaluation.metrics;

/** Gold-label retrieval quality computation state (distinct from runtime coverage). */
public enum RetrievalQualityStatus {
    COMPUTED,
    NOT_AVAILABLE,
    GOLD_UNRESOLVED
}
