package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.indexing.ReindexImpactLevel;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.ReindexEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReindexServiceTest {

    @Mock private ReindexEventRepository reindexEventRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ConversationRepository conversationRepository;
    @Mock private KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    @Mock private ReindexAsyncRunner reindexAsyncRunner;

    @InjectMocks private ReindexService reindexService;

    private final UUID projectId = UUID.randomUUID();

    @Test
    void handleConfigReindexImpact_noReindex_doesNotPersistEvent() {
        reindexService.handleConfigReindexImpact(ReindexImpact.none(), projectId, CorpusScope.PROJECT_SHARED, null);
        verify(reindexEventRepository, never()).save(any());
        verify(knowledgePipelineOrchestrator, never()).rebuildScope(any(), any(), any());
    }

    @Test
    void handleConfigReindexImpact_hard_withReadyDocuments_runsRebuild() {
        when(knowledgePipelineOrchestrator.hasReadyDocumentsInScope(projectId, CorpusScope.PROJECT_SHARED, null))
                .thenReturn(true);
        when(projectRepository.getReferenceById(projectId)).thenReturn(org.mockito.Mockito.mock(
                com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity.class));
        when(knowledgePipelineOrchestrator.previewSnapshotSignatureHex(projectId, CorpusScope.PROJECT_SHARED, null))
                .thenReturn("ab".repeat(32));
        when(reindexEventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        reindexService.handleConfigReindexImpact(
                new ReindexImpact(ReindexImpactLevel.HARD_REINDEX, java.util.List.of("HARD:TEST")),
                projectId,
                CorpusScope.PROJECT_SHARED,
                null);

        verify(knowledgePipelineOrchestrator).rebuildScope(projectId, CorpusScope.PROJECT_SHARED, null);
        verify(reindexEventRepository, org.mockito.Mockito.atLeastOnce()).save(any());
    }
}
