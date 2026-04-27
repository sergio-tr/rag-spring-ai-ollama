package com.uniovi.rag.application.service.knowledge;

import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.indexing.ReindexImpactLevel;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.KnowledgeBuildProjection;
import com.uniovi.rag.domain.knowledge.KnowledgeReindexDecision;
import com.uniovi.rag.domain.knowledge.KnowledgeReindexKind;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.infrastructure.persistence.ConversationRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.ReindexEventRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ReindexEventEntity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReindexServiceTest {

    @Mock
    private ReindexEventRepository reindexEventRepository;

    @Mock
    private ProjectRepository projectRepository;

    @Mock
    private ConversationRepository conversationRepository;

    @Mock
    private KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;

    @Mock
    private ReindexEventStatusUpdater reindexEventStatusUpdater;

    @InjectMocks
    private ReindexService reindexService;

    private final UUID projectId = UUID.randomUUID();
    private final UUID configSnapId = UUID.randomUUID();

    @Test
    void execute_noOp_doesNotPersistEvent() {
        KnowledgeBuildProjection projection = projectionHard();
        reindexService.executeKnowledgeReindexDecision(
                new KnowledgeReindexDecision(KnowledgeReindexKind.NO_OP),
                projection,
                projectId,
                CorpusScope.PROJECT_SHARED,
                null,
                configSnapId);
        verify(reindexEventRepository, never()).save(any());
        verify(knowledgePipelineOrchestrator, never()).rebuildScope(any(), any(), any(), any(), any());
    }

    @Test
    void execute_hard_withReadyDocuments_runsRebuild() {
        KnowledgeBuildProjection projection = projectionHard();
        when(knowledgePipelineOrchestrator.hasReadyDocumentsInScope(projectId, CorpusScope.PROJECT_SHARED, null))
                .thenReturn(true);
        when(projectRepository.getReferenceById(projectId)).thenReturn(org.mockito.Mockito.mock(ProjectEntity.class));
        when(knowledgePipelineOrchestrator.previewSnapshotSignatureHex(
                        eq(projectId), eq(CorpusScope.PROJECT_SHARED), eq(null), eq(projection)))
                .thenReturn("ab".repeat(32));
        when(knowledgePipelineOrchestrator.rebuildScope(
                        eq(projectId),
                        eq(CorpusScope.PROJECT_SHARED),
                        eq(null),
                        eq(projection),
                        eq(configSnapId)))
                .thenReturn(UUID.randomUUID());
        when(reindexEventRepository.save(any()))
                .thenAnswer(
                        inv -> {
                            ReindexEventEntity e = inv.getArgument(0);
                            ReflectionTestUtils.setField(e, "id", UUID.randomUUID());
                            return e;
                        });

        KnowledgeReindexExecutionResult out =
                reindexService.executeKnowledgeReindexDecision(
                        new KnowledgeReindexDecision(KnowledgeReindexKind.HARD_REBUILD),
                        projection,
                        projectId,
                        CorpusScope.PROJECT_SHARED,
                        null,
                        configSnapId);

        assertThat(out.reindexEventId()).isNotNull();
        verify(knowledgePipelineOrchestrator)
                .rebuildScope(projectId, CorpusScope.PROJECT_SHARED, null, projection, configSnapId);
        verify(reindexEventRepository, org.mockito.Mockito.atLeastOnce()).save(any());
    }

    private static KnowledgeBuildProjection projectionHard() {
        return new KnowledgeBuildProjection(
                1,
                MaterializationStrategy.CHUNK_LEVEL,
                400,
                0,
                "m",
                false,
                new ReindexImpact(ReindexImpactLevel.HARD_REINDEX, java.util.List.of("t")),
                null,
                "deadbeef");
    }
}
