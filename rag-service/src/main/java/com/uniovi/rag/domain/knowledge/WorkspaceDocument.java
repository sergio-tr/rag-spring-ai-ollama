package com.uniovi.rag.domain.knowledge;

import java.util.UUID;

/**
 * Domain view of one row in {@code project_documents}. The single mapping from
 * {@code KnowledgeDocumentEntity} is {@link com.uniovi.rag.application.service.knowledge.WorkspaceDocumentMapper#fromEntity(com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity)}
 * (record cannot reference JPA per §6a).
 */
public record WorkspaceDocument(
        UUID id,
        UUID projectId,
        CorpusScope corpusScope,
        UUID conversationId,
        String contentChecksum,
        String storageUri,
        String mimeType,
        Long byteSize,
        UUID currentIndexSnapshotId,
        boolean requiresReindex
) {}
