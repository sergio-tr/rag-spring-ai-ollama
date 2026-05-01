package com.uniovi.rag.domain.config.runtime;

import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;

import java.time.Instant;
import java.util.UUID;

/**
 * In-memory resolution artifact for preview, diagnostics, and reproducibility (not persisted in 2.1).
 */
public record ResolvedConfigSnapshot(
        UUID snapshotId,
        Instant createdAt,
        ResolvedRuntimeConfig resolvedRuntimeConfig,
        CapabilitySet capabilitySet,
        CompatibilityResult compatibility,
        ReindexImpact reindexImpact,
        String effectiveSystemPrompt,
        ConfigProvenance provenance) {

    public ResolvedConfigSnapshot {
        if (snapshotId == null || createdAt == null) {
            throw new IllegalArgumentException("snapshotId and createdAt are required");
        }
    }
}
