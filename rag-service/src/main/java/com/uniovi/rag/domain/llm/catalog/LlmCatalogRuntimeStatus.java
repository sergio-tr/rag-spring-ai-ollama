package com.uniovi.rag.domain.llm.catalog;

/** Runtime probe outcome for a configured catalog model. */
public enum LlmCatalogRuntimeStatus {
    UNKNOWN,
    CONFIGURED,
    NOT_PROBED,
    AVAILABLE,
    UNAVAILABLE,
    PROBE_FAILED
}
