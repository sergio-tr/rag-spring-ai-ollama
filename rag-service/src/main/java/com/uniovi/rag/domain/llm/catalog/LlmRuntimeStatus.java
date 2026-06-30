package com.uniovi.rag.domain.llm.catalog;

/** Runtime reachability of a catalog entry (properties-only phase uses {@link #UNKNOWN}). */
public enum LlmRuntimeStatus {
    UNKNOWN,
    AVAILABLE,
    UNAVAILABLE,
    ERROR
}
