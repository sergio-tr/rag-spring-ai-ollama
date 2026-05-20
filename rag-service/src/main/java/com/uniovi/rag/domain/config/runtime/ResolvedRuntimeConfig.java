package com.uniovi.rag.domain.config.runtime;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.runtime.RagConfig;

/**
 * Normative aggregate for runtime configuration. Does not embed {@link
 * com.uniovi.rag.domain.config.prompt.PromptStack}.
 */
public record ResolvedRuntimeConfig(
        RagConfig resolvedCoreConfig,
        CapabilitySet capabilitySet,
        CompatibilityResult compatibility,
        ReindexImpact reindexImpact,
        SystemPromptLayers systemPromptLayers,
        String effectiveSystemPrompt,
        ConfigProvenance provenance,
        RagConfig configProjection) {

    /** Effective {@link RagConfig} for orchestrated execution (equals {@link #resolvedCoreConfig()} when unset). */
    public RagConfig toRagConfig() {
        return configProjection != null ? configProjection : resolvedCoreConfig;
    }
}
