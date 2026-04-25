package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.KnowledgeBuildProjection;
import com.uniovi.rag.domain.knowledge.KnowledgeReindexDecision;
import com.uniovi.rag.domain.knowledge.KnowledgeReindexKind;
import com.uniovi.rag.domain.knowledge.ReindexEventReason;
import com.uniovi.rag.domain.knowledge.ReindexEventStatus;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.ReindexEventRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ConversationEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ReindexEventEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Owns {@code reindex_event} rows; executes precomputed {@link KnowledgeReindexDecision} only.
 */
@Service
public class ReindexService {

    private final ReindexEventRepository reindexEventRepository;
    private final ProjectRepository projectRepository;
    private final ConversationRepository conversationRepository;
    private final KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;

    public ReindexService(
            ReindexEventRepository reindexEventRepository,
            ProjectRepository projectRepository,
            ConversationRepository conversationRepository,
            KnowledgePipelineOrchestrator knowledgePipelineOrchestrator) {
        this.reindexEventRepository = reindexEventRepository;
        this.projectRepository = projectRepository;
        this.conversationRepository = conversationRepository;
        this.knowledgePipelineOrchestrator = knowledgePipelineOrchestrator;
    }

    /**
     * Runs synchronous scope rebuild when {@code decision} requests pipeline work; otherwise no-op.
     */
    @Transactional
    public KnowledgeReindexExecutionResult executeKnowledgeReindexDecision(
            KnowledgeReindexDecision decision,
            KnowledgeBuildProjection projection,
            UUID projectId,
            CorpusScope corpusScope,
            UUID conversationId,
            UUID resolvedConfigSnapshotId) {
        if (decision == null || decision.kind() == KnowledgeReindexKind.NO_OP) {
            return KnowledgeReindexExecutionResult.NONE;
        }
        if (!knowledgePipelineOrchestrator.hasReadyDocumentsInScope(projectId, corpusScope, conversationId)) {
            return KnowledgeReindexExecutionResult.NONE;
        }
        ProjectEntity project = projectRepository.getReferenceById(projectId);
        ConversationEntity conversation = resolveConversation(corpusScope, conversationId);
        String targetSig =
                knowledgePipelineOrchestrator.previewSnapshotSignatureHex(
                        projectId, corpusScope, conversationId, projection);
        String reason =
                decision.kind() == KnowledgeReindexKind.HARD_REBUILD
                        ? ReindexEventReason.CONFIG_HARD
                        : ReindexEventReason.CONFIG_SOFT;
        ReindexEventEntity ev =
                ReindexEventEntity.newPending(
                        project,
                        conversation,
                        null,
                        reason,
                        targetSig,
                        ReindexEventStatus.PENDING,
                        resolvedConfigSnapshotId);
        ev = reindexEventRepository.save(ev);
        try {
            updateReindexEventStatus(ev.getId(), ReindexEventStatus.RUNNING);
            UUID knowledgeSnapshotId =
                    knowledgePipelineOrchestrator.rebuildScope(
                            projectId, corpusScope, conversationId, projection, resolvedConfigSnapshotId);
            updateReindexEventStatus(ev.getId(), ReindexEventStatus.COMPLETED);
            return new KnowledgeReindexExecutionResult(ev.getId(), knowledgeSnapshotId);
        } catch (RuntimeException e) {
            updateReindexEventStatus(ev.getId(), ReindexEventStatus.FAILED);
            throw e;
        }
    }

    @Transactional
    public void updateReindexEventStatus(UUID eventId, ReindexEventStatus status) {
        reindexEventRepository
                .findById(eventId)
                .ifPresent(
                        ev -> {
                            ev.setStatus(status);
                            ev.setUpdatedAt(Instant.now());
                            reindexEventRepository.save(ev);
                        });
    }

    private ConversationEntity resolveConversation(CorpusScope corpusScope, UUID conversationId) {
        if (corpusScope == CorpusScope.PROJECT_SHARED) {
            return null;
        }
        if (conversationId == null) {
            throw new IllegalArgumentException("conversationId required for CHAT_LOCAL");
        }
        return conversationRepository.getReferenceById(conversationId);
    }
}
