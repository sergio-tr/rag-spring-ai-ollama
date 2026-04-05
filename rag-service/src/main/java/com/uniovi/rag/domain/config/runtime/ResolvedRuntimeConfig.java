package com.uniovi.rag.domain.config.runtime;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexPreview;
import com.uniovi.rag.domain.config.prompt.PromptStack;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.runtime.RagConfig;

import java.util.Optional;

/**
 * Fully resolved configuration for chat/lab, including legacy projection for the existing pipeline.
 */
public record ResolvedRuntimeConfig(
        RagConfig resolvedCoreConfig,
        CapabilitySet capabilitySet,
        CompatibilityResult compatibility,
        PromptStack promptStack,
        ReindexPreview reindexPreview,
        ConfigProvenance provenance,
        RagConfig legacyProjection
) {

    /**
     * Same effective {@link RagConfig} as consumed by {@link com.uniovi.rag.service.query.ProcessQueryService} today.
     */
    public RagConfig toRagConfig() {
        return legacyProjection != null ? legacyProjection : resolvedCoreConfig;
    }

    public Optional<ReindexPreview> reindexPreviewOptional() {
        return Optional.ofNullable(reindexPreview);
    }
}
