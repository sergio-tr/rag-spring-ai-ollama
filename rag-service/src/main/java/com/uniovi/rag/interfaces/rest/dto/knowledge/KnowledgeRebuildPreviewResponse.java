package com.uniovi.rag.interfaces.rest.dto.knowledge;

import com.uniovi.rag.application.service.knowledge.KnowledgeRebuildPreviewResult;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.KnowledgeBuildProjection;
import com.uniovi.rag.domain.knowledge.KnowledgeReindexKind;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;

import java.util.UUID;

public record KnowledgeRebuildPreviewResponse(
        int projectionVersion,
        MaterializationStrategy materializationStrategy,
        int chunkMaxChars,
        int chunkOverlap,
        String embeddingModelId,
        boolean metadataExtractionEnabled,
        ReindexImpact reindexImpact,
        KnowledgeReindexKind reindexDecision,
        String configHash,
        CorpusScope corpusScope,
        UUID conversationId) {

    public static KnowledgeRebuildPreviewResponse from(
            KnowledgeRebuildPreviewResult result, CorpusScope corpusScope, UUID conversationId) {
        KnowledgeBuildProjection p = result.projection();
        return new KnowledgeRebuildPreviewResponse(
                p.projectionVersion(),
                p.materializationStrategy(),
                p.chunkMaxChars(),
                p.chunkOverlap(),
                p.embeddingModelId(),
                p.metadataExtractionEnabled(),
                p.reindexImpact(),
                result.decision().kind(),
                p.configHash(),
                corpusScope,
                conversationId);
    }
}
