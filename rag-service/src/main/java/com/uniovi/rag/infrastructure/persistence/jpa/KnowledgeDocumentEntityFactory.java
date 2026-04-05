package com.uniovi.rag.infrastructure.persistence.jpa;

import com.uniovi.rag.domain.ProjectDocumentStatus;
import com.uniovi.rag.domain.knowledge.CorpusScope;

import java.time.Instant;

/**
 * Factory for {@link KnowledgeDocumentEntity} (minimal surface for ingestion).
 */
public final class KnowledgeDocumentEntityFactory {

    private KnowledgeDocumentEntityFactory() {
    }

    public static KnowledgeDocumentEntity newIngesting(ProjectEntity project, String fileName) {
        KnowledgeDocumentEntity e = new KnowledgeDocumentEntity();
        e.setProject(project);
        e.setCorpusScope(CorpusScope.PROJECT_SHARED);
        e.setFileName(fileName);
        e.setStatus(ProjectDocumentStatus.INGESTING);
        e.setUploadedAt(Instant.now());
        return e;
    }

    public static KnowledgeDocumentEntity newChatLocalIngesting(
            ProjectEntity project, ConversationEntity conversation, String fileName) {
        KnowledgeDocumentEntity e = new KnowledgeDocumentEntity();
        e.setProject(project);
        e.setConversation(conversation);
        e.setCorpusScope(CorpusScope.CHAT_LOCAL);
        e.setFileName(fileName);
        e.setStatus(ProjectDocumentStatus.INGESTING);
        e.setUploadedAt(Instant.now());
        return e;
    }
}
