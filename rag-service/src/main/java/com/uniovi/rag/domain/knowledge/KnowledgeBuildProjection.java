package com.uniovi.rag.domain.knowledge;

import com.uniovi.rag.domain.config.indexing.ReindexImpact;

import java.util.UUID;

/**
 * Versioned, hash-stable knowledge build inputs derived only via {@link com.uniovi.rag.application.service.knowledge.KnowledgeBuildProjectionMapper}.
 */
public record KnowledgeBuildProjection(
        int projectionVersion,
        MaterializationStrategy materializationStrategy,
        int chunkMaxChars,
        int chunkOverlap,
        String embeddingModelId,
        boolean metadataExtractionEnabled,
        ReindexImpact reindexImpact,
        UUID resolvedConfigSnapshotId,
        String configHash) {

    public KnowledgeBuildProjection {
        reindexImpact = reindexImpact == null ? ReindexImpact.none() : reindexImpact;
        embeddingModelId = embeddingModelId != null ? embeddingModelId : "";
        configHash = configHash != null ? configHash : "";
    }
}
