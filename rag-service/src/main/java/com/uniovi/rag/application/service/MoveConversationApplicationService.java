package com.uniovi.rag.application.service;

import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Moves a conversation to another owned project; updates CHAT_LOCAL {@code project_documents} rows without
 * re-embedding; clears {@code document_filter}; aligns CONVERSATION-scope index snapshots with the destination project.
 */
@Service
public class MoveConversationApplicationService {

    private final ConversationRepository conversationRepository;
    private final KnowledgeDocumentRepository knowledgeDocumentRepository;
    private final KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;
    private final ProjectAccessService projectAccessService;

    public MoveConversationApplicationService(
            ConversationRepository conversationRepository,
            KnowledgeDocumentRepository knowledgeDocumentRepository,
            KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository,
            ProjectAccessService projectAccessService) {
        this.conversationRepository = conversationRepository;
        this.knowledgeDocumentRepository = knowledgeDocumentRepository;
        this.knowledgeIndexSnapshotRepository = knowledgeIndexSnapshotRepository;
        this.projectAccessService = projectAccessService;
    }

    @Transactional
    public void moveConversationToProject(
            UUID userId, UUID sourceProjectId, UUID conversationId, UUID destinationProjectId) {
        ConversationEntity conversation = projectAccessService.requireConversationForUser(userId, conversationId);
        ProjectEntity sourceProject = conversation.getProject();
        if (!sourceProject.getId().equals(sourceProjectId)) {
            throw new NotFoundException("conversation not in project");
        }
        projectAccessService.requireOwnedProject(userId, sourceProject.getId());
        ProjectEntity dest = projectAccessService.requireOwnedProject(userId, destinationProjectId);
        conversation.setProject(dest);
        conversation.setDocumentFilter(List.of());
        conversation.touchUpdated();
        conversationRepository.save(conversation);
        knowledgeDocumentRepository.updateProjectForChatLocalDocuments(conversationId, dest.getId());
        knowledgeIndexSnapshotRepository.updateProjectIdForConversationSnapshots(
                conversationId, dest.getId(), Instant.now());
    }
}
