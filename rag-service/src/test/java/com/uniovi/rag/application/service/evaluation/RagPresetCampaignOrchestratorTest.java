package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.application.port.EvaluationDatasetStorePort;
import com.uniovi.rag.configuration.RagRuntimeProperties;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.EvaluationRunKind;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationCampaignRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationCorpusRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationDatasetRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.RagPresetRepository;
import com.uniovi.rag.infrastructure.persistence.ResolvedConfigSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCorpusEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationDatasetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.vector.EmbeddingSpaceGuard;
import com.uniovi.rag.application.service.async.AsyncTaskService;
import com.uniovi.rag.application.service.evaluation.config.LabBenchmarkConfigPreflightResult;
import com.uniovi.rag.application.service.evaluation.config.LabBenchmarkConfigPreflightService;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusApplicationService;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusReadinessService;
import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.service.evaluation.preset.LabPresetAxisSupport;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusReadinessDto;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import com.uniovi.rag.infrastructure.observability.RuntimeObservability;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagPresetCampaignOrchestratorTest {

    @Mock private UserRepository userRepository;
    @Mock private EvaluationDatasetRepository evaluationDatasetRepository;
    @Mock private EvaluationCampaignRepository evaluationCampaignRepository;
    @Mock private EvaluationRunRepository evaluationRunRepository;
    @Mock private ResolvedConfigSnapshotRepository resolvedConfigSnapshotRepository;
    @Mock private KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;
    @Mock private RagPresetRepository ragPresetRepository;
    @Mock private AsyncTaskRepository asyncTaskRepository;
    @Mock private AsyncTaskService asyncTaskService;
    @Mock private LabJobLifecycleService labJobLifecycleService;
    @Mock private ProjectAccessService projectAccessService;
    @Mock private RagRuntimeProperties ragRuntimeProperties;
    @Mock private EvaluationDatasetStorePort evaluationDatasetStorePort;
    @Mock private EmbeddingSpaceGuard embeddingSpaceGuard;
    @Mock private EvaluationCorpusApplicationService evaluationCorpusApplicationService;
    @Mock private EvaluationCorpusReadinessService evaluationCorpusReadinessService;
    @Mock private EvaluationCorpusRepository evaluationCorpusRepository;
    @Mock private LabBenchmarkConfigPreflightService labBenchmarkConfigPreflightService;
    @Mock private ObjectProvider<RuntimeObservability> runtimeObservability;
    private final LabPresetAxisSupport labPresetAxisSupport =
            new LabPresetAxisSupport(new EvaluationReferenceBundleLoader(new EvaluationWorkbookParser()));

    @BeforeEach
    void stubCorpus() {
        KnowledgeDocumentEntity doc = mock(KnowledgeDocumentEntity.class);
        UUID corpusId = UUID.randomUUID();
        lenient()
                .when(evaluationCorpusApplicationService.requireContext(any(), any()))
                .thenReturn(
                        new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                                corpusId, UUID.randomUUID(), List.of(UUID.randomUUID()), List.of(doc)));
        EvaluationCorpusEntity corpus = mock(EvaluationCorpusEntity.class);
        ProjectEntity indexProject = mock(ProjectEntity.class);
        lenient().when(corpus.getIndexProject()).thenReturn(indexProject);
        lenient().when(indexProject.getId()).thenReturn(UUID.randomUUID());
        lenient()
                .when(evaluationCorpusRepository.findByIdAndOwner_Id(any(), any()))
                .thenReturn(Optional.of(corpus));
        lenient().when(asyncTaskRepository.findById(any())).thenReturn(Optional.of(mock(AsyncTaskEntity.class)));
        lenient()
                .when(evaluationCorpusReadinessService.getReadiness(any(), any()))
                .thenReturn(
                        new EvaluationCorpusReadinessDto(
                                corpusId,
                                UUID.randomUUID(),
                                1,
                                1,
                                0,
                                0,
                                null,
                                null,
                                UUID.randomUUID(),
                                false,
                                null,
                                null,
                                List.of(UUID.randomUUID()),
                                true));
        lenient()
                .when(labBenchmarkConfigPreflightService.validateOrThrow(any(), any(), any()))
                .thenReturn(
                        new LabBenchmarkConfigPreflightResult(
                                true, "OK", List.of("P0"), null, true, false, Map.of()));
    }

    @Test
    void startJsonBenchmark_ragPresetCampaign_createsOneRunPerPreset() {
        BenchmarkRunOrchestrator orch = orchestrator();

        UUID userId = UUID.randomUUID();
        UserEntity user = mock(UserEntity.class);
        when(user.getId()).thenReturn(userId);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        UUID datasetId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        EvaluationDatasetEntity ds = mock(EvaluationDatasetEntity.class);
        when(ds.getId()).thenReturn(datasetId);
        when(ds.getOwner()).thenReturn(user);
        when(ds.getDatasetScope()).thenReturn("USER_DATASET");
        when(ds.getExperimentalKind()).thenReturn("RAG_PRESET_BENCHMARK");
        when(evaluationDatasetRepository.findById(datasetId)).thenReturn(Optional.of(ds));

        when(evaluationCampaignRepository.save(any())).thenAnswer(inv -> {
            EvaluationCampaignEntity c = inv.getArgument(0);
            c.setId(UUID.randomUUID());
            c.setCreatedAt(Instant.now());
            return c;
        });

        when(evaluationRunRepository.save(any())).thenAnswer(inv -> {
            EvaluationRunEntity r = inv.getArgument(0);
            if (r.getId() == null) {
                r.setId(UUID.randomUUID());
            }
            return r;
        });
        when(evaluationRunRepository.findById(any())).thenAnswer(inv -> {
            UUID id = inv.getArgument(0);
            if (id == null) {
                return Optional.empty();
            }
            EvaluationRunEntity r = mock(EvaluationRunEntity.class);
            when(r.getId()).thenReturn(id);
            when(r.getProject()).thenReturn(null);
            return Optional.of(r);
        });

        UUID taskId = UUID.randomUUID();
        when(asyncTaskService.submitEvalRagCampaign(any(), any(), any(), any())).thenReturn(taskId);

        List<String> presets = List.of("P0", "P1", "P2");
        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        datasetId,
                        corpusId,
                        null,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "RAG sweep",
                        null,
                        null,
                        null,
                        null,
                        presets,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        "Preset comparison",
                        true,
                        true,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of());

        BenchmarkJobAccepted accepted =
                orch.startJsonBenchmark(userId, "USER", BenchmarkKind.RAG_PRESET_END_TO_END, req);
        assertThat(accepted.campaignId()).isPresent();
        verify(asyncTaskService, times(1)).submitEvalRagCampaign(any(), any(), any(), any());
        verify(asyncTaskService, times(0)).submitEvalRag(any(), any(), any());

        ArgumentCaptor<EvaluationRunEntity> runCaptor = ArgumentCaptor.forClass(EvaluationRunEntity.class);
        verify(evaluationRunRepository, atLeast(3)).save(runCaptor.capture());
        List<EvaluationRunEntity> childRuns =
                runCaptor.getAllValues().stream()
                        .filter(r -> BenchmarkKind.RAG_PRESET_END_TO_END.name().equals(r.getBenchmarkKind()))
                        .toList();
        assertThat(childRuns).hasSizeGreaterThanOrEqualTo(3);
        assertThat(childRuns.stream().allMatch(r -> r.getCampaign() != null)).isTrue();
        assertThat(childRuns)
                .allMatch(
                        r ->
                                r.getAggregatesJson() != null
                                        && r.getAggregatesJson()
                                                .containsKey(BenchmarkRunOrchestrator.AGG_KEY_CONFIG_PREFLIGHT));
        assertThat(childRuns)
                .allMatch(
                        r ->
                                r.getAggregatesJson() != null
                                        && r.getAggregatesJson()
                                                .containsKey(LabPresetAxisSupport.AGG_KEY_PRESET_KEY));
        assertThat(childRuns)
                .allMatch(
                        r ->
                                r.getAggregatesJson() != null
                                        && LabPresetAxisSupport.COMPARISON_AXIS_PRESET.equals(
                                                r.getAggregatesJson().get(LabPresetAxisSupport.AGG_KEY_COMPARISON_AXIS)));
    }

    private BenchmarkRunOrchestrator orchestrator() {
        return new BenchmarkRunOrchestrator(
                userRepository,
                evaluationDatasetRepository,
                evaluationCampaignRepository,
                evaluationRunRepository,
                resolvedConfigSnapshotRepository,
                knowledgeIndexSnapshotRepository,
                ragPresetRepository,
                asyncTaskRepository,
                asyncTaskService,
                labJobLifecycleService,
                projectAccessService,
                ragRuntimeProperties,
                evaluationDatasetStorePort,
                new EvaluationWorkbookParser(),
                embeddingSpaceGuard,
                evaluationCorpusApplicationService,
                evaluationCorpusReadinessService,
                evaluationCorpusRepository,
                labBenchmarkConfigPreflightService,
                labPresetAxisSupport,
                runtimeObservability);
    }
}
