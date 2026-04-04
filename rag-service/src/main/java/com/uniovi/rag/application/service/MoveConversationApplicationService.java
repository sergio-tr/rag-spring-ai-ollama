package com.uniovi.rag.application.service;

import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.service.project.ProjectAccessService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Moves a conversation to another project; updates CHAT_LOCAL document rows without re-embedding (DC-08).
 */
@Service
public class MoveConversationApplicationService {

    private final ConversationRepository conversationRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final ProjectAccessService projectAccessService;

    public MoveConversationApplicationService(
            ConversationRepository conversationRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            ProjectAccessService projectAccessService) {
        this.conversationRepository = conversationRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.projectAccessService = projectAccessService;
    }

    @Transactional
    public void moveConversationToProject(
            UUID userId, UUID sourceProjectId, UUID conversationId, UUID destinationProjectId) {
        ConversationEntity conversation = projectAccessService.requireConversationForUser(userId, conversationId);
        ProjectEntity sourceProject = conversation.getProject();
        if (!sourceProject.getId().equals(sourceProjectId)) {
            throw new com.uniovi.rag.interfaces.rest.NotFoundException("conversation not in project");
        }
        projectAccessService.requireOwnedProject(userId, sourceProject.getId());
        ProjectEntity dest = projectAccessService.requireOwnedProject(userId, destinationProjectId);
        conversation.setProject(dest);
        conversationRepository.save(conversation);
        knowledgeDocumentRepository.updateProjectForChatLocalDocuments(conversationId, dest.getId());
    }
}
