package com.uniovi.rag.domain.knowledge;

/**
 * Precomputed outcome of {@code computeReindexDecision}; {@link com.uniovi.rag.application.service.knowledge.ReindexService}
 * branches only on this enum.
 */
public enum KnowledgeReindexKind {
    NO_OP,
    SOFT_REBUILD,
    HARD_REBUILD
}
