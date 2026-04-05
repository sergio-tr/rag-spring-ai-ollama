package com.uniovi.rag.domain.config.capability;

/**
 * Closed taxonomy grouping capabilities for validation and UX (microphase 2.1).
 */
public enum CapabilityGroup {
    WORKFLOW_FAMILY,
    QUERY_UNDERSTANDING,
    RETRIEVAL,
    POST_RETRIEVAL,
    TOOL_EXECUTION,
    ADVISOR,
    CLARIFICATION,
    MEMORY,
    JUDGE;

    /**
     * Each {@link Capability} maps to exactly one group.
     */
    public static CapabilityGroup forCapability(Capability c) {
        return switch (c) {
            case REASONING -> WORKFLOW_FAMILY;
            case EXPANSION, NER -> QUERY_UNDERSTANDING;
            case USE_RETRIEVAL, METADATA, NAIVE_FULL_CORPUS_PROMPT -> RETRIEVAL;
            case POST_RETRIEVAL, RANKER -> POST_RETRIEVAL;
            case TOOLS, FUNCTION_CALLING -> TOOL_EXECUTION;
            case USE_ADVISOR -> ADVISOR;
        };
    }
}
