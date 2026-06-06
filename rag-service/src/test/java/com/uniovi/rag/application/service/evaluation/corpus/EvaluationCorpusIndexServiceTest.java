package com.uniovi.rag.application.service.evaluation.corpus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.ResolvedConfigSnapshotApplicationService;
import com.uniovi.rag.application.service.ResolvedConfigSnapshotLinkage;
import com.uniovi.rag.application.service.knowledge.KnowledgePipelineOrchestrator;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileService;
import com.uniovi.rag.domain.knowledge.CorpusScope;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotOwnerType;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EvaluationCorpusIndexServiceTest {

    @Mock private EvaluationCorpusApplicationService evaluationCorpusApplicationService;
    @Mock private KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    @Mock private ProjectIndexProfileService projectIndexProfileService;
    @Mock private ResolvedConfigSnapshotApplicationService resolvedConfigSnapshotApplicationService;

    @InjectMocks private EvaluationCorpusIndexService service;

    @Test
    void prepareIndex_passesResolvedConfigSnapshotLinkageToRebuild() {
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID indexProjectId = UUID.randomUUID();
        UUID configSnapshotId = UUID.randomUUID();
        UUID builtSnapshotId = UUID.randomUUID();
        String configHash = "a".repeat(64);

        KnowledgeDocumentEntity readyDoc = mock(KnowledgeDocumentEntity.class);
        when(evaluationCorpusApplicationService.requireReadyContext(userId, corpusId))
                .thenReturn(
                        new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                                corpusId, indexProjectId, List.of(UUID.randomUUID()), List.of(readyDoc)));
        when(evaluationCorpusApplicationService.syncIndexProjectDocuments(userId, corpusId)).thenReturn(1);
        ProjectIndexProfile profile = defaultProfile(indexProjectId);
        when(projectIndexProfileService.ensureDefault(indexProjectId)).thenReturn(profile);
        when(resolvedConfigSnapshotApplicationService.persistIngestionDefaultSnapshotLinkage(
                        eq(userId), eq(indexProjectId), eq(Optional.empty())))
                .thenReturn(new ResolvedConfigSnapshotLinkage(configSnapshotId, configHash));
        when(knowledgePipelineOrchestrator.rebuildScopeWithProfileOverride(
                        eq(indexProjectId),
                        eq(CorpusScope.PROJECT_SHARED),
                        isNull(),
                        eq(KnowledgeSnapshotOwnerType.EVALUATION_CORPUS),
                        eq(corpusId),
                        eq(configSnapshotId),
                        eq(configHash),
                        eq(profile)))
                .thenReturn(builtSnapshotId);

        service.prepareIndex(userId, corpusId);

        ArgumentCaptor<UUID> configIdCaptor = ArgumentCaptor.forClass(UUID.class);
        ArgumentCaptor<String> configHashCaptor = ArgumentCaptor.forClass(String.class);
        verify(knowledgePipelineOrchestrator)
                .rebuildScopeWithProfileOverride(
                        eq(indexProjectId),
                        eq(CorpusScope.PROJECT_SHARED),
                        isNull(),
                        eq(KnowledgeSnapshotOwnerType.EVALUATION_CORPUS),
                        eq(corpusId),
                        configIdCaptor.capture(),
                        configHashCaptor.capture(),
                        eq(profile));
        assertThat(configIdCaptor.getValue()).isEqualTo(configSnapshotId);
        assertThat(configHashCaptor.getValue()).isEqualTo(configHash);
    }

    @Test
    void prepareIndex_doesNotThrowWhenResolvedConfigLinkageProvided() {
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID indexProjectId = UUID.randomUUID();
        KnowledgeDocumentEntity readyDoc = mock(KnowledgeDocumentEntity.class);
        when(evaluationCorpusApplicationService.requireReadyContext(userId, corpusId))
                .thenReturn(
                        new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                                corpusId, indexProjectId, List.of(UUID.randomUUID()), List.of(readyDoc)));
        when(evaluationCorpusApplicationService.syncIndexProjectDocuments(userId, corpusId)).thenReturn(1);
        when(projectIndexProfileService.ensureDefault(indexProjectId)).thenReturn(defaultProfile(indexProjectId));
        when(resolvedConfigSnapshotApplicationService.persistIngestionDefaultSnapshotLinkage(
                        eq(userId), eq(indexProjectId), eq(Optional.empty())))
                .thenReturn(new ResolvedConfigSnapshotLinkage(UUID.randomUUID(), "b".repeat(64)));
        when(knowledgePipelineOrchestrator.rebuildScopeWithProfileOverride(
                        eq(indexProjectId),
                        eq(CorpusScope.PROJECT_SHARED),
                        isNull(),
                        eq(KnowledgeSnapshotOwnerType.EVALUATION_CORPUS),
                        eq(corpusId),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenReturn(UUID.randomUUID());

        service.prepareIndex(userId, corpusId);
    }

    @Test
    void prepareIndex_mapsResolvedConfigLinkageFailureToControlledError() {
        UUID userId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID indexProjectId = UUID.randomUUID();
        KnowledgeDocumentEntity readyDoc = mock(KnowledgeDocumentEntity.class);
        when(evaluationCorpusApplicationService.requireReadyContext(userId, corpusId))
                .thenReturn(
                        new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                                corpusId, indexProjectId, List.of(UUID.randomUUID()), List.of(readyDoc)));
        when(evaluationCorpusApplicationService.syncIndexProjectDocuments(userId, corpusId)).thenReturn(1);
        when(projectIndexProfileService.ensureDefault(indexProjectId)).thenReturn(defaultProfile(indexProjectId));
        when(resolvedConfigSnapshotApplicationService.persistIngestionDefaultSnapshotLinkage(
                        eq(userId), eq(indexProjectId), eq(Optional.empty())))
                .thenReturn(new ResolvedConfigSnapshotLinkage(UUID.randomUUID(), "c".repeat(64)));
        when(knowledgePipelineOrchestrator.rebuildScopeWithProfileOverride(
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.any()))
                .thenThrow(
                        new IllegalArgumentException(
                                "resolved_config_snapshot linkage required for knowledge_index_snapshot"));

        assertThatThrownBy(() -> service.prepareIndex(userId, corpusId))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getReason())
                                        .isEqualTo(LabCorpusReasonCodes.RUNTIME_CONFIG_SNAPSHOT_UNAVAILABLE));
    }

    private static ProjectIndexProfile defaultProfile(UUID projectId) {
        return new ProjectIndexProfile(
                projectId,
                MaterializationStrategy.HYBRID,
                true,
                "meta-v1",
                "mxbai-embed-large",
                400,
                10,
                ProjectIndexProfile.computeProfileHash(
                        MaterializationStrategy.HYBRID, true, "meta-v1", "mxbai-embed-large", 400, 10),
                Instant.now(),
                Instant.now());
    }
}
