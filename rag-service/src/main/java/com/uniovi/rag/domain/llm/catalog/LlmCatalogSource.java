package com.uniovi.rag.domain.llm.catalog;

/** Origin of a catalog entry. */
public enum LlmCatalogSource {
    /** @deprecated use {@link #CONFIGURED_CATALOG} or {@link #LITELLM_CONFIGURED} */
    PROPERTIES,
    CONFIGURED_CATALOG,
    LITELLM_CONFIGURED,
    OLLAMA_LIVE,
    UNKNOWN
}
