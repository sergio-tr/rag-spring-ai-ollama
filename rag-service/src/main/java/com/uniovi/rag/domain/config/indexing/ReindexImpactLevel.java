package com.uniovi.rag.domain.config.indexing;

/**
 * Semantic impact of a configuration change on indexing / materialization.
 */
public enum ReindexImpactLevel {
    NO_REINDEX,
    SOFT_REINDEX,
    HARD_REINDEX
}
