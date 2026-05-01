package com.uniovi.rag.service.project;

import com.uniovi.rag.interfaces.rest.NotFoundException;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Ensures the current user owns the project or related resources (404 if not).
 */
@Service
public class ProjectAccessService {

    private final ProjectRepository projectRepository;
    private final ConversationRepository conversationRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;

    public ProjectAccessService(
            ProjectRepository projectRepository,
            ConversationRepository conversationRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository) {
        this.projectRepository = projectRepository;
        this.conversationRepository = conversationRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
    }

    public ProjectEntity requireOwnedProject(UUID userId, UUID projectId) {
        return projectRepository.findByIdAndOwner_Id(projectId, userId)
                .orElseThrow(() -> new NotFoundException("project not found"));
    }

    public ConversationEntity requireConversationForUser(UUID userId, UUID conversationId) {
        return conversationRepository.findByIdAndUser_Id(conversationId, userId)
                .orElseThrow(() -> new NotFoundException("conversation not found"));
    }

    public KnowledgeDocumentEntity requireDocumentForUser(UUID userId, UUID documentId) {
        KnowledgeDocumentEntity doc = knowledgeDocumentRepository.findById(documentId)
                .orElseThrow(() -> new NotFoundException("document not found"));
        requireOwnedProject(userId, doc.getProject().getId());
        return doc;
    }
}
