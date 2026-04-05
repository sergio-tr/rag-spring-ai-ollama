package com.uniovi.rag.application.port;

import java.util.Set;

/**
 * Governance view of which LLM model names may be selected at runtime (allowlist rows).
 */
public interface ModelCatalogPort {

    /**
     * LLM names marked in_allowlist in {@code allowed_model}. Empty means no extra restriction on overrides.
     */
    Set<String> allowedLlmNamesInGovernance();
}
