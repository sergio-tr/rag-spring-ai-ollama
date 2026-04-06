package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.ReindexEventStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Async execution for operator-triggered scope rebuilds (separate bean so {@code @Async} proxies apply).
 */
@Service
public class ReindexAsyncRunner {

    private static final Logger log = LoggerFactory.getLogger(ReindexAsyncRunner.class);

    private final KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    private final ReindexService reindexService;

    public ReindexAsyncRunner(
            KnowledgePipelineOrchestrator knowledgePipelineOrchestrator, @Lazy ReindexService reindexService) {
        this.knowledgePipelineOrchestrator = knowledgePipelineOrchestrator;
        this.reindexService = reindexService;
    }

    @Async
    public void runOperatorReindex(UUID eventId, UUID projectId, CorpusScope corpusScope, UUID conversationId) {
        try {
            reindexService.updateReindexEventStatus(eventId, ReindexEventStatus.RUNNING);
            knowledgePipelineOrchestrator.rebuildScope(projectId, corpusScope, conversationId);
            reindexService.updateReindexEventStatus(eventId, ReindexEventStatus.COMPLETED);
        } catch (RuntimeException e) {
            log.error("Operator reindex failed for event {}: {}", eventId, e.getMessage(), e);
            reindexService.updateReindexEventStatus(eventId, ReindexEventStatus.FAILED);
        }
    }
}
