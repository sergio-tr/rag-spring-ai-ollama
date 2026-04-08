package com.uniovi.rag.domain.runtime.advisor;

/**
 * Advisor vocabulary. In microphase 5.2 only {@link #RETRIEVAL_ADVISOR} and {@link #CONTEXT_PACKING_ADVISOR} are executable.
 */
public enum AdvisorKind {
    RETRIEVAL_ADVISOR,
    CONTEXT_PACKING_ADVISOR,
    MEMORY_ADVISOR,
    ROUTING_ADVISOR
}
