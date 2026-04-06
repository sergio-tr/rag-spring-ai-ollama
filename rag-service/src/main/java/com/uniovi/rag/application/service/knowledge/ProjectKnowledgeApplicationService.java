package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeSnapshotDocumentRepository;
import com.uniovi.rag.infrastructure.persistence.mapper.KnowledgeIndexSnapshotMapper;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.interfaces.rest.dto.knowledge.KnowledgeSnapshotDetailResponse;
import com.uniovi.rag.interfaces.rest.dto.knowledge.KnowledgeSnapshotSummaryResponse;
import com.uniovi.rag.service.project.ProjectAccessService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class ProjectKnowledgeApplicationService {

    private final KnowledgeIndexSnapshotRepository snapshotRepository;
    private final KnowledgeSnapshotDocumentRepository snapshotDocumentRepository;
    private final ProjectAccessService projectAccessService;
    private final ReindexService reindexService;

    public ProjectKnowledgeApplicationService(
            KnowledgeIndexSnapshotRepository snapshotRepository,
            KnowledgeSnapshotDocumentRepository snapshotDocumentRepository,
            ProjectAccessService projectAccessService,
            ReindexService reindexService) {
        this.snapshotRepository = snapshotRepository;
        this.snapshotDocumentRepository = snapshotDocumentRepository;
        this.projectAccessService = projectAccessService;
        this.reindexService = reindexService;
    }

    public List<KnowledgeSnapshotSummaryResponse> listSnapshots(
            UUID userId, UUID projectId, CorpusScope corpusScope, UUID conversationId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        if (corpusScope == CorpusScope.PROJECT_SHARED) {
            if (conversationId != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId must be absent for PROJECT_SHARED");
            }
            return snapshotRepository
                    .findByProjectAndScopeProjectOrderByCreatedAtDesc(projectId, KnowledgeSnapshotScopeType.PROJECT)
                    .stream()
                    .map(KnowledgeIndexSnapshotMapper::toDomain)
                    .map(KnowledgeSnapshotSummaryResponse::fromDomain)
                    .toList();
        }
        if (conversationId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId required for CHAT_LOCAL");
        }
        projectAccessService.requireConversationForUser(userId, conversationId);
        return snapshotRepository
                .findByConversationAndScopeOrderByCreatedAtDesc(conversationId, KnowledgeSnapshotScopeType.CONVERSATION)
                .stream()
                .map(KnowledgeIndexSnapshotMapper::toDomain)
                .map(KnowledgeSnapshotSummaryResponse::fromDomain)
                .toList();
    }

    public KnowledgeSnapshotDetailResponse getSnapshot(
            UUID userId, UUID projectId, UUID snapshotId, CorpusScope corpusScope, UUID conversationId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        KnowledgeIndexSnapshotEntity e =
                snapshotRepository.findById(snapshotId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!e.getProject().getId().equals(projectId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (corpusScope == CorpusScope.PROJECT_SHARED) {
            if (e.getConversation() != null || e.getScopeType() != KnowledgeSnapshotScopeType.PROJECT) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        } else {
            if (conversationId == null
                    || e.getConversation() == null
                    || !e.getConversation().getId().equals(conversationId)
                    || e.getScopeType() != KnowledgeSnapshotScopeType.CONVERSATION) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
            }
        }
        long docCount = snapshotDocumentRepository.countBySnapshot_Id(snapshotId);
        return KnowledgeSnapshotDetailResponse.fromDomain(KnowledgeIndexSnapshotMapper.toDomain(e), docCount);
    }

    /**
     * Operator reindex trigger: reserved for full-scope rebuild (config integration).
     */
    public void triggerReindex(UUID userId, UUID projectId, CorpusScope corpusScope, UUID conversationId) {
        projectAccessService.requireOwnedProject(userId, projectId);
        if (corpusScope == CorpusScope.CHAT_LOCAL) {
            if (conversationId == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId required for CHAT_LOCAL");
            }
            projectAccessService.requireConversationForUser(userId, conversationId);
        } else if (conversationId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId must be absent for PROJECT_SHARED");
        }
        reindexService.enqueueOperatorReindex(projectId, corpusScope, conversationId);
    }
}
