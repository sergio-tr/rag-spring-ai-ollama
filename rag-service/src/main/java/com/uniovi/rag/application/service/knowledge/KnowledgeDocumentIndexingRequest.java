package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;

import java.nio.file.Path;

/**
 * Parameters for {@link KnowledgeIndexingService#processDocument(KnowledgeDocumentIndexingRequest)}.
 */
public record KnowledgeDocumentIndexingRequest(
        KnowledgeDocumentEntity doc,
        Path tempFileOverride,
        String originalFilename,
        String contentType,
        KnowledgeIndexSnapshotEntity snapshot,
        String indexSigHex,
        MaterializationStrategy strategy,
        int effectiveChunkMaxChars,
        ProjectIndexProfileResolver.ResolvedIngestionIndexProfile ingestionProfile) {

    public KnowledgeDocumentIndexingRequest(
            KnowledgeDocumentEntity doc,
            Path tempFileOverride,
            String originalFilename,
            String contentType,
            KnowledgeIndexSnapshotEntity snapshot,
            String indexSigHex,
            MaterializationStrategy strategy,
            int effectiveChunkMaxChars) {
        this(
                doc,
                tempFileOverride,
                originalFilename,
                contentType,
                snapshot,
                indexSigHex,
                strategy,
                effectiveChunkMaxChars,
                null);
    }
}
