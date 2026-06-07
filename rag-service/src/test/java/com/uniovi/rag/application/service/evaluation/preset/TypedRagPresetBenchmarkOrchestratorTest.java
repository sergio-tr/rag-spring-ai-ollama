package com.uniovi.rag.application.service.evaluation.preset;

import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.application.service.evaluation.TypedBenchmarkDataset;
import com.uniovi.rag.application.service.knowledge.KnowledgePipelineOrchestrator;
import com.uniovi.rag.application.service.knowledge.LabIndexProfileOverrideFactory;
import com.uniovi.rag.application.service.knowledge.ProjectIndexProfileService;
import com.uniovi.rag.application.service.knowledge.KnowledgeSnapshotService;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagImplementationProperties;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.domain.evaluation.snapshot.EmbeddingExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.snapshot.LlmExperimentalSnapshot;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetDefinition;
import com.uniovi.rag.domain.evaluation.workbook.RagPresetQuestion;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.evaluation.workbook.DifficultyLevel;
import com.uniovi.rag.infrastructure.persistence.EvaluationRunRepository;
import com.uniovi.rag.infrastructure.persistence.KnowledgeIndexSnapshotRepository;
import com.uniovi.rag.infrastructure.persistence.ProjectRepository;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ResolvedConfigSnapshotEntity;
import com.uniovi.rag.application.service.evaluation.AbstractEvaluationService;
import com.uniovi.rag.application.result.evaluation.RagPresetBenchmarkRunPayload;
import com.uniovi.rag.application.service.evaluation.EvaluationService;
import com.uniovi.rag.application.service.evaluation.EvaluationPayloadMapper;
import com.uniovi.rag.application.service.evaluation.EvaluationTestFixtures;
import com.uniovi.rag.application.service.evaluation.LabJobProgressTracker;
import com.uniovi.rag.application.service.evaluation.baseline.ExperimentalSnapshotFactory;
import com.uniovi.rag.infrastructure.observability.RuntimeObservability;
import org.springframework.beans.factory.ObjectProvider;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.knowledge.ProjectIndexProfile;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TypedRagPresetBenchmarkOrchestratorTest {

    @Mock private EvaluationService evaluationService;
    @Mock private EvaluationRunRepository evaluationRunRepository;
    @Mock private ProjectRepository projectRepository;
    @Mock private ExperimentalSnapshotFactory experimentalSnapshotFactory;
    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;
    @Mock private KnowledgeIndexSnapshotRepository knowledgeIndexSnapshotRepository;
    @Mock private KnowledgePipelineOrchestrator knowledgePipelineOrchestrator;
    @Mock private ProjectIndexProfileService projectIndexProfileService;
    @Mock private LabIndexProfileOverrideFactory labIndexProfileOverrideFactory;
    @Mock private CorpusAvailabilityGate corpusAvailabilityGate;

    @BeforeEach
    void defaultCorpusAvailabilityGate() {
        Mockito.lenient()
                .when(corpusAvailabilityGate.evaluate(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(new CorpusAvailabilityGate.Result(true, 1, List.of(UUID.randomUUID()), 1, 3L, null, null));
        Mockito.lenient()
                .when(corpusAvailabilityGate.probe(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any()))
                .thenReturn(Map.of());
    }

    private static LlmExperimentalSnapshot llmSnap() {
        return new LlmExperimentalSnapshot(
                "lm", 0.2, 0.9, 5, null, 1.05, 8192, 512, null, 42, List.of(), null, false, List.of());
    }

    private static EmbeddingExperimentalSnapshot embSnap() {
        return new EmbeddingExperimentalSnapshot("emb", null, null, null, null, null, null, List.of());
    }

    private TypedRagPresetBenchmarkOrchestrator orchestrator() {
        @SuppressWarnings("unchecked")
        ObjectProvider<LabJobProgressTracker> labJobProgressTracker = Mockito.mock(ObjectProvider.class);
        LabEvaluationSnapshotService labEvaluationSnapshotService =
                new LabEvaluationSnapshotService(
                        knowledgeSnapshotService,
                        knowledgePipelineOrchestrator,
                        projectIndexProfileService,
                        labIndexProfileOverrideFactory,
                        knowledgeIndexSnapshotRepository,
                        evaluationRunRepository,
                        projectRepository,
                        labJobProgressTracker);
        @SuppressWarnings("unchecked")
        ObjectProvider<RuntimeObservability> runtimeObservability = Mockito.mock(ObjectProvider.class);
        return new TypedRagPresetBenchmarkOrchestrator(
                evaluationService,
                evaluationRunRepository,
                experimentalSnapshotFactory,
                labEvaluationSnapshotService,
                new LabPresetRunPlanService(labEvaluationSnapshotService),
                corpusAvailabilityGate,
                runtimeObservability);
    }

    private static EvaluationRunEntity runWithAutoReindex(ProjectEntity project, boolean enabled) {
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setProject(project);
        if (!enabled) {
            return run;
        }
        ResolvedConfigSnapshotEntity cfg = ResolvedConfigSnapshotEntity.newForInsert();
        cfg.setId(UUID.randomUUID());
        cfg.setConfigHash("cfg-hash");
        run.setResolvedConfigSnapshot(cfg);
        run.setAggregatesJson(
                Map.of(
                        "autoReindexPolicy",
                        Map.of(
                                "enabled", true,
                                "allowActiveSnapshotMutation", true,
                                "reuseCompatibleActiveSnapshot", true,
                                "failOnReindexFailure", true)));
        return run;
    }

    @Test
    void empty_catalog_single_evaluate_call_no_preset_codes_on_arbitrary_rows() {
        UUID runId = UUID.randomUUID();
        when(evaluationRunRepository.findById(runId)).thenReturn(Optional.empty());
        when(experimentalSnapshotFactory.buildLlmSnapshot(null)).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(null)).thenReturn(embSnap());

        RagPresetQuestion q = sampleQuestion();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("question", q.question());
        rows.add(row);
        when(evaluationService.evaluateWithConfigurationForRagPresetQuestions(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.eq(List.of(q)),
                        ArgumentMatchers.any()))
                .thenReturn(EvaluationTestFixtures.ragBatchFromRowMaps(rows));

        RagPresetBenchmarkRunPayload out =
                orchestrator()
                        .runPresetBenchmark(
                                runId,
                                new TypedBenchmarkDataset.RagPresetQuestions(List.of(q), List.of()),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                null,
                                null);

        List<Map<String, Object>> outRows = EvaluationTestFixtures.toRowMaps(out);
        assertThat(outRows).hasSize(rows.size());
        assertThat(outRows.get(0).get(BenchmarkResultRowKeys.PRESET_CODE)).isNull();
        assertThat(outRows.get(0).get(BenchmarkResultRowKeys.LLM_MODEL_ID)).isEqualTo("lm");
        assertThat(outRows.get(0).get(BenchmarkResultRowKeys.EMBEDDING_MODEL_ID)).isEqualTo("emb");
        verify(evaluationRunRepository).findById(runId);
    }

    @Test
    void catalog_with_p13_emits_not_supported_without_evaluation_service_for_blocked_preset() {
        when(experimentalSnapshotFactory.buildLlmSnapshot(null)).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(null)).thenReturn(embSnap());

        RagPresetQuestion q = sampleQuestion();
        RagPresetDefinition p13 =
                new RagPresetDefinition(
                        RagExperimentalPresetCode.P13,
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "",
                        "");

        RagPresetBenchmarkRunPayload out =
                orchestrator()
                        .runPresetBenchmark(
                                null,
                                new TypedBenchmarkDataset.RagPresetQuestions(List.of(q), List.of(p13)),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                null,
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = EvaluationTestFixtures.toRowMaps(out);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(BenchmarkResultRowKeys.ITEM_OUTCOME))
                .isEqualTo(BenchmarkItemOutcome.NOT_SUPPORTED.name());
        assertThat(rows.get(0).get(BenchmarkResultRowKeys.ERROR_CODE))
                .isEqualTo("PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED");
        @SuppressWarnings("unchecked")
        Map<String, Object> mp = (Map<String, Object>) rows.get(0).get("metrics_payload");
        assertThat(mp.get("groupKey")).isEqualTo(LabPresetRunGroupKey.MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN.name());
        assertThat(mp.get("runPlanVersion")).isNotNull();
        @SuppressWarnings("unchecked")
        var es = EvaluationPayloadMapper.summaryToMap(out.evaluationSummary());
        assertThat(es).containsKey("runPlan");
        Mockito.verify(evaluationService, Mockito.never())
                .evaluateWithConfigurationForRagPresetQuestions(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any());
    }

    @Test
    void p8_with_chunk_snapshot_is_skipped_and_does_not_call_evaluation_service() {
        when(experimentalSnapshotFactory.buildLlmSnapshot(ArgumentMatchers.any())).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(ArgumentMatchers.any())).thenReturn(embSnap());
        when(knowledgeSnapshotService.findActiveProjectSnapshot(ArgumentMatchers.any()))
                .thenReturn(Optional.of(mockSnapshot("CHUNK_LEVEL", true, "h1", UUID.randomUUID())));

        RagPresetQuestion q = sampleQuestion();
        RagPresetDefinition p8 = preset(RagExperimentalPresetCode.P8);
        // Run entity with project id so orchestrator can resolve active snapshot.
        var run = new EvaluationRunEntity();
        ProjectEntity project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(UUID.randomUUID());
        run.setProject(project);
        when(evaluationRunRepository.findById(ArgumentMatchers.any())).thenReturn(Optional.of(run));

        RagPresetBenchmarkRunPayload out =
                orchestrator()
                        .runPresetBenchmark(
                                UUID.randomUUID(),
                                new TypedBenchmarkDataset.RagPresetQuestions(List.of(q), List.of(p8)),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                null,
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = EvaluationTestFixtures.toRowMaps(out);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(BenchmarkResultRowKeys.ITEM_OUTCOME)).isEqualTo(BenchmarkItemOutcome.SKIPPED.name());
        assertThat(String.valueOf(rows.get(0).get(BenchmarkResultRowKeys.ERROR_CODE)))
                .isEqualTo("NO_COMPATIBLE_SNAPSHOT");
        assertThat(rows.get(0)).containsKey("metrics_payload");
        @SuppressWarnings("unchecked")
        Map<String, Object> mp = (Map<String, Object>) rows.get(0).get("metrics_payload");
        assertThat(mp.get("groupKey")).isEqualTo(LabPresetRunGroupKey.HYBRID_METADATA.name());
        assertThat(mp.get("runPlanVersion")).isNotNull();
        @SuppressWarnings("unchecked")
        var es = EvaluationPayloadMapper.summaryToMap(out.evaluationSummary());
        assertThat(es).containsKey("runPlan");
        Mockito.verify(evaluationService, Mockito.never())
                .evaluateWithConfigurationForRagPresetQuestions(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any());
    }

    @Test
    void autoReindex_true_with_incompatible_snapshot_rebuilds_once_for_group_and_reuses_snapshot_for_all_group_presets() {
        when(experimentalSnapshotFactory.buildLlmSnapshot(ArgumentMatchers.any())).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(ArgumentMatchers.any())).thenReturn(embSnap());

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(projectId);
        EvaluationRunEntity run = runWithAutoReindex(project, true);
        UUID runId = UUID.randomUUID();
        when(evaluationRunRepository.findById(runId)).thenReturn(Optional.of(run));
        when(evaluationRunRepository.save(ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));

        // First: chunk snapshot (incompatible for HYBRID_METADATA); after rebuild: hybrid snapshot.
        UUID oldSnapId = UUID.randomUUID();
        UUID newSnapId = UUID.randomUUID();
        when(knowledgeSnapshotService.findActiveProjectSnapshot(projectId))
                .thenReturn(Optional.of(mockSnapshot("CHUNK_LEVEL", true, "hOld", oldSnapId)))
                .thenReturn(Optional.of(mockSnapshot("CHUNK_LEVEL", true, "hOld", oldSnapId)))
                .thenReturn(Optional.of(mockSnapshot("CHUNK_LEVEL", true, "hOld", oldSnapId)))
                .thenReturn(Optional.of(mockSnapshot("HYBRID", true, "hNew", newSnapId)));

        when(projectIndexProfileService.ensureDefault(projectId)).thenReturn(sampleProfile(projectId));
        when(labIndexProfileOverrideFactory.buildEffectiveProfile(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any()))
                .thenReturn(sampleProfile(projectId));
        when(knowledgePipelineOrchestrator.rebuildScopeWithProfileOverride(
                        ArgumentMatchers.eq(projectId),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any()))
                .thenReturn(newSnapId);
        when(knowledgeIndexSnapshotRepository.findById(newSnapId))
                .thenReturn(Optional.of(mockSnapshot("HYBRID", true, "hNew", newSnapId)));

        RagPresetQuestion q = sampleQuestion();
        RagPresetDefinition p8 = preset(RagExperimentalPresetCode.P8);
        RagPresetDefinition p9 = preset(RagExperimentalPresetCode.P9);

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(new HashMap<>(Map.of("question", q.question())));
        when(evaluationService.evaluateWithConfigurationForRagPresetQuestions(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.anyList(),
                        ArgumentMatchers.any()))
                .thenReturn(EvaluationTestFixtures.ragBatchFromRowMaps(rows));
        RagPresetBenchmarkRunPayload out =
                orchestrator()
                        .runPresetBenchmark(
                                runId,
                                new TypedBenchmarkDataset.RagPresetQuestions(List.of(q), List.of(p8, p9)),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                Set.of(RagExperimentalPresetCode.P8, RagExperimentalPresetCode.P9),
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> outRows = EvaluationTestFixtures.toRowMaps(out);
        assertThat(outRows).isNotEmpty();
        // All emitted rows should carry effectiveGroupSnapshotId = newSnapId for HYBRID group.
        for (Map<String, Object> r : outRows) {
            @SuppressWarnings("unchecked")
            Map<String, Object> mp = (Map<String, Object>) r.get("metrics_payload");
            assertThat(mp.get("groupKey")).isEqualTo(LabPresetRunGroupKey.HYBRID_METADATA.name());
            assertThat(mp.get("effectiveGroupSnapshotId")).isEqualTo(newSnapId.toString());
            assertThat(mp.get("materializationStrategy")).isEqualTo("HYBRID");
            assertThat(mp.get("reindexAction")).isEqualTo("BUILD_AND_ACTIVATE");
            assertThat(mp.get("forcedSnapshotSelection")).isEqualTo(true);
        }

        verify(knowledgePipelineOrchestrator, Mockito.times(1))
                .rebuildScopeWithProfileOverride(
                        ArgumentMatchers.eq(projectId),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any());
    }

    @Test
    void autoReindex_reuses_existing_compatible_snapshot_even_when_active_snapshot_is_incompatible() {
        when(experimentalSnapshotFactory.buildLlmSnapshot(ArgumentMatchers.any())).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(ArgumentMatchers.any())).thenReturn(embSnap());

        UUID projectId = UUID.randomUUID();
        UUID compatibleSnapshotId = UUID.randomUUID();
        ProjectEntity project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(projectId);
        EvaluationRunEntity run = runWithAutoReindex(project, true);
        UUID runId = UUID.randomUUID();
        when(evaluationRunRepository.findById(runId)).thenReturn(Optional.of(run));
        when(evaluationRunRepository.save(ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        when(knowledgeSnapshotService.findActiveProjectSnapshot(projectId))
                .thenReturn(Optional.of(mockSnapshot("CHUNK_LEVEL", true, "active", UUID.randomUUID())));
        when(knowledgeSnapshotService.findCompatibleProjectSnapshot(ArgumentMatchers.eq(projectId), ArgumentMatchers.any()))
                .thenReturn(Optional.of(mockSnapshot("HYBRID", true, "compatible", compatibleSnapshotId)));

        RagPresetQuestion q = sampleQuestion();
        RagPresetDefinition p8 = preset(RagExperimentalPresetCode.P8);
        when(evaluationService.evaluateWithConfigurationForRagPresetQuestions(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.anyList(),
                        ArgumentMatchers.any()))
                .thenReturn(EvaluationTestFixtures.ragBatchFromRowMaps(baseRowsFor(1)));
        RagPresetBenchmarkRunPayload out =
                orchestrator()
                        .runPresetBenchmark(
                                runId,
                                new TypedBenchmarkDataset.RagPresetQuestions(List.of(q), List.of(p8)),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                Set.of(RagExperimentalPresetCode.P8),
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = EvaluationTestFixtures.toRowMaps(out);
        @SuppressWarnings("unchecked")
        Map<String, Object> mp = (Map<String, Object>) rows.get(0).get("metrics_payload");
        assertThat(mp.get("effectiveGroupSnapshotId")).isEqualTo(compatibleSnapshotId.toString());
        assertThat(mp.get("reindexAction")).isEqualTo("REUSE_COMPATIBLE_SNAPSHOT");
        verify(knowledgePipelineOrchestrator, never())
                .rebuildScopeWithProfileOverride(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any());
    }

    @Test
    void p4_with_chunk_snapshot_missing_metadata_is_skipped_and_does_not_call_evaluation_service() {
        when(experimentalSnapshotFactory.buildLlmSnapshot(ArgumentMatchers.any())).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(ArgumentMatchers.any())).thenReturn(embSnap());
        when(knowledgeSnapshotService.findActiveProjectSnapshot(ArgumentMatchers.any()))
                .thenReturn(Optional.of(mockSnapshot("CHUNK_LEVEL", false, "h2", UUID.randomUUID())));

        RagPresetQuestion q = sampleQuestion();
        RagPresetDefinition p4 = preset(RagExperimentalPresetCode.P4);
        var run = new EvaluationRunEntity();
        ProjectEntity project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(UUID.randomUUID());
        run.setProject(project);
        when(evaluationRunRepository.findById(ArgumentMatchers.any())).thenReturn(Optional.of(run));

        RagPresetBenchmarkRunPayload out =
                orchestrator()
                        .runPresetBenchmark(
                                UUID.randomUUID(),
                                new TypedBenchmarkDataset.RagPresetQuestions(List.of(q), List.of(p4)),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                null,
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = EvaluationTestFixtures.toRowMaps(out);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(BenchmarkResultRowKeys.ITEM_OUTCOME)).isEqualTo(BenchmarkItemOutcome.SKIPPED.name());
        assertThat(String.valueOf(rows.get(0).get(BenchmarkResultRowKeys.ERROR_CODE)))
                .isEqualTo("NO_COMPATIBLE_SNAPSHOT");
        Mockito.verify(evaluationService, Mockito.never())
                .evaluateWithConfigurationForRagPresetQuestions(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any());
    }

    @Test
    void p2_readyDocumentsWithoutSnapshot_exportsReindexRequiredWithCorpusIds() {
        when(experimentalSnapshotFactory.buildLlmSnapshot(ArgumentMatchers.any())).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(ArgumentMatchers.any())).thenReturn(embSnap());
        when(knowledgeSnapshotService.findActiveProjectSnapshot(ArgumentMatchers.any())).thenReturn(Optional.empty());

        UUID projectId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        ProjectEntity project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(projectId);
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setProject(project);
        when(evaluationRunRepository.findById(ArgumentMatchers.any())).thenReturn(Optional.of(run));
        when(corpusAvailabilityGate.evaluate(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.argThat(List::isEmpty)))
                .thenReturn(
                        new CorpusAvailabilityGate.Result(
                                false,
                                1,
                                List.of(documentId),
                                1,
                                0L,
                                CorpusAvailabilityGate.REINDEX_REQUIRED,
                                "Documents are READY, but no snapshot was selected for corpus evidence."));
        when(corpusAvailabilityGate.probe(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.argThat(List::isEmpty)))
                .thenReturn(
                        Map.of(
                                "corpusRequired",
                                true,
                                "corpusAvailable",
                                false,
                                "evaluationCorpusProjectId",
                                projectId.toString(),
                                "evaluationCorpusDocumentIds",
                                List.of(documentId.toString()),
                                "skippedReasonCode",
                                CorpusAvailabilityGate.REINDEX_REQUIRED));

        RagPresetBenchmarkRunPayload out =
                orchestrator()
                        .runPresetBenchmark(
                                UUID.randomUUID(),
                                new TypedBenchmarkDataset.RagPresetQuestions(
                                        List.of(sampleQuestion()), List.of(preset(RagExperimentalPresetCode.P2))),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                null,
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = EvaluationTestFixtures.toRowMaps(out);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(BenchmarkResultRowKeys.ITEM_OUTCOME)).isEqualTo(BenchmarkItemOutcome.SKIPPED.name());
        assertThat(rows.get(0).get(BenchmarkResultRowKeys.ERROR_CODE)).isEqualTo(CorpusAvailabilityGate.REINDEX_REQUIRED);
        @SuppressWarnings("unchecked")
        Map<String, Object> mp = (Map<String, Object>) rows.get(0).get("metrics_payload");
        assertThat(mp.get("selectedSnapshotIds")).isEqualTo(List.of());
        assertThat(mp.get("evaluationCorpusDocumentIds")).isEqualTo(List.of(documentId.toString()));
        Mockito.verify(evaluationService, Mockito.never())
                .evaluateWithConfigurationForRagPresetQuestions(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any());
    }

    @Test
    void p2_noDocumentsOverridesIndexReasonWithNoDocuments() {
        when(experimentalSnapshotFactory.buildLlmSnapshot(ArgumentMatchers.any())).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(ArgumentMatchers.any())).thenReturn(embSnap());
        when(knowledgeSnapshotService.findActiveProjectSnapshot(ArgumentMatchers.any())).thenReturn(Optional.empty());

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(projectId);
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setProject(project);
        when(evaluationRunRepository.findById(ArgumentMatchers.any())).thenReturn(Optional.of(run));
        when(corpusAvailabilityGate.evaluate(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.argThat(List::isEmpty)))
                .thenReturn(
                        new CorpusAvailabilityGate.Result(
                                false,
                                0,
                                List.of(),
                                0,
                                0L,
                                CorpusAvailabilityGate.NO_DOCUMENTS,
                                "The selected evaluation corpus has no documents."));
        when(corpusAvailabilityGate.probe(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.argThat(List::isEmpty)))
                .thenReturn(
                        Map.of(
                                "corpusRequired",
                                true,
                                "corpusAvailable",
                                false,
                                "projectDocumentCount",
                                0,
                                "skippedReasonCode",
                                CorpusAvailabilityGate.NO_DOCUMENTS));

        RagPresetBenchmarkRunPayload out =
                orchestrator()
                        .runPresetBenchmark(
                                UUID.randomUUID(),
                                new TypedBenchmarkDataset.RagPresetQuestions(
                                        List.of(sampleQuestion()), List.of(preset(RagExperimentalPresetCode.P2))),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                null,
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = EvaluationTestFixtures.toRowMaps(out);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(BenchmarkResultRowKeys.ERROR_CODE)).isEqualTo(CorpusAvailabilityGate.NO_DOCUMENTS);
        @SuppressWarnings("unchecked")
        Map<String, Object> mp = (Map<String, Object>) rows.get(0).get("metrics_payload");
        assertThat(mp.get("projectDocumentCount")).isEqualTo(0);
    }

    @Test
    void p3_with_hybrid_snapshot_executes() {
        when(experimentalSnapshotFactory.buildLlmSnapshot(ArgumentMatchers.any())).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(ArgumentMatchers.any())).thenReturn(embSnap());
        when(knowledgeSnapshotService.findActiveProjectSnapshot(ArgumentMatchers.any()))
                .thenReturn(Optional.of(mockSnapshot("HYBRID", true, "h3", UUID.randomUUID())));

        List<RagPresetQuestion> questions = List.of(sampleQuestion());
        RagPresetDefinition p3 = preset(RagExperimentalPresetCode.P3);
        when(evaluationService.evaluateWithConfigurationForRagPresetQuestions(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq(questions),
                ArgumentMatchers.any()))
                .thenReturn(EvaluationTestFixtures.ragBatchFromRowMaps(baseRowsFor(questions.size())));
        var run = new EvaluationRunEntity();
        ProjectEntity project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(UUID.randomUUID());
        run.setProject(project);
        when(evaluationRunRepository.findById(ArgumentMatchers.any())).thenReturn(Optional.of(run));

        orchestrator()
                .runPresetBenchmark(
                        UUID.randomUUID(),
                        new TypedBenchmarkDataset.RagPresetQuestions(questions, List.of(p3)),
                        new RagFeatureConfiguration(),
                        new RagImplementationProperties(),
                        null,
                        null);

        Mockito.verify(evaluationService)
                .evaluateWithConfigurationForRagPresetQuestions(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.eq(questions),
                        ArgumentMatchers.any());
    }

    @Test
    void p4_compatibleSnapshot_executesAndExportsCorpusSnapshotEvidence() {
        when(experimentalSnapshotFactory.buildLlmSnapshot(ArgumentMatchers.any())).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(ArgumentMatchers.any())).thenReturn(embSnap());
        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        UUID documentId = UUID.randomUUID();
        when(knowledgeSnapshotService.findActiveProjectSnapshot(ArgumentMatchers.eq(projectId)))
                .thenReturn(Optional.of(mockSnapshot("CHUNK_LEVEL", true, "hMeta", snapshotId)));

        ProjectEntity project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(projectId);
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setProject(project);
        when(evaluationRunRepository.findById(ArgumentMatchers.any())).thenReturn(Optional.of(run));
        when(corpusAvailabilityGate.evaluate(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.argThat(ids -> ids != null && ids.size() == 1 && snapshotId.equals(ids.get(0)))))
                .thenReturn(
                        new CorpusAvailabilityGate.Result(true, 1, List.of(documentId), 1, 7L, null, null));
        when(corpusAvailabilityGate.probe(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.argThat(ids -> ids != null && ids.size() == 1 && snapshotId.equals(ids.get(0)))))
                .thenReturn(
                        Map.of(
                                "corpusRequired",
                                true,
                                "corpusAvailable",
                                true,
                                "evaluationCorpusProjectId",
                                projectId.toString(),
                                "evaluationCorpusDocumentIds",
                                List.of(documentId.toString()),
                                "vectorChunkRowCount",
                                7L));
        when(evaluationService.evaluateWithConfigurationForRagPresetQuestions(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.anyList(),
                        ArgumentMatchers.any()))
                .thenReturn(EvaluationTestFixtures.ragBatchFromRowMaps(baseRowsFor(1)));
        RagPresetBenchmarkRunPayload out =
                orchestrator()
                        .runPresetBenchmark(
                                UUID.randomUUID(),
                                new TypedBenchmarkDataset.RagPresetQuestions(
                                        List.of(sampleQuestion()), List.of(preset(RagExperimentalPresetCode.P4))),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                null,
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = EvaluationTestFixtures.toRowMaps(out);
        @SuppressWarnings("unchecked")
        Map<String, Object> mp = (Map<String, Object>) rows.get(0).get("metrics_payload");
        assertThat(mp.get("selectedSnapshotIds")).isEqualTo(List.of(snapshotId.toString()));
        assertThat(mp.get("evaluationCorpusProjectId")).isEqualTo(projectId.toString());
        assertThat(mp.get("evaluationCorpusDocumentIds")).isEqualTo(List.of(documentId.toString()));
        assertThat(mp.get("vectorChunkRowCount")).isEqualTo(7L);
    }

    @Test
    void noActiveIndexExceptionIsExportedAsReindexRequired() {
        when(experimentalSnapshotFactory.buildLlmSnapshot(ArgumentMatchers.any())).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(ArgumentMatchers.any())).thenReturn(embSnap());
        when(knowledgeSnapshotService.findActiveProjectSnapshot(ArgumentMatchers.any()))
                .thenReturn(Optional.of(mockSnapshot("HYBRID", true, "hHybrid", UUID.randomUUID())));
        when(evaluationService.evaluateWithConfigurationForRagPresetQuestions(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.anyList(),
                        ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("NO_ACTIVE_INDEX"));
        ProjectEntity project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(UUID.randomUUID());
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setProject(project);
        when(evaluationRunRepository.findById(ArgumentMatchers.any())).thenReturn(Optional.of(run));

        RagPresetBenchmarkRunPayload out =
                orchestrator()
                        .runPresetBenchmark(
                                UUID.randomUUID(),
                                new TypedBenchmarkDataset.RagPresetQuestions(
                                        List.of(sampleQuestion()), List.of(preset(RagExperimentalPresetCode.P2))),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                null,
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = EvaluationTestFixtures.toRowMaps(out);
        assertThat(rows.get(0).get(BenchmarkResultRowKeys.ITEM_OUTCOME)).isEqualTo(BenchmarkItemOutcome.FAILED.name());
        assertThat(rows.get(0).get(BenchmarkResultRowKeys.ERROR_CODE)).isEqualTo("REINDEX_REQUIRED");
        assertThat(rows.get(0).get(BenchmarkResultRowKeys.REASON))
                .isEqualTo("Documents may exist, but no compatible snapshot was selected for this preset.");
    }

    @Test
    void p0_without_snapshot_is_skipped_and_does_not_call_evaluation_service() {
        when(experimentalSnapshotFactory.buildLlmSnapshot(ArgumentMatchers.any())).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(ArgumentMatchers.any())).thenReturn(embSnap());

        UUID projectId = UUID.randomUUID();
        ProjectEntity project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(projectId);
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setProject(project);
        UUID runId = UUID.randomUUID();
        when(evaluationRunRepository.findById(runId)).thenReturn(Optional.of(run));
        when(knowledgeSnapshotService.findActiveProjectSnapshot(projectId)).thenReturn(Optional.empty());
        when(corpusAvailabilityGate.evaluate(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.eq(List.of())))
                .thenReturn(
                        new CorpusAvailabilityGate.Result(
                                false,
                                1,
                                List.of(UUID.randomUUID()),
                                1,
                                0L,
                                CorpusAvailabilityGate.REINDEX_REQUIRED,
                                "Documents are READY, but no snapshot was selected for corpus evidence."));
        when(corpusAvailabilityGate.probe(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.eq(List.of())))
                .thenReturn(
                        Map.of(
                                "skippedReasonCode",
                                CorpusAvailabilityGate.REINDEX_REQUIRED,
                                "selectedSnapshotIds",
                                List.of()));

        List<RagPresetQuestion> questions = List.of(sampleQuestion());
        RagPresetDefinition p0 = preset(RagExperimentalPresetCode.P0);

        RagPresetBenchmarkRunPayload out =
                orchestrator()
                        .runPresetBenchmark(
                                runId,
                                new TypedBenchmarkDataset.RagPresetQuestions(questions, List.of(p0)),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                null,
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = EvaluationTestFixtures.toRowMaps(out);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(BenchmarkResultRowKeys.ITEM_OUTCOME)).isEqualTo(BenchmarkItemOutcome.SKIPPED.name());
        assertThat(rows.get(0).get(BenchmarkResultRowKeys.ERROR_CODE)).isEqualTo(CorpusAvailabilityGate.REINDEX_REQUIRED);
        @SuppressWarnings("unchecked")
        Map<String, Object> mp = (Map<String, Object>) rows.get(0).get("metrics_payload");
        assertThat(mp.get("groupKey")).isEqualTo(LabPresetRunGroupKey.NO_INDEX.name());

        Mockito.verify(evaluationService, Mockito.never())
                .evaluateWithConfigurationForRagPresetQuestions(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any());
    }

    @Test
    void p0_skipped_whenCorpusGateFails_withoutCallingEvaluation() {
        when(experimentalSnapshotFactory.buildLlmSnapshot(ArgumentMatchers.any())).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(ArgumentMatchers.any())).thenReturn(embSnap());

        UUID projectId = UUID.randomUUID();
        UUID snapshotId = UUID.randomUUID();
        ProjectEntity project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(projectId);
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setProject(project);
        when(evaluationRunRepository.findById(ArgumentMatchers.any())).thenReturn(Optional.of(run));
        when(knowledgeSnapshotService.findActiveProjectSnapshot(projectId))
                .thenReturn(Optional.of(mockSnapshot("CHUNK_LEVEL", false, "hSnap", snapshotId)));

        when(corpusAvailabilityGate.evaluate(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.argThat(ids -> ids != null && ids.size() == 1 && snapshotId.equals(ids.get(0)))))
                .thenReturn(
                        new CorpusAvailabilityGate.Result(
                                false,
                                1,
                                List.of(),
                                0,
                                0L,
                                CorpusAvailabilityGate.NO_READY_DOCUMENTS,
                                "No READY documents"));
        when(corpusAvailabilityGate.probe(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.argThat(ids -> ids != null && ids.size() == 1 && snapshotId.equals(ids.get(0)))))
                .thenReturn(
                        Map.of(
                                "corpusRequired",
                                true,
                                "corpusAvailable",
                                false,
                                "skippedReasonCode",
                                CorpusAvailabilityGate.NO_READY_DOCUMENTS));

        RagPresetQuestion q = sampleQuestion();
        RagPresetDefinition p0 = preset(RagExperimentalPresetCode.P0);
        RagPresetBenchmarkRunPayload out =
                orchestrator()
                        .runPresetBenchmark(
                                UUID.randomUUID(),
                                new TypedBenchmarkDataset.RagPresetQuestions(List.of(q), List.of(p0)),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                null,
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = EvaluationTestFixtures.toRowMaps(out);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(BenchmarkResultRowKeys.ITEM_OUTCOME)).isEqualTo(BenchmarkItemOutcome.SKIPPED.name());
        assertThat(rows.get(0).get(BenchmarkResultRowKeys.ERROR_CODE)).isEqualTo(CorpusAvailabilityGate.NO_READY_DOCUMENTS);
        Mockito.verify(evaluationService, Mockito.never())
                .evaluateWithConfigurationForRagPresetQuestions(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any());

        @SuppressWarnings("unchecked")
        Map<String, Object> mp = (Map<String, Object>) rows.get(0).get("metrics_payload");
        assertThat(mp.get("skippedReasonCode")).isEqualTo(CorpusAvailabilityGate.NO_READY_DOCUMENTS);
        assertThat(mp.get("groupKey")).isEqualTo(LabPresetRunGroupKey.NO_INDEX.name());
        assertThat(mp.get("selectedSnapshotIds")).isEqualTo(List.of(snapshotId.toString()));
        assertThat(mp.get("presetCode")).isEqualTo("P0");
        assertThat(mp.get("productPresetId")).isNotNull();
        assertThat(mp.get("protocolStageIndex")).isEqualTo(0);
        assertThat(mp.get("presetStage")).isEqualTo("P0");
        assertThat(mp.get("presetLadderScope")).isEqualTo("SINGLE_TURN_LADDER");
        assertThat(mp.get("singleTurnBenchmarkSelectable")).isEqualTo(true);
        assertThat(mp.get("comparableSingleTurnMetric")).isEqualTo(true);
        assertThat(mp.get("benchmarkSupportStatus")).isEqualTo("SINGLE_TURN_SUPPORTED");
    }

    @Test
    void p0_executed_merges_telemetry_for_corpus_traceability() {
        when(experimentalSnapshotFactory.buildLlmSnapshot(ArgumentMatchers.any())).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(ArgumentMatchers.any())).thenReturn(embSnap());
        UUID snapshotId = UUID.randomUUID();
        when(knowledgeSnapshotService.findActiveProjectSnapshot(ArgumentMatchers.any()))
                .thenReturn(Optional.of(mockSnapshot("CHUNK_LEVEL", false, "hSnap", snapshotId)));

        List<RagPresetQuestion> questions = List.of(sampleQuestion());
        RagPresetDefinition p0 = preset(RagExperimentalPresetCode.P0);

        Map<String, Object> tel = new LinkedHashMap<>();
        tel.put("workflowName", "CorpusGroundedDirectWorkflow");
        tel.put("corpusChars", 2400);
        tel.put("corpusTruncated", false);
        tel.put("groundingPolicy", "EVIDENCE_ONLY");

        Map<String, Object> evalRow = new HashMap<>();
        evalRow.put(AbstractEvaluationService.EVALUATION_CHAT_TELEMETRY_ROW_KEY, tel);

        when(evaluationService.evaluateWithConfigurationForRagPresetQuestions(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.eq(questions),
                        ArgumentMatchers.any()))
                .thenReturn(EvaluationTestFixtures.ragBatchFromRowMaps(List.of(evalRow)));
        var run = new EvaluationRunEntity();
        ProjectEntity project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(UUID.randomUUID());
        run.setProject(project);
        when(evaluationRunRepository.findById(ArgumentMatchers.any())).thenReturn(Optional.of(run));

        RagPresetBenchmarkRunPayload out =
                orchestrator()
                        .runPresetBenchmark(
                                UUID.randomUUID(),
                                new TypedBenchmarkDataset.RagPresetQuestions(questions, List.of(p0)),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                null,
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = EvaluationTestFixtures.toRowMaps(out);
        @SuppressWarnings("unchecked")
        Map<String, Object> mp = (Map<String, Object>) rows.get(0).get("metrics_payload");
        assertThat(mp.get("workflowName")).isEqualTo("CorpusGroundedDirectWorkflow");
        assertThat(mp.get("materializationStrategy")).isEqualTo("CHUNK_LEVEL");
        assertThat(mp.get("corpusChars")).isEqualTo(2400);
        assertThat(mp.get("corpusAvailable")).isEqualTo(true);
        assertThat(mp.get("corpusTruncated")).isEqualTo(false);
        assertThat(mp.get("groundingPolicy")).isEqualTo("EVIDENCE_ONLY");
        assertThat(mp.get("selectedSnapshotIds")).isEqualTo(List.of(snapshotId.toString()));
    }

    @Test
    void p1_executed_exports_corpus_chars_and_truncation_flag() {
        when(experimentalSnapshotFactory.buildLlmSnapshot(ArgumentMatchers.any())).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(ArgumentMatchers.any())).thenReturn(embSnap());
        UUID snapshotId = UUID.randomUUID();
        when(knowledgeSnapshotService.findActiveProjectSnapshot(ArgumentMatchers.any()))
                .thenReturn(Optional.of(mockSnapshot("CHUNK_LEVEL", false, "hSnap", snapshotId)));

        List<RagPresetQuestion> questions = List.of(sampleQuestion());
        RagPresetDefinition p1 = preset(RagExperimentalPresetCode.P1);

        Map<String, Object> tel = new LinkedHashMap<>();
        tel.put("corpusChars", 50000);
        tel.put("corpusTruncated", true);

        Map<String, Object> evalRow = new HashMap<>();
        evalRow.put(AbstractEvaluationService.EVALUATION_CHAT_TELEMETRY_ROW_KEY, tel);

        when(evaluationService.evaluateWithConfigurationForRagPresetQuestions(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.eq(questions),
                        ArgumentMatchers.any()))
                .thenReturn(EvaluationTestFixtures.ragBatchFromRowMaps(List.of(evalRow)));
        var run = new EvaluationRunEntity();
        ProjectEntity project = Mockito.mock(ProjectEntity.class);
        when(project.getId()).thenReturn(UUID.randomUUID());
        run.setProject(project);
        when(evaluationRunRepository.findById(ArgumentMatchers.any())).thenReturn(Optional.of(run));

        RagPresetBenchmarkRunPayload out =
                orchestrator()
                        .runPresetBenchmark(
                                UUID.randomUUID(),
                                new TypedBenchmarkDataset.RagPresetQuestions(questions, List.of(p1)),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                null,
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = EvaluationTestFixtures.toRowMaps(out);
        @SuppressWarnings("unchecked")
        Map<String, Object> mp = (Map<String, Object>) rows.get(0).get("metrics_payload");
        assertThat(mp.get("corpusChars")).isEqualTo(50000);
        assertThat(mp.get("corpusTruncated")).isEqualTo(true);
    }

    @Test
    void p13_notSupported_exports_multiTurnExtensionClassification_notComparableMetric() {
        when(experimentalSnapshotFactory.buildLlmSnapshot(null)).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(null)).thenReturn(embSnap());

        RagPresetQuestion q = sampleQuestion();
        RagPresetDefinition p13 = preset(RagExperimentalPresetCode.P13);

        RagPresetBenchmarkRunPayload out =
                orchestrator()
                        .runPresetBenchmark(
                                null,
                                new TypedBenchmarkDataset.RagPresetQuestions(List.of(q), List.of(p13)),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                null,
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = EvaluationTestFixtures.toRowMaps(out);
        @SuppressWarnings("unchecked")
        Map<String, Object> mp = (Map<String, Object>) rows.get(0).get("metrics_payload");
        assertThat(rows.get(0).get(BenchmarkResultRowKeys.ITEM_OUTCOME))
                .isEqualTo(BenchmarkItemOutcome.NOT_SUPPORTED.name());
        assertThat(mp.get("protocolStageIndex")).isEqualTo(13);
        assertThat(mp.get("presetStage")).isEqualTo("P13");
        assertThat(mp.get("presetLadderScope")).isEqualTo("CONVERSATIONAL_EXTENSION");
        assertThat(mp.get("requiresMultiTurn")).isEqualTo(true);
        assertThat(mp.get("singleTurnBenchmarkSelectable")).isEqualTo(false);
        assertThat(mp.get("comparableSingleTurnMetric")).isEqualTo(false);
        assertThat(mp.get("benchmarkSupportStatus")).isEqualTo("MULTI_TURN_EXTENSION_NOT_COMPARABLE");
        assertThat(mp.get("skippedReasonCode")).isEqualTo("PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED");
    }

    @Test
    void catalog_three_presets_two_questions_yieldsSixItems_andPresetCodesAreAssigned() {
        when(experimentalSnapshotFactory.buildLlmSnapshot(null)).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(null)).thenReturn(embSnap());

        List<RagPresetQuestion> questions = List.of(
                question("RAG-001"),
                question("RAG-002"));
        List<RagPresetDefinition> catalog = List.of(preset(RagExperimentalPresetCode.P0), preset(RagExperimentalPresetCode.P1), preset(RagExperimentalPresetCode.P2));

        Mockito.lenient()
                .when(evaluationService.evaluateWithConfigurationForRagPresetQuestions(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.eq(questions),
                        ArgumentMatchers.any()))
                .thenAnswer(inv -> Map.of("results", baseRowsFor(questions.size())));
        RagPresetBenchmarkRunPayload out =
                orchestrator()
                        .runPresetBenchmark(
                                null,
                                new TypedBenchmarkDataset.RagPresetQuestions(questions, catalog),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                Set.of(),
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = EvaluationTestFixtures.toRowMaps(out);
        assertThat(rows).hasSize(6);
        assertThat(rows.stream().map(r -> String.valueOf(r.get(BenchmarkResultRowKeys.PRESET_CODE))).toList())
                .containsExactlyInAnyOrder("P0", "P0", "P1", "P1", "P2", "P2");
    }

    @Test
    void requestedPresets_filter_appliesBeforeExecution() {
        when(experimentalSnapshotFactory.buildLlmSnapshot(null)).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(null)).thenReturn(embSnap());

        List<RagPresetQuestion> questions = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            questions.add(question(String.format("RAG-%03d", i)));
        }
        List<RagPresetDefinition> catalog = new ArrayList<>();
        for (RagExperimentalPresetCode p : RagExperimentalPresetCode.values()) {
            catalog.add(preset(p));
        }

        Mockito.lenient()
                .when(evaluationService.evaluateWithConfigurationForRagPresetQuestions(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.eq(questions),
                        ArgumentMatchers.any()))
                .thenAnswer(inv -> Map.of("results", baseRowsFor(questions.size())));
        RagPresetBenchmarkRunPayload out =
                orchestrator()
                        .runPresetBenchmark(
                                null,
                                new TypedBenchmarkDataset.RagPresetQuestions(questions, catalog),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                Set.of(RagExperimentalPresetCode.P0, RagExperimentalPresetCode.P3),
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = EvaluationTestFixtures.toRowMaps(out);
        // Two presets x 60 questions.
        assertThat(rows).hasSize(120);
        assertThat(rows.stream().map(r -> String.valueOf(r.get(BenchmarkResultRowKeys.PRESET_CODE))).distinct().toList())
                .containsExactlyInAnyOrder("P0", "P3");
    }

    private static RagPresetQuestion sampleQuestion() {
        return new RagPresetQuestion(
                "rq1",
                "Q?",
                "A",
                Optional.empty(),
                Optional.empty(),
                "",
                List.of(),
                List.of(),
                "",
                false,
                false,
                false,
                false,
                false,
                "");
    }

    private static RagPresetQuestion question(String id) {
        return new RagPresetQuestion(
                id,
                "Q?",
                "A",
                Optional.of(QueryType.COUNT_DOCUMENTS),
                Optional.of(DifficultyLevel.LOW),
                "",
                List.of(),
                List.of(),
                "",
                false,
                false,
                false,
                false,
                false,
                "");
    }

    private static RagPresetDefinition preset(RagExperimentalPresetCode code) {
        return new RagPresetDefinition(code, "", code.name(), "", "", "", "", "", "", "", "");
    }

    private static List<Map<String, Object>> baseRowsFor(int n) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            out.add(new HashMap<>());
        }
        return out;
    }

    private static KnowledgeIndexSnapshotEntity mockSnapshot(
            String strategy,
            boolean supportsMetadata,
            String profileHash,
            UUID snapshotId) {
        KnowledgeIndexSnapshotEntity snap = new KnowledgeIndexSnapshotEntity();
        try {
            var f = KnowledgeIndexSnapshotEntity.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(snap, snapshotId);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to set snapshot id for test", e);
        }
        snap.setIndexProfileHash(profileHash);
        snap.setIndexProfileJsonb(Map.of(
                "materializationStrategy", strategy,
                "supportsMetadata", supportsMetadata,
                "embeddingModelId", "emb",
                "chunkMaxChars", 400,
                "chunkOverlap", 40));
        return snap;
    }

    private static ProjectIndexProfile sampleProfile(UUID projectId) {
        return new ProjectIndexProfile(
                projectId,
                MaterializationStrategy.HYBRID,
                true,
                "meta-v1",
                "emb",
                400,
                40,
                ProjectIndexProfile.computeProfileHash(MaterializationStrategy.HYBRID, true, "meta-v1", "emb", 400, 40),
                Instant.now(),
                Instant.now());
    }
}
