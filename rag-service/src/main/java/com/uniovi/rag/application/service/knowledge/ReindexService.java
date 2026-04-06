package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.indexing.ReindexImpactLevel;
import com.uniovi.rag.domain.knowledge.CorpusScope;
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
 * Sole application entry for interpreting {@link ReindexImpact} and operator reindex (Microphase 3.1).
 */
@Service
public class ReindexService {

    private final ReindexEventRepository reindexEventRepository;
    private final ProjectRepository projectRepository;
    private final ConversationRepository conversationRepository;
    private final KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    private final ReindexAsyncRunner reindexAsyncRunner;

    public ReindexService(
            ReindexEventRepository reindexEventRepository,
            ProjectRepository projectRepository,
            ConversationRepository conversationRepository,
            KnowledgePipelineOrchestrator knowledgePipelineOrchestrator,
            ReindexAsyncRunner reindexAsyncRunner) {
        this.reindexEventRepository = reindexEventRepository;
        this.projectRepository = projectRepository;
        this.conversationRepository = conversationRepository;
        this.knowledgePipelineOrchestrator = knowledgePipelineOrchestrator;
        this.reindexAsyncRunner = reindexAsyncRunner;
    }

    /**
     * NO_REINDEX: no {@code reindex_event} row. SOFT with no READY docs or no {@code requires_reindex}: no row.
     * HARD/SOFT with work: one row and synchronous {@link KnowledgePipelineOrchestrator#rebuildScope}.
     */
    @Transactional
    public void handleConfigReindexImpact(
            ReindexImpact impact, UUID projectId, CorpusScope corpusScope, UUID conversationId) {
        if (impact == null || impact.level() == ReindexImpactLevel.NO_REINDEX) {
            return;
        }
        if (!knowledgePipelineOrchestrator.hasReadyDocumentsInScope(projectId, corpusScope, conversationId)) {
            return;
        }
        if (impact.level() == ReindexImpactLevel.SOFT_REINDEX
                && !knowledgePipelineOrchestrator.scopeHasRequiresReindex(projectId, corpusScope, conversationId)) {
            return;
        }
        ProjectEntity project = projectRepository.getReferenceById(projectId);
        ConversationEntity conversation = resolveConversation(corpusScope, conversationId);
        String targetSig =
                knowledgePipelineOrchestrator.previewSnapshotSignatureHex(projectId, corpusScope, conversationId);
        String reason =
                impact.level() == ReindexImpactLevel.HARD_REINDEX
                        ? ReindexEventReason.CONFIG_HARD
                        : ReindexEventReason.CONFIG_SOFT;
        ReindexEventEntity ev =
                ReindexEventEntity.newPending(project, conversation, null, reason, targetSig, ReindexEventStatus.PENDING);
        ev = reindexEventRepository.save(ev);
        try {
            updateReindexEventStatus(ev.getId(), ReindexEventStatus.RUNNING);
            knowledgePipelineOrchestrator.rebuildScope(projectId, corpusScope, conversationId);
            updateReindexEventStatus(ev.getId(), ReindexEventStatus.COMPLETED);
        } catch (RuntimeException e) {
            updateReindexEventStatus(ev.getId(), ReindexEventStatus.FAILED);
            throw e;
        }
    }

    /**
     * Operator API: persists {@code reindex_event} and runs rebuild asynchronously when scope has READY documents.
     */
    public void enqueueOperatorReindex(UUID projectId, CorpusScope corpusScope, UUID conversationId) {
        if (!knowledgePipelineOrchestrator.hasReadyDocumentsInScope(projectId, corpusScope, conversationId)) {
            return;
        }
        ProjectEntity project = projectRepository.getReferenceById(projectId);
        ConversationEntity conversation = resolveConversation(corpusScope, conversationId);
        String targetSig =
                knowledgePipelineOrchestrator.previewSnapshotSignatureHex(projectId, corpusScope, conversationId);
        ReindexEventEntity ev =
                ReindexEventEntity.newPending(
                        project,
                        conversation,
                        null,
                        ReindexEventReason.OPERATOR_REQUEST,
                        targetSig,
                        ReindexEventStatus.PENDING);
        ev = reindexEventRepository.save(ev);
        reindexAsyncRunner.runOperatorReindex(ev.getId(), projectId, corpusScope, conversationId);
    }

    /**
     * Sole writer for {@code reindex_event} status transitions (including async operator runs).
     */
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
