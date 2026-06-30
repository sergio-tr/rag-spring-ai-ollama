package com.uniovi.rag.domain.llm.catalog;

import com.uniovi.rag.domain.llm.LlmProvider;
import java.util.Map;

/** Canonical catalog entry for a provider-scoped model. */
public record LlmCatalogEntry(
        LlmProvider provider,
        String modelName,
        LlmModelCapability capability,
        boolean available,
        boolean selectableByUser,
        boolean usableAsDefault,
        String displayName,
        String description,
        LlmCatalogSource source,
        Map<String, Object> metadata) {}
