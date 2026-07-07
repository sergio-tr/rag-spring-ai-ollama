package com.uniovi.rag.application.port;

import java.util.Set;

/**
 * Governance view of explicitly blocked model names in {@code allowed_model}.
 * Models are allowed by default when present in the merged catalog unless listed here.
 */
public interface ModelCatalogPort {

    /**
     * LLM names marked {@code in_allowlist=false} in {@code allowed_model}.
     */
    Set<String> blockedLlmNamesInGovernance();

    /**
     * Embedding names marked {@code in_allowlist=false} in {@code allowed_model}.
     */
    Set<String> blockedEmbeddingNamesInGovernance();
}
