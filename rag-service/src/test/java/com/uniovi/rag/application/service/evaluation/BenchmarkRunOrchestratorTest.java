package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.configuration.RagRuntimeProperties;
import com.uniovi.rag.domain.knowledge.KnowledgeSnapshotScopeType;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.EvaluationDatasetScope;
import com.uniovi.rag.domain.evaluation.EvaluationRunKind;
import com.uniovi.rag.domain.evaluation.workbook.ExperimentalDatasetType;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationDatasetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.UserEntity;
import com.uniovi.rag.infrastructure.persistence.AsyncTaskRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationCampaignRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationDatasetRepository;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.RagPresetRepository;
import com.uniovi.rag.infrastructure.persistence.ResolvedConfigSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.UserRepository;
import com.uniovi.rag.infrastructure.vector.EmbeddingSpaceGuard;
import com.uniovi.rag.application.evaluation.workbook.EvaluationReferenceBundleLoader;
import com.uniovi.rag.application.evaluation.workbook.EvaluationWorkbookParser;
import com.uniovi.rag.application.service.evaluation.config.LabBenchmarkConfigPreflightResult;
import com.uniovi.rag.application.service.evaluation.config.LabBenchmarkConfigPreflightService;
import com.uniovi.rag.application.service.evaluation.config.LabRuntimeConfigReasonCodes;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusApplicationService;
import com.uniovi.rag.application.service.evaluation.corpus.EvaluationCorpusReadinessService;
import com.uniovi.rag.application.service.evaluation.corpus.LabCorpusReasonCodes;
import com.uniovi.rag.application.service.evaluation.preset.CorpusAvailabilityGate;
import com.uniovi.rag.application.service.evaluation.preset.LabIndexSnapshotCompatibilityService;
import com.uniovi.rag.application.service.evaluation.preset.LabPresetAxisSupport;
import com.uniovi.rag.application.service.knowledge.KnowledgePipelineOrchestrator;
import com.uniovi.rag.application.service.knowledge.LabIndexProfileOverrideFactory;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileService;
import com.uniovi.rag.interfaces.rest.dto.evaluation.EvaluationCorpusReadinessDto;
import com.uniovi.rag.application.service.evaluation.lab.LabCorpusBootstrapErrors;
import com.uniovi.rag.infrastructure.persistence.EvaluationCorpusRepository;
import com.uniovi.rag.application.port.EvaluationDatasetStorePort;
import com.uniovi.rag.infrastructure.persistence.jpa.AsyncTaskEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCampaignEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCorpusEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeDocumentEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.interfaces.rest.dto.ActiveLabJobDto;
import com.uniovi.rag.application.service.async.AsyncTaskService;
import com.uniovi.rag.application.service.project.ProjectAccessService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import com.uniovi.rag.infrastructure.observability.RuntimeObservability;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BenchmarkRunOrchestratorTest {

    private static final UUID TEST_CORPUS_ID = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

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
    private final EvaluationWorkbookParser evaluationWorkbookParser = new EvaluationWorkbookParser();
    @Mock private EmbeddingSpaceGuard embeddingSpaceGuard;
    @Mock private EvaluationCorpusApplicationService evaluationCorpusApplicationService;
    @Mock private EvaluationCorpusReadinessService evaluationCorpusReadinessService;
    @Mock private EvaluationCorpusRepository evaluationCorpusRepository;
    @Mock private LabBenchmarkConfigPreflightService labBenchmarkConfigPreflightService;
    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;
    @Mock private CorpusAvailabilityGate corpusAvailabilityGate;
    @Mock private KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    @Mock private ProjectIndexProfileService projectIndexProfileService;
    @Mock private LabIndexProfileOverrideFactory labIndexProfileOverrideFactory;
    @Mock private ObjectProvider<RuntimeObservability> runtimeObservability;
    private final LabPresetAxisSupport labPresetAxisSupport =
            new LabPresetAxisSupport(new EvaluationReferenceBundleLoader(evaluationWorkbookParser));
    private final LabBenchmarkDefaultModelResolver labBenchmarkDefaultModelResolver =
            new LabBenchmarkDefaultModelResolver("gemma3:4b", "mxbai-embed-large:latest");

    @BeforeEach
    void stubRuntimeObservability() {
        lenient().when(runtimeObservability.getIfAvailable()).thenReturn(null);
    }

    @BeforeEach
    void stubConfigPreflight() {
        lenient()
                .when(labBenchmarkConfigPreflightService.validateOrThrow(any(), any(), any()))
                .thenReturn(
                        new LabBenchmarkConfigPreflightResult(
                                true, "OK", List.of("P0"), null, true, false, Map.of()));
    }

    @BeforeEach
    void lenientEmbeddingGuard() {
        lenient().when(embeddingSpaceGuard.assertFitsPhysicalVectorColumnReturning(anyString())).thenReturn(1024);
    }

    @BeforeEach
    void stubEvaluationCorpus() {
        KnowledgeDocumentEntity doc = Mockito.mock(KnowledgeDocumentEntity.class);
        lenient()
                .when(evaluationCorpusApplicationService.requireContext(any(), any()))
                .thenReturn(
                        new EvaluationCorpusApplicationService.EvaluationCorpusContext(
                                TEST_CORPUS_ID, UUID.randomUUID(), List.of(UUID.randomUUID()), List.of(doc)));
        EvaluationCorpusEntity corpus = Mockito.mock(EvaluationCorpusEntity.class);
        ProjectEntity indexProject = Mockito.mock(ProjectEntity.class);
        lenient().when(corpus.getIndexProject()).thenReturn(indexProject);
        lenient().when(indexProject.getId()).thenReturn(UUID.randomUUID());
        lenient()
                .when(evaluationCorpusRepository.findByIdAndOwner_Id(any(), any()))
                .thenReturn(Optional.of(corpus));
        lenient().when(asyncTaskRepository.findById(any())).thenReturn(Optional.of(Mockito.mock(AsyncTaskEntity.class)));
        lenient()
                .when(evaluationCorpusReadinessService.getReadiness(any(), any()))
                .thenReturn(
                        new EvaluationCorpusReadinessDto(
                                TEST_CORPUS_ID,
                                UUID.randomUUID(),
                                1,
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
    }

    @Test
    void startJsonBenchmark_ragRejectsEmptyCorpusWithKbEmptyCode() {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
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
                        evaluationWorkbookParser,
                        embeddingSpaceGuard,
                        evaluationCorpusApplicationService,
                        evaluationCorpusReadinessService,
                        evaluationCorpusRepository,
                        labBenchmarkConfigPreflightService,
                        labPresetAxisSupport,
                        labBenchmarkDefaultModelResolver,
                        runtimeObservability);
        when(evaluationCorpusReadinessService.getReadiness(any(), eq(TEST_CORPUS_ID)))
                .thenReturn(
                        new EvaluationCorpusReadinessDto(
                                TEST_CORPUS_ID,
                                UUID.randomUUID(),
                                0,
                                0,
                                0,
                                0,
                                0,
                                LabCorpusReasonCodes.NO_DOCUMENTS,
                                "empty",
                                null,
                                false,
                                null,
                                null,
                                List.of(),
                                false));

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        UUID.randomUUID(),
                        TEST_CORPUS_ID,
                        null,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null,
                        false,
                        false,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of());

        assertThatThrownBy(() -> orch.startJsonBenchmark(UUID.randomUUID(), "USER", BenchmarkKind.RAG_PRESET_END_TO_END, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex -> {
                            ResponseStatusException rse = (ResponseStatusException) ex;
                            assertThat(rse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                            assertThat(rse.getReason()).isEqualTo(LabCorpusReasonCodes.KB_EMPTY);
                        });
    }

    @Test
    void startJsonBenchmark_ragRejectsInvalidPresetConfigAfterCorpusReady() {
        LabIndexSnapshotCompatibilityService indexSnapshotCompatibilityService =
                new LabIndexSnapshotCompatibilityService(corpusAvailabilityGate, knowledgePipelineOrchestrator);
        LabBenchmarkConfigPreflightService realPreflight =
                new LabBenchmarkConfigPreflightService(
                        new RagFeatureConfiguration(),
                        knowledgeSnapshotService,
                        embeddingSpaceGuard,
                        indexSnapshotCompatibilityService,
                        evaluationCorpusApplicationService,
                        projectIndexProfileService,
                        labIndexProfileOverrideFactory);
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
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
                        evaluationWorkbookParser,
                        embeddingSpaceGuard,
                        evaluationCorpusApplicationService,
                        evaluationCorpusReadinessService,
                        evaluationCorpusRepository,
                        realPreflight,
                        labPresetAxisSupport,
                        labBenchmarkDefaultModelResolver,
                        runtimeObservability);

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        UUID.randomUUID(),
                        TEST_CORPUS_ID,
                        null,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of("P13"),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null,
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

        assertThatThrownBy(() -> orch.startJsonBenchmark(UUID.randomUUID(), "USER", BenchmarkKind.RAG_PRESET_END_TO_END, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getReason())
                                        .isEqualTo(
                                                LabRuntimeConfigReasonCodes.PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED));
    }

    @Test
    void startJsonBenchmark_forbidsAdminBaselineForNonAdmin() {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
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
                        evaluationWorkbookParser,
                        embeddingSpaceGuard,
                        evaluationCorpusApplicationService,
                        evaluationCorpusReadinessService,
                        evaluationCorpusRepository,
                        labBenchmarkConfigPreflightService,
                        labPresetAxisSupport,
                        labBenchmarkDefaultModelResolver,
                        runtimeObservability);

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        UUID.randomUUID(),
                        null,
                        UUID.randomUUID(),
                        EvaluationRunKind.ADMIN_BASELINE,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null,
                        false,
                        false,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of());

        assertThatThrownBy(() -> orch.startJsonBenchmark(UUID.randomUUID(), "USER", BenchmarkKind.LLM_JUDGE_QA, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode())
                                        .isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void startJsonBenchmark_allowsUserForPackagedReferenceBundleDataset() {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
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
                        evaluationWorkbookParser,
                        embeddingSpaceGuard,
                        evaluationCorpusApplicationService,
                        evaluationCorpusReadinessService,
                        evaluationCorpusRepository,
                        labBenchmarkConfigPreflightService,
                        labPresetAxisSupport,
                        labBenchmarkDefaultModelResolver,
                        runtimeObservability);

        UUID dsId = UUID.randomUUID();
        EvaluationDatasetEntity ds = Mockito.mock(EvaluationDatasetEntity.class);
        Mockito.when(ds.getDatasetScope()).thenReturn(EvaluationDatasetScope.SYSTEM_DATASET.name());
        Mockito.when(ds.getExperimentalKind()).thenReturn(ExperimentalDatasetType.REFERENCE_BUNDLE.name());
        Mockito.when(ds.getOwner()).thenReturn(null);
        when(evaluationDatasetRepository.findById(dsId)).thenReturn(Optional.of(ds));

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        dsId,
                        null,
                        UUID.randomUUID(),
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of("gemma3:4b"),
                        List.of(),
                        false,
                        null,
                        false,
                        false,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of());

        try {
            orch.startJsonBenchmark(UUID.randomUUID(), "USER", BenchmarkKind.LLM_JUDGE_QA, req);
        } catch (ResponseStatusException ex) {
            assertThat(ex.getStatusCode()).isNotEqualTo(HttpStatus.FORBIDDEN);
            assertThat(ex.getReason()).isNotEqualTo("SYSTEM_DATASET requires ADMIN");
        } catch (RuntimeException ex) {
            // Passed dataset authorization; downstream orchestration is not fully mocked here.
        }
    }

    @Test
    void startJsonBenchmark_rejectsWhenAnotherActiveJobExists() {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
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
                        evaluationWorkbookParser,
                        embeddingSpaceGuard,
                        evaluationCorpusApplicationService,
                        evaluationCorpusReadinessService,
                        evaluationCorpusRepository,
                        labBenchmarkConfigPreflightService,
                        labPresetAxisSupport,
                        labBenchmarkDefaultModelResolver,
                        runtimeObservability);

        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        when(labJobLifecycleService.findFirstActiveJobForScope(eq(userId), eq(projectId)))
                .thenReturn(new ActiveLabJobDto(
                        UUID.randomUUID(),
                        "LLM_JUDGE_QA",
                        UUID.randomUUID(),
                        projectId,
                        UUID.randomUUID(),
                        "RUNNING",
                        "x",
                        null,
                        null,
                        "/lab/jobs/x",
                        "/lab/jobs/x/events",
                        true
                ));

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        UUID.randomUUID(),
                        null,
                        projectId,
                        EvaluationRunKind.SCIENCE,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null,
                        false,
                        false,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of());

        assertThatThrownBy(() -> orch.startJsonBenchmark(userId, "USER", BenchmarkKind.LLM_JUDGE_QA, req))
                .isInstanceOf(LabJobConcurrencyException.class);
    }

    @Test
    void startJsonBenchmark_returnsNotFoundWhenDatasetMissing() {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
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
                        evaluationWorkbookParser,
                        embeddingSpaceGuard,
                        evaluationCorpusApplicationService,
                        evaluationCorpusReadinessService,
                        evaluationCorpusRepository,
                        labBenchmarkConfigPreflightService,
                        labPresetAxisSupport,
                        labBenchmarkDefaultModelResolver,
                        runtimeObservability);

        UUID dsId = UUID.randomUUID();
        when(evaluationDatasetRepository.findById(dsId)).thenReturn(Optional.empty());
        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        dsId,
                        null,
                        UUID.randomUUID(),
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null,
                        false,
                        false,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of());

        assertThatThrownBy(() -> orch.startJsonBenchmark(UUID.randomUUID(), "ADMIN", BenchmarkKind.LLM_JUDGE_QA, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode())
                                        .isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void startJsonBenchmark_rejectsIncompatibleExperimentalKind() {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
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
                        evaluationWorkbookParser,
                        embeddingSpaceGuard,
                        evaluationCorpusApplicationService,
                        evaluationCorpusReadinessService,
                        evaluationCorpusRepository,
                        labBenchmarkConfigPreflightService,
                        labPresetAxisSupport,
                        labBenchmarkDefaultModelResolver,
                        runtimeObservability);

        UUID dsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        EvaluationDatasetEntity ds = Mockito.mock(EvaluationDatasetEntity.class);
        UserEntity owner = Mockito.mock(UserEntity.class);
        Mockito.when(owner.getId()).thenReturn(userId);
        Mockito.when(ds.getExperimentalKind()).thenReturn("LLM_MODEL_BASELINE");
        Mockito.when(ds.getOwner()).thenReturn(owner);
        Mockito.when(ds.getDatasetScope()).thenReturn("USER_DATASET");
        when(evaluationDatasetRepository.findById(dsId)).thenReturn(Optional.of(ds));
        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        dsId,
                        null,
                        null,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null,
                        false,
                        false,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of());

        assertThatThrownBy(() -> orch.startJsonBenchmark(userId, "USER", BenchmarkKind.EMBEDDING_RETRIEVAL, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode())
                                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void startJsonBenchmark_embeddingCampaign_rejectsMisaligned_indexSnapshotIds() throws Exception {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
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
                        evaluationWorkbookParser,
                        embeddingSpaceGuard,
                        evaluationCorpusApplicationService,
                        evaluationCorpusReadinessService,
                        evaluationCorpusRepository,
                        labBenchmarkConfigPreflightService,
                        labPresetAxisSupport,
                        labBenchmarkDefaultModelResolver,
                        runtimeObservability);

        UUID dsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        byte[] bytes = canonicalReferenceBundleBytes();

        EvaluationDatasetEntity ds = Mockito.mock(EvaluationDatasetEntity.class);
        UserEntity owner = Mockito.mock(UserEntity.class);
        Mockito.when(owner.getId()).thenReturn(userId);
        Mockito.when(ds.getOwner()).thenReturn(owner);
        Mockito.when(ds.getDatasetScope()).thenReturn("USER_DATASET");
        Mockito.when(ds.getExperimentalKind()).thenReturn("REFERENCE_BUNDLE");
        Mockito.when(ds.getStorageUri()).thenReturn("datasets/u1/ref.xlsx");
        when(evaluationDatasetRepository.findById(dsId)).thenReturn(Optional.of(ds));
        when(evaluationDatasetStorePort.openStream(eq("datasets/u1/ref.xlsx"))).thenReturn(new ByteArrayInputStream(bytes));
        when(labJobLifecycleService.findFirstActiveJobForScope(eq(userId), eq(projectId))).thenReturn(null);
        when(userRepository.findById(userId)).thenReturn(Optional.of(Mockito.mock(UserEntity.class)));

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        dsId,
                        TEST_CORPUS_ID,
                        projectId,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "emb-campaign",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of("mxbai-embed-large", "nomic-embed-text"),
                        false,
                        null,
                        false,
                        false,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(UUID.randomUUID()));

        assertThatThrownBy(() -> orch.startJsonBenchmark(userId, "USER", BenchmarkKind.EMBEDDING_RETRIEVAL, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex -> {
                            ResponseStatusException r = (ResponseStatusException) ex;
                            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                            assertThat(r.getReason()).contains("EMBEDDING_CAMPAIGN_REQUIRES_ALIGNED_INDEX_SNAPSHOT_IDS");
                        });
        verify(asyncTaskService, never()).submitEvalEmbeddingCampaign(any(), any(), any(), any());
    }

    @Test
    void startJsonBenchmark_embeddingCampaign_rejectsWhenRunEmbeddingModelDoesNotMatchSnapshotProfile()
            throws Exception {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
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
                        evaluationWorkbookParser,
                        embeddingSpaceGuard,
                        evaluationCorpusApplicationService,
                        evaluationCorpusReadinessService,
                        evaluationCorpusRepository,
                        labBenchmarkConfigPreflightService,
                        labPresetAxisSupport,
                        labBenchmarkDefaultModelResolver,
                        runtimeObservability);

        UUID dsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID snapId = UUID.randomUUID();
        byte[] bytes = canonicalReferenceBundleBytes();

        EvaluationDatasetEntity ds = Mockito.mock(EvaluationDatasetEntity.class);
        UserEntity owner = Mockito.mock(UserEntity.class);
        Mockito.when(owner.getId()).thenReturn(userId);
        Mockito.when(ds.getOwner()).thenReturn(owner);
        Mockito.when(ds.getDatasetScope()).thenReturn("USER_DATASET");
        Mockito.when(ds.getExperimentalKind()).thenReturn("REFERENCE_BUNDLE");
        Mockito.when(ds.getStorageUri()).thenReturn("datasets/u1/ref.xlsx");
        Mockito.when(ds.getId()).thenReturn(dsId);
        when(evaluationDatasetRepository.findById(dsId)).thenReturn(Optional.of(ds));
        when(evaluationDatasetStorePort.openStream(eq("datasets/u1/ref.xlsx"))).thenReturn(new ByteArrayInputStream(bytes));
        when(labJobLifecycleService.findFirstActiveJobForScope(eq(userId), eq(projectId))).thenReturn(null);
        when(projectAccessService.requireOwnedProject(eq(userId), eq(projectId))).thenReturn(Mockito.mock(ProjectEntity.class));

        KnowledgeIndexSnapshotEntity idx = Mockito.mock(KnowledgeIndexSnapshotEntity.class);
        when(idx.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "nomic-embed-text"));
        when(idx.getSignatureHash()).thenReturn("sig");
        when(knowledgeIndexSnapshotRepository.findById(snapId)).thenReturn(Optional.of(idx));

        UserEntity user = Mockito.mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        dsId,
                        TEST_CORPUS_ID,
                        projectId,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "emb-single",
                        null,
                        snapId,
                        null,
                        null,
                        List.of(),
                        null,
                        "mxbai-embed-large",
                        List.of(),
                        List.of(),
                        false,
                        null,
                        false,
                        false,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(snapId));

        assertThatThrownBy(() -> orch.startJsonBenchmark(userId, "USER", BenchmarkKind.EMBEDDING_RETRIEVAL, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex -> {
                            ResponseStatusException r = (ResponseStatusException) ex;
                            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                            assertThat(r.getReason()).contains("EMBEDDING_MODEL_INDEX_MISMATCH");
                        });
        verify(asyncTaskService, never()).submitEvalEmbeddingRetrieval(any(), any(), any());
        verify(asyncTaskService, never()).submitEvalEmbeddingCampaign(any(), any(), any(), any());
    }

    @Test
    void startJsonBenchmark_embeddingCampaign_bindsEachRunToItsAlignedIndexSnapshot() throws Exception {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
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
                        evaluationWorkbookParser,
                        embeddingSpaceGuard,
                        evaluationCorpusApplicationService,
                        evaluationCorpusReadinessService,
                        evaluationCorpusRepository,
                        labBenchmarkConfigPreflightService,
                        labPresetAxisSupport,
                        labBenchmarkDefaultModelResolver,
                        runtimeObservability);

        UUID dsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID snapA = UUID.randomUUID();
        UUID snapB = UUID.randomUUID();
        byte[] bytes = canonicalReferenceBundleBytes();

        EvaluationDatasetEntity ds = Mockito.mock(EvaluationDatasetEntity.class);
        UserEntity owner = Mockito.mock(UserEntity.class);
        Mockito.when(owner.getId()).thenReturn(userId);
        Mockito.when(ds.getOwner()).thenReturn(owner);
        Mockito.when(ds.getDatasetScope()).thenReturn("USER_DATASET");
        Mockito.when(ds.getExperimentalKind()).thenReturn("REFERENCE_BUNDLE");
        Mockito.when(ds.getStorageUri()).thenReturn("datasets/u1/ref.xlsx");
        Mockito.when(ds.getId()).thenReturn(dsId);
        when(evaluationDatasetRepository.findById(dsId)).thenReturn(Optional.of(ds));
        when(evaluationDatasetStorePort.openStream(eq("datasets/u1/ref.xlsx"))).thenReturn(new ByteArrayInputStream(bytes));
        when(labJobLifecycleService.findFirstActiveJobForScope(eq(userId), eq(projectId))).thenReturn(null);
        when(projectAccessService.requireOwnedProject(eq(userId), eq(projectId))).thenReturn(Mockito.mock(ProjectEntity.class));

        KnowledgeIndexSnapshotEntity idxA = Mockito.mock(KnowledgeIndexSnapshotEntity.class);
        when(idxA.getId()).thenReturn(snapA);
        when(idxA.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "mxbai-embed-large"));
        when(idxA.getSignatureHash()).thenReturn("sig-a");
        when(knowledgeIndexSnapshotRepository.findById(snapA)).thenReturn(Optional.of(idxA));

        KnowledgeIndexSnapshotEntity idxB = Mockito.mock(KnowledgeIndexSnapshotEntity.class);
        when(idxB.getId()).thenReturn(snapB);
        when(idxB.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "nomic-embed-text"));
        when(idxB.getSignatureHash()).thenReturn("sig-b");
        when(knowledgeIndexSnapshotRepository.findById(snapB)).thenReturn(Optional.of(idxB));

        UserEntity user = Mockito.mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(evaluationCampaignRepository.save(any(EvaluationCampaignEntity.class)))
                .thenAnswer(
                        inv -> {
                            EvaluationCampaignEntity c = inv.getArgument(0);
                            if (c.getId() == null) {
                                c.setId(UUID.randomUUID());
                            }
                            return c;
                        });
        when(evaluationRunRepository.save(any(EvaluationRunEntity.class)))
                .thenAnswer(
                        inv -> {
                            EvaluationRunEntity r = inv.getArgument(0);
                            if (r.getId() == null) {
                                r.setId(UUID.randomUUID());
                            }
                            return r;
                        });

        UUID taskId = UUID.randomUUID();
        when(asyncTaskService.submitEvalEmbeddingCampaign(eq(userId), eq(projectId), any(UUID.class), any(UUID.class)))
                .thenReturn(taskId);
        when(evaluationRunRepository.findById(any(UUID.class)))
                .thenAnswer(
                        inv -> {
                            UUID id = inv.getArgument(0);
                            EvaluationRunEntity r = new EvaluationRunEntity();
                            r.setId(id);
                            return Optional.of(r);
                        });
        when(asyncTaskRepository.findById(taskId)).thenReturn(Optional.of(Mockito.mock(AsyncTaskEntity.class)));

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        dsId,
                        TEST_CORPUS_ID,
                        projectId,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "emb-campaign",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of("mxbai-embed-large", "nomic-embed-text"),
                        false,
                        "cmp",
                        false,
                        false,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(snapA, snapB));

        orch.startJsonBenchmark(userId, "USER", BenchmarkKind.EMBEDDING_RETRIEVAL, req);

        verify(knowledgeIndexSnapshotRepository).findById(snapA);
        verify(knowledgeIndexSnapshotRepository).findById(snapB);
        verify(asyncTaskService, times(1))
                .submitEvalEmbeddingCampaign(eq(userId), isNull(), any(UUID.class), any(UUID.class));
        verify(evaluationRunRepository, times(3)).save(any(EvaluationRunEntity.class));
    }

    @Test
    void startJsonBenchmark_embeddingCampaign_threeModels_oneJob_plannedTotalItemsNxM() throws Exception {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
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
                        evaluationWorkbookParser,
                        embeddingSpaceGuard,
                        evaluationCorpusApplicationService,
                        evaluationCorpusReadinessService,
                        evaluationCorpusRepository,
                        labBenchmarkConfigPreflightService,
                        labPresetAxisSupport,
                        labBenchmarkDefaultModelResolver,
                        runtimeObservability);

        UUID dsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        UUID snapA = UUID.randomUUID();
        UUID snapB = UUID.randomUUID();
        UUID snapC = UUID.randomUUID();
        byte[] bytes = canonicalReferenceBundleBytes();

        EvaluationDatasetEntity ds = Mockito.mock(EvaluationDatasetEntity.class);
        UserEntity owner = Mockito.mock(UserEntity.class);
        Mockito.when(owner.getId()).thenReturn(userId);
        Mockito.when(ds.getOwner()).thenReturn(owner);
        Mockito.when(ds.getDatasetScope()).thenReturn("USER_DATASET");
        Mockito.when(ds.getExperimentalKind()).thenReturn("REFERENCE_BUNDLE");
        Mockito.when(ds.getStorageUri()).thenReturn("datasets/u1/ref.xlsx");
        Mockito.when(ds.getId()).thenReturn(dsId);
        Mockito.when(ds.getQuestionCount()).thenReturn(12);
        when(evaluationDatasetRepository.findById(dsId)).thenReturn(Optional.of(ds));
        when(evaluationDatasetStorePort.openStream(eq("datasets/u1/ref.xlsx"))).thenReturn(new ByteArrayInputStream(bytes));
        when(labJobLifecycleService.findFirstActiveJobForScope(eq(userId), eq(projectId))).thenReturn(null);
        when(projectAccessService.requireOwnedProject(eq(userId), eq(projectId))).thenReturn(Mockito.mock(ProjectEntity.class));

        KnowledgeIndexSnapshotEntity idxA = Mockito.mock(KnowledgeIndexSnapshotEntity.class);
        when(idxA.getId()).thenReturn(snapA);
        when(idxA.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "mxbai-embed-large"));
        when(idxA.getSignatureHash()).thenReturn("sig-a");
        when(knowledgeIndexSnapshotRepository.findById(snapA)).thenReturn(Optional.of(idxA));

        KnowledgeIndexSnapshotEntity idxB = Mockito.mock(KnowledgeIndexSnapshotEntity.class);
        when(idxB.getId()).thenReturn(snapB);
        when(idxB.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "nomic-embed-text"));
        when(idxB.getSignatureHash()).thenReturn("sig-b");
        when(knowledgeIndexSnapshotRepository.findById(snapB)).thenReturn(Optional.of(idxB));

        KnowledgeIndexSnapshotEntity idxC = Mockito.mock(KnowledgeIndexSnapshotEntity.class);
        when(idxC.getId()).thenReturn(snapC);
        when(idxC.getIndexProfileJsonb()).thenReturn(Map.of("embeddingModelId", "bge-m3"));
        when(idxC.getSignatureHash()).thenReturn("sig-c");
        when(knowledgeIndexSnapshotRepository.findById(snapC)).thenReturn(Optional.of(idxC));

        UserEntity user = Mockito.mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ArgumentCaptor<EvaluationCampaignEntity> campCaptor = ArgumentCaptor.forClass(EvaluationCampaignEntity.class);
        when(evaluationCampaignRepository.save(campCaptor.capture()))
                .thenAnswer(
                        inv -> {
                            EvaluationCampaignEntity c = inv.getArgument(0);
                            if (c.getId() == null) {
                                c.setId(UUID.randomUUID());
                            }
                            return c;
                        });
        when(evaluationRunRepository.save(any(EvaluationRunEntity.class)))
                .thenAnswer(
                        inv -> {
                            EvaluationRunEntity r = inv.getArgument(0);
                            if (r.getId() == null) {
                                r.setId(UUID.randomUUID());
                            }
                            return r;
                        });

        UUID taskId = UUID.randomUUID();
        when(asyncTaskService.submitEvalEmbeddingCampaign(eq(userId), isNull(), any(UUID.class), any(UUID.class)))
                .thenReturn(taskId);
        when(evaluationRunRepository.findById(any(UUID.class)))
                .thenAnswer(
                        inv -> {
                            UUID id = inv.getArgument(0);
                            EvaluationRunEntity r = new EvaluationRunEntity();
                            r.setId(id);
                            return Optional.of(r);
                        });
        when(asyncTaskRepository.findById(taskId)).thenReturn(Optional.of(Mockito.mock(AsyncTaskEntity.class)));

        List<String> models = List.of("mxbai-embed-large", "nomic-embed-text", "bge-m3");
        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        dsId,
                        TEST_CORPUS_ID,
                        projectId,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "emb-campaign-3",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        models,
                        false,
                        "cmp",
                        false,
                        false,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(snapA, snapB, snapC));

        BenchmarkJobAccepted accepted =
                orch.startJsonBenchmark(userId, "USER", BenchmarkKind.EMBEDDING_RETRIEVAL, req);

        verify(asyncTaskService, times(1))
                .submitEvalEmbeddingCampaign(eq(userId), isNull(), any(UUID.class), any(UUID.class));
        verify(evaluationRunRepository, times(4)).save(any(EvaluationRunEntity.class));
        assertThat(accepted.totalItems()).isPresent();
        assertThat(accepted.totalItems().orElseThrow()).isGreaterThan(0);
        Map<String, Object> meta = campCaptor.getValue().getMetaJson();
        assertThat(meta).containsEntry("embeddingModelIds", models);
        int perAxis = ((Number) meta.get("perAxisItemCount")).intValue();
        assertThat(((Number) meta.get("plannedTotalItems")).intValue()).isEqualTo(perAxis * models.size());
    }

    @Test
    void startJsonBenchmark_embeddingCampaign_rejectsMissingSnapshotForModel() throws Exception {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
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
                        evaluationWorkbookParser,
                        embeddingSpaceGuard,
                        evaluationCorpusApplicationService,
                        evaluationCorpusReadinessService,
                        evaluationCorpusRepository,
                        labBenchmarkConfigPreflightService,
                        labPresetAxisSupport,
                        labBenchmarkDefaultModelResolver,
                        runtimeObservability);

        UUID dsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        byte[] bytes = canonicalReferenceBundleBytes();

        EvaluationDatasetEntity ds = Mockito.mock(EvaluationDatasetEntity.class);
        UserEntity owner = Mockito.mock(UserEntity.class);
        Mockito.when(owner.getId()).thenReturn(userId);
        Mockito.when(ds.getOwner()).thenReturn(owner);
        Mockito.when(ds.getDatasetScope()).thenReturn("USER_DATASET");
        Mockito.when(ds.getExperimentalKind()).thenReturn("REFERENCE_BUNDLE");
        Mockito.when(ds.getStorageUri()).thenReturn("datasets/u1/ref.xlsx");
        Mockito.when(ds.getQuestionCount()).thenReturn(10);
        when(evaluationDatasetRepository.findById(dsId)).thenReturn(Optional.of(ds));
        when(evaluationDatasetStorePort.openStream(eq("datasets/u1/ref.xlsx"))).thenReturn(new ByteArrayInputStream(bytes));
        when(labJobLifecycleService.findFirstActiveJobForScope(eq(userId), eq(projectId))).thenReturn(null);
        when(projectAccessService.requireOwnedProject(eq(userId), eq(projectId))).thenReturn(Mockito.mock(ProjectEntity.class));
        when(knowledgeIndexSnapshotRepository.findByProjectAndScopeProjectOrderByCreatedAtDesc(
                        eq(projectId), eq(KnowledgeSnapshotScopeType.PROJECT)))
                .thenReturn(List.of());

        UserEntity user = Mockito.mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        dsId,
                        TEST_CORPUS_ID,
                        projectId,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "emb-campaign-missing",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of("mxbai-embed-large", "nomic-embed-text"),
                        false,
                        null,
                        false,
                        false,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of());

        assertThatThrownBy(() -> orch.startJsonBenchmark(userId, "USER", BenchmarkKind.EMBEDDING_RETRIEVAL, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex -> {
                            ResponseStatusException r = (ResponseStatusException) ex;
                            assertThat(r.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                            assertThat(r.getReason()).contains("EMBEDDING_CAMPAIGN_MISSING_INDEX_SNAPSHOT");
                        });
        verify(asyncTaskService, never()).submitEvalEmbeddingCampaign(any(), any(), any(), any());
    }

    @Test
    void startJsonBenchmark_embeddingCampaign_recordsDimensionMismatchInsteadOfRejectingRun() throws Exception {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
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
                        evaluationWorkbookParser,
                        embeddingSpaceGuard,
                        evaluationCorpusApplicationService,
                        evaluationCorpusReadinessService,
                        evaluationCorpusRepository,
                        labBenchmarkConfigPreflightService,
                        labPresetAxisSupport,
                        labBenchmarkDefaultModelResolver,
                        runtimeObservability);

        UUID dsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        byte[] bytes = canonicalReferenceBundleBytes();

        EvaluationDatasetEntity ds = Mockito.mock(EvaluationDatasetEntity.class);
        UserEntity owner = Mockito.mock(UserEntity.class);
        Mockito.when(owner.getId()).thenReturn(userId);
        Mockito.when(ds.getOwner()).thenReturn(owner);
        Mockito.when(ds.getDatasetScope()).thenReturn("USER_DATASET");
        Mockito.when(ds.getExperimentalKind()).thenReturn("REFERENCE_BUNDLE");
        Mockito.when(ds.getStorageUri()).thenReturn("datasets/u1/ref.xlsx");
        Mockito.when(ds.getId()).thenReturn(dsId);
        when(evaluationDatasetRepository.findById(dsId)).thenReturn(Optional.of(ds));
        when(evaluationDatasetStorePort.openStream(eq("datasets/u1/ref.xlsx"))).thenReturn(new ByteArrayInputStream(bytes));
        when(labJobLifecycleService.findFirstActiveJobForScope(eq(userId), eq(projectId))).thenReturn(null);
        when(projectAccessService.requireOwnedProject(eq(userId), eq(projectId))).thenReturn(Mockito.mock(ProjectEntity.class));
        when(embeddingSpaceGuard.assertFitsPhysicalVectorColumnReturning("nomic-embed-text"))
                .thenThrow(
                        new ResponseStatusException(
                                HttpStatus.UNPROCESSABLE_ENTITY,
                                "EMBEDDING_DIMENSION_MISMATCH: model 'nomic-embed-text' outputs 768 dimensions but this deployment's vector_store.embedding column is fixed to 1024"));

        UserEntity user = Mockito.mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(evaluationCampaignRepository.save(any(EvaluationCampaignEntity.class)))
                .thenAnswer(
                        inv -> {
                            EvaluationCampaignEntity c = inv.getArgument(0);
                            if (c.getId() == null) {
                                c.setId(UUID.randomUUID());
                            }
                            return c;
                        });
        ArgumentCaptor<EvaluationRunEntity> runCaptor = ArgumentCaptor.forClass(EvaluationRunEntity.class);
        when(evaluationRunRepository.save(any(EvaluationRunEntity.class)))
                .thenAnswer(
                        inv -> {
                            EvaluationRunEntity r = inv.getArgument(0);
                            if (r.getId() == null) {
                                r.setId(UUID.randomUUID());
                            }
                            return r;
                        });
        UUID taskId = UUID.randomUUID();
        when(asyncTaskService.submitEvalEmbeddingCampaign(eq(userId), eq(projectId), any(UUID.class), any(UUID.class)))
                .thenReturn(taskId);
        when(evaluationRunRepository.findById(any(UUID.class)))
                .thenAnswer(
                        inv -> {
                            UUID id = inv.getArgument(0);
                            EvaluationRunEntity r = new EvaluationRunEntity();
                            r.setId(id);
                            return Optional.of(r);
                        });
        when(asyncTaskRepository.findById(taskId)).thenReturn(Optional.of(Mockito.mock(AsyncTaskEntity.class)));

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        dsId,
                        TEST_CORPUS_ID,
                        projectId,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "emb-campaign",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of("nomic-embed-text"),
                        false,
                        "cmp",
                        false,
                        false,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of());

        orch.startJsonBenchmark(userId, "USER", BenchmarkKind.EMBEDDING_RETRIEVAL, req);

        verify(evaluationRunRepository, times(2)).save(runCaptor.capture());
        EvaluationRunEntity savedRun =
                runCaptor.getAllValues().stream()
                        .filter(r -> r.getAggregatesJson() != null)
                        .findFirst()
                        .orElseThrow();
        assertThat(savedRun.getAggregatesJson())
                .containsEntry("embeddingCompatibilityStatus", "INCOMPATIBLE")
                .containsEntry("embeddingCompatibilityErrorCode", "EMBEDDING_DIMENSION_MISMATCH");
        verify(asyncTaskService)
                .submitEvalEmbeddingCampaign(eq(userId), isNull(), any(UUID.class), any(UUID.class));
    }

    private static byte[] canonicalReferenceBundleBytes() throws Exception {
        ClassPathResource r = new ClassPathResource(EvaluationReferenceBundleLoader.CLASSPATH_LOCATION);
        try (var in = r.getInputStream()) {
            return in.readAllBytes();
        }
    }

    @Test
    void startJsonBenchmark_rejectsDemoOrTooSmallReferenceBundle_beforeCreatingRun() throws Exception {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
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
                        evaluationWorkbookParser,
                        embeddingSpaceGuard,
                        evaluationCorpusApplicationService,
                        evaluationCorpusReadinessService,
                        evaluationCorpusRepository,
                        labBenchmarkConfigPreflightService,
                        labPresetAxisSupport,
                        labBenchmarkDefaultModelResolver,
                        runtimeObservability);

        UUID dsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // Create a tiny workbook with explicit demo strings.
        byte[] bytes = demoReferenceBundleBytes();

        EvaluationDatasetEntity ds = Mockito.mock(EvaluationDatasetEntity.class);
        UserEntity owner = Mockito.mock(UserEntity.class);
        Mockito.when(owner.getId()).thenReturn(userId);
        Mockito.when(ds.getOwner()).thenReturn(owner);
        Mockito.when(ds.getDatasetScope()).thenReturn("USER_DATASET");
        Mockito.when(ds.getExperimentalKind()).thenReturn("REFERENCE_BUNDLE");
        Mockito.when(ds.getStorageUri()).thenReturn("datasets/u1/demo.xlsx");
        when(evaluationDatasetRepository.findById(dsId)).thenReturn(Optional.of(ds));
        when(evaluationDatasetStorePort.openStream(eq("datasets/u1/demo.xlsx"))).thenReturn(new ByteArrayInputStream(bytes));

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        dsId,
                        TEST_CORPUS_ID,
                        null,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null,
                        false,
                        false,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of());

        assertThatThrownBy(() -> orch.startJsonBenchmark(userId, "USER", BenchmarkKind.RAG_PRESET_END_TO_END, req))
                .isInstanceOf(LabDatasetGateException.class)
                .satisfies(ex -> assertThat(((LabDatasetGateException) ex).code()).isIn(
                        "DATASET_TOO_SMALL",
                        "DATASET_DEMO_CONTENT_DETECTED",
                        "EXPERIMENTAL_DATASET_INVALID"));
    }

    @Test
    void startJsonBenchmark_autoReindex_requiresProjectContext() {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
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
                        evaluationWorkbookParser,
                        embeddingSpaceGuard,
                        evaluationCorpusApplicationService,
                        evaluationCorpusReadinessService,
                        evaluationCorpusRepository,
                        labBenchmarkConfigPreflightService,
                        labPresetAxisSupport,
                        labBenchmarkDefaultModelResolver,
                        runtimeObservability);

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        UUID.randomUUID(),
                        null,
                        null,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null,
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

        assertThatThrownBy(() -> orch.startJsonBenchmark(UUID.randomUUID(), "USER", BenchmarkKind.RAG_PRESET_END_TO_END, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode())
                                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void startJsonBenchmark_autoReindex_requiresActiveSnapshotMutationFlag() {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
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
                        evaluationWorkbookParser,
                        embeddingSpaceGuard,
                        evaluationCorpusApplicationService,
                        evaluationCorpusReadinessService,
                        evaluationCorpusRepository,
                        labBenchmarkConfigPreflightService,
                        labPresetAxisSupport,
                        labBenchmarkDefaultModelResolver,
                        runtimeObservability);

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        UUID.randomUUID(),
                        null,
                        UUID.randomUUID(),
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null,
                        true,
                        false,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of());

        assertThatThrownBy(() -> orch.startJsonBenchmark(UUID.randomUUID(), "USER", BenchmarkKind.RAG_PRESET_END_TO_END, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getStatusCode())
                                        .isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void startJsonBenchmark_classpathBootstrap_rejectsNonRagKind() {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
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
                        evaluationWorkbookParser,
                        embeddingSpaceGuard,
                        evaluationCorpusApplicationService,
                        evaluationCorpusReadinessService,
                        evaluationCorpusRepository,
                        labBenchmarkConfigPreflightService,
                        labPresetAxisSupport,
                        labBenchmarkDefaultModelResolver,
                        runtimeObservability);

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        UUID.randomUUID(),
                        null,
                        UUID.randomUUID(),
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null,
                        false,
                        false,
                        true,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        List.of());

        assertThatThrownBy(() -> orch.startJsonBenchmark(UUID.randomUUID(), "USER", BenchmarkKind.LLM_JUDGE_QA, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getReason())
                                        .isEqualTo(LabCorpusBootstrapErrors.UNSUPPORTED_BENCHMARK_KIND));
    }

    @Test
    void startJsonBenchmark_classpathBootstrap_requiresCorpusForRag() {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
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
                        evaluationWorkbookParser,
                        embeddingSpaceGuard,
                        evaluationCorpusApplicationService,
                        evaluationCorpusReadinessService,
                        evaluationCorpusRepository,
                        labBenchmarkConfigPreflightService,
                        labPresetAxisSupport,
                        labBenchmarkDefaultModelResolver,
                        runtimeObservability);

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        UUID.randomUUID(),
                        null,
                        null,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "n",
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null,
                        false,
                        false,
                        true,
                        true,
                        true,
                        null,
                        null,
                        null,
                        null,
                        List.of());

        assertThatThrownBy(() -> orch.startJsonBenchmark(UUID.randomUUID(), "USER", BenchmarkKind.RAG_PRESET_END_TO_END, req))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(
                        ex ->
                                assertThat(((ResponseStatusException) ex).getReason())
                                        .isEqualTo(EvaluationCorpusApplicationService.NO_CORPUS_SELECTED));
    }

    @Test
    void startJsonBenchmark_ragAcceptsCorpusDrivenRun_withoutNullCorpusReadinessAggregate() throws Exception {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
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
                        evaluationWorkbookParser,
                        embeddingSpaceGuard,
                        evaluationCorpusApplicationService,
                        evaluationCorpusReadinessService,
                        evaluationCorpusRepository,
                        labBenchmarkConfigPreflightService,
                        labPresetAxisSupport,
                        labBenchmarkDefaultModelResolver,
                        runtimeObservability);

        UUID dsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        byte[] bytes = canonicalReferenceBundleBytes();

        EvaluationDatasetEntity ds = Mockito.mock(EvaluationDatasetEntity.class);
        UserEntity owner = Mockito.mock(UserEntity.class);
        Mockito.when(owner.getId()).thenReturn(userId);
        Mockito.when(ds.getOwner()).thenReturn(owner);
        Mockito.when(ds.getDatasetScope()).thenReturn("USER_DATASET");
        Mockito.when(ds.getExperimentalKind()).thenReturn("REFERENCE_BUNDLE");
        Mockito.when(ds.getStorageUri()).thenReturn("datasets/u1/ref.xlsx");
        Mockito.when(ds.getId()).thenReturn(dsId);
        Mockito.when(ds.getSha256()).thenReturn("sha");
        when(evaluationDatasetRepository.findById(dsId)).thenReturn(Optional.of(ds));
        when(evaluationDatasetStorePort.openStream(eq("datasets/u1/ref.xlsx")))
                .thenReturn(new ByteArrayInputStream(bytes));
        when(labJobLifecycleService.findFirstActiveJobForScope(eq(userId), any())).thenReturn(null);

        UserEntity user = Mockito.mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        ArgumentCaptor<EvaluationRunEntity> runCaptor = ArgumentCaptor.forClass(EvaluationRunEntity.class);
        when(evaluationRunRepository.save(any(EvaluationRunEntity.class)))
                .thenAnswer(
                        inv -> {
                            EvaluationRunEntity r = inv.getArgument(0);
                            if (r.getId() == null) {
                                r.setId(UUID.randomUUID());
                            }
                            return r;
                        });
        UUID taskId = UUID.randomUUID();
        when(asyncTaskService.submitEvalRag(eq(userId), any(), any(UUID.class))).thenReturn(taskId);
        when(asyncTaskRepository.findById(taskId)).thenReturn(Optional.of(Mockito.mock(AsyncTaskEntity.class)));

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        dsId,
                        TEST_CORPUS_ID,
                        null,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "rag-run",
                        null,
                        null,
                        null,
                        null,
                        List.of("P0"),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null,
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

        assertThat(accepted.evaluationRunId()).isNotNull();
        assertThat(accepted.asyncTaskId()).isEqualTo(taskId);
        verify(evaluationRunRepository, times(2)).save(runCaptor.capture());
        EvaluationRunEntity savedRun =
                runCaptor.getAllValues().stream()
                        .filter(r -> r.getAggregatesJson() != null && r.getAggregatesJson().containsKey("corpusReadiness"))
                        .findFirst()
                        .orElseThrow();
        @SuppressWarnings("unchecked")
        Map<String, Object> corpusReadiness =
                (Map<String, Object>) savedRun.getAggregatesJson().get("corpusReadiness");
        assertThat(corpusReadiness).isNotNull();
        assertThat(corpusReadiness.values()).doesNotContainNull();
        assertThatCode(() -> Map.copyOf(corpusReadiness)).doesNotThrowAnyException();
        assertThat(savedRun.getLlmModelId()).isEqualTo("gemma3:4b");
        assertThat(savedRun.getEmbeddingModelId()).isEqualTo("mxbai-embed-large:latest");
        verify(asyncTaskService).submitEvalRag(eq(userId), any(), eq(accepted.evaluationRunId()));
        verify(projectAccessService, never()).requireOwnedProject(eq(userId), any());
    }

    @Test
    void startJsonBenchmark_p0Only_acceptsWithoutCorpusId() throws Exception {
        BenchmarkRunOrchestrator orch =
                new BenchmarkRunOrchestrator(
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
                        evaluationWorkbookParser,
                        embeddingSpaceGuard,
                        evaluationCorpusApplicationService,
                        evaluationCorpusReadinessService,
                        evaluationCorpusRepository,
                        labBenchmarkConfigPreflightService,
                        labPresetAxisSupport,
                        labBenchmarkDefaultModelResolver,
                        runtimeObservability);

        UUID dsId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        byte[] bytes = canonicalReferenceBundleBytes();

        EvaluationDatasetEntity ds = Mockito.mock(EvaluationDatasetEntity.class);
        UserEntity owner = Mockito.mock(UserEntity.class);
        Mockito.when(owner.getId()).thenReturn(userId);
        Mockito.when(ds.getOwner()).thenReturn(owner);
        Mockito.when(ds.getDatasetScope()).thenReturn("USER_DATASET");
        Mockito.when(ds.getExperimentalKind()).thenReturn("REFERENCE_BUNDLE");
        Mockito.when(ds.getStorageUri()).thenReturn("datasets/u1/ref.xlsx");
        Mockito.when(ds.getId()).thenReturn(dsId);
        Mockito.when(ds.getSha256()).thenReturn("sha");
        when(evaluationDatasetRepository.findById(dsId)).thenReturn(Optional.of(ds));
        when(evaluationDatasetStorePort.openStream(eq("datasets/u1/ref.xlsx")))
                .thenReturn(new ByteArrayInputStream(bytes));
        when(labJobLifecycleService.findFirstActiveJobForScope(eq(userId), any())).thenReturn(null);

        UserEntity user = Mockito.mock(UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        when(evaluationRunRepository.save(any(EvaluationRunEntity.class)))
                .thenAnswer(
                        inv -> {
                            EvaluationRunEntity r = inv.getArgument(0);
                            if (r.getId() == null) {
                                r.setId(UUID.randomUUID());
                            }
                            return r;
                        });
        UUID taskId = UUID.randomUUID();
        when(asyncTaskService.submitEvalRag(eq(userId), any(), any(UUID.class))).thenReturn(taskId);
        when(asyncTaskRepository.findById(taskId)).thenReturn(Optional.of(Mockito.mock(AsyncTaskEntity.class)));

        StartBenchmarkRunRequest req =
                new StartBenchmarkRunRequest(
                        dsId,
                        null,
                        null,
                        EvaluationRunKind.PRODUCT_EXPLORATION,
                        "p0-no-corpus",
                        null,
                        null,
                        null,
                        null,
                        List.of("P0"),
                        null,
                        null,
                        List.of(),
                        List.of(),
                        false,
                        null,
                        false,
                        false,
                        false,
                        false,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of());

        BenchmarkJobAccepted accepted =
                orch.startJsonBenchmark(userId, "USER", BenchmarkKind.RAG_PRESET_END_TO_END, req);

        assertThat(accepted.evaluationRunId()).isNotNull();
        assertThat(accepted.asyncTaskId()).isEqualTo(taskId);
        verify(evaluationCorpusReadinessService, never()).getReadiness(any(), any());
        verify(asyncTaskService).submitEvalRag(eq(userId), any(), eq(accepted.evaluationRunId()));
    }

    private static byte[] demoReferenceBundleBytes() throws Exception {
        try (Workbook wb = new XSSFWorkbook()) {
            // Minimal required reference sheets (headers + single row), with demo content.
            Sheet readme = wb.createSheet("README");
            readme.createRow(0).createCell(0).setCellValue("Item");
            readme.getRow(0).createCell(1).setCellValue("Decision");
            readme.createRow(1).createCell(0).setCellValue("Protocol version");
            readme.getRow(1).createCell(1).setCellValue("demo");

            Sheet corpus = wb.createSheet("corpus_documents");
            corpus.createRow(0).createCell(0).setCellValue("document_id");
            corpus.createRow(1).createCell(0).setCellValue("DOC_1");

            Sheet chunks = wb.createSheet("chunk_registry");
            chunks.createRow(0).createCell(0).setCellValue("chunk_id");
            chunks.getRow(0).createCell(1).setCellValue("document_id");
            chunks.createRow(1).createCell(0).setCellValue("CHUNK_1");
            chunks.getRow(1).createCell(1).setCellValue("DOC_1");

            Sheet llm = wb.createSheet("llm_reader_questions");
            llm.createRow(0).createCell(0).setCellValue("id");
            llm.getRow(0).createCell(1).setCellValue("question");
            llm.createRow(1).createCell(0).setCellValue("LLM_Q1");
            llm.getRow(1).createCell(1).setCellValue("Provide a grounded summary of the sample acta.");

            Sheet emb = wb.createSheet("embedding_retrieval_queries");
            emb.createRow(0).createCell(0).setCellValue("id");
            emb.getRow(0).createCell(1).setCellValue("query");
            emb.createRow(1).createCell(0).setCellValue("EMB_Q1");
            emb.getRow(1).createCell(1).setCellValue("sample acta evidence");

            Sheet rag = wb.createSheet("rag_preset_questions_enriched");
            rag.createRow(0).createCell(0).setCellValue("id");
            rag.getRow(0).createCell(1).setCellValue("question");
            rag.getRow(0).createCell(2).setCellValue("expected_answer");
            rag.createRow(1).createCell(0).setCellValue("RAG_Q1");
            rag.getRow(1).createCell(1).setCellValue("What does the sample acta contain?");
            rag.getRow(1).createCell(2).setCellValue("Evidence: Acta sample 1");

            Sheet llmCand = wb.createSheet("llm_candidates");
            llmCand.createRow(0).createCell(0).setCellValue("candidate_id");
            llmCand.createRow(1).createCell(0).setCellValue("c1");

            Sheet embCand = wb.createSheet("embedding_candidates");
            embCand.createRow(0).createCell(0).setCellValue("candidate_id");
            embCand.createRow(1).createCell(0).setCellValue("c1");

            Sheet catalog = wb.createSheet("rag_preset_catalog_P0_P14");
            catalog.createRow(0).createCell(0).setCellValue("preset_id");
            catalog.createRow(1).createCell(0).setCellValue("P0");

            Sheet metric = wb.createSheet("metric_spec");
            metric.createRow(0).createCell(0).setCellValue("metric_id");
            metric.createRow(1).createCell(0).setCellValue("m1");

            Sheet schema = wb.createSheet("result_schema");
            schema.createRow(0).createCell(0).setCellValue("field");
            schema.createRow(1).createCell(0).setCellValue("outcome");

            Sheet summary = wb.createSheet("summary_counts");
            summary.createRow(0).createCell(0).setCellValue("Dataset");
            summary.createRow(1).createCell(0).setCellValue("REFERENCE_BUNDLE");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }
}

