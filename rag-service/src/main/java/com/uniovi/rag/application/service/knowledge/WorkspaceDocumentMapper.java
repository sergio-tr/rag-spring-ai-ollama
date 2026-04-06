package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.knowledge.WorkspaceDocument;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;

/**
 * Single mapping location: entity to domain record (Microphase 3.1).
 */
public final class WorkspaceDocumentMapper {

    private WorkspaceDocumentMapper() {}

    public static WorkspaceDocument fromEntity(KnowledgeDocumentEntity e) {
        return new WorkspaceDocument(
                e.getId(),
                e.getProject().getId(),
                e.getCorpusScope(),
                e.getConversation() != null ? e.getConversation().getId() : null,
                e.getContentChecksum(),
                e.getStorageUri(),
                e.getMimeType(),
                e.getByteSize(),
                e.getCurrentIndexSnapshot() != null ? e.getCurrentIndexSnapshot().getId() : null,
                e.isRequiresReindex());
    }
}
