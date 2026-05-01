package com.uniovi.rag.domain.config.capability;

/**
 * Normalized capability flags derived from {@link com.uniovi.rag.domain.runtime.RagConfig} and profiles.
 */
public enum Capability {
    EXPANSION,
    NER,
    TOOLS,
    METADATA,
    REASONING,
    RANKER,
    POST_RETRIEVAL,
    FUNCTION_CALLING,
    USE_RETRIEVAL,
    USE_ADVISOR,
    NAIVE_FULL_CORPUS_PROMPT
}
