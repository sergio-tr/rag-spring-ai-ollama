package com.uniovi.rag.service.evaluation.preset;

import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.application.service.evaluation.TypedBenchmarkDataset;
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
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ProjectEntity;
import com.uniovi.rag.service.evaluation.EvaluationService;
import com.uniovi.rag.service.evaluation.baseline.ExperimentalSnapshotFactory;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TypedRagPresetBenchmarkOrchestratorTest {

    @Mock private EvaluationService evaluationService;
    @Mock private EvaluationRunRepository evaluationRunRepository;
    @Mock private ExperimentalSnapshotFactory experimentalSnapshotFactory;
    @Mock private KnowledgeSnapshotService knowledgeSnapshotService;

    private static LlmExperimentalSnapshot llmSnap() {
        return new LlmExperimentalSnapshot(
                "lm", 0.2, 0.9, 5, null, 1.05, 8192, 512, null, 42, List.of(), null, false, List.of());
    }

    private static EmbeddingExperimentalSnapshot embSnap() {
        return new EmbeddingExperimentalSnapshot("emb", null, null, null, null, null, null, List.of());
    }

    private TypedRagPresetBenchmarkOrchestrator orchestrator() {
        return new TypedRagPresetBenchmarkOrchestrator(
                evaluationService,
                evaluationRunRepository,
                experimentalSnapshotFactory,
                knowledgeSnapshotService,
                new LabPresetRunPlanService(knowledgeSnapshotService));
    }

    @Test
    void empty_catalog_single_evaluate_call_no_preset_codes_on_arbitrary_rows() {
        UUID runId = UUID.randomUUID();
        when(evaluationRunRepository.findById(runId)).thenReturn(Optional.empty());
        when(experimentalSnapshotFactory.buildLlmSnapshot(null)).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(null)).thenReturn(embSnap());

        RagPresetQuestion q = sampleQuestion();
        Map<String, Object> eval = new HashMap<>();
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> row = new HashMap<>();
        row.put("question", q.question());
        rows.add(row);
        eval.put("results", rows);
        eval.put("evaluation_summary", Map.of());
        when(evaluationService.evaluateWithConfigurationForRagPresetQuestions(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.eq(List.of(q)),
                        ArgumentMatchers.any()))
                .thenReturn(eval);

        Map<String, Object> out =
                orchestrator()
                        .runPresetBenchmark(
                                runId,
                                new TypedBenchmarkDataset.RagPresetQuestions(List.of(q), List.of()),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                null,
                                null);

        assertThat(out.get("results")).isSameAs(rows);
        assertThat(row.get(BenchmarkResultRowKeys.PRESET_CODE)).isNull();
        assertThat(row.get(BenchmarkResultRowKeys.LLM_MODEL_ID)).isEqualTo("lm");
        assertThat(row.get(BenchmarkResultRowKeys.EMBEDDING_MODEL_ID)).isEqualTo("emb");
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

        Map<String, Object> out =
                orchestrator()
                        .runPresetBenchmark(
                                null,
                                new TypedBenchmarkDataset.RagPresetQuestions(List.of(q), List.of(p13)),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                null,
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("results");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(BenchmarkResultRowKeys.ITEM_OUTCOME))
                .isEqualTo(BenchmarkItemOutcome.NOT_SUPPORTED.name());
        assertThat(rows.get(0).get(BenchmarkResultRowKeys.ERROR_CODE))
                .isEqualTo("PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED");
        @SuppressWarnings("unchecked")
        Map<String, Object> mp = (Map<String, Object>) rows.get(0).get("metrics_payload");
        assertThat(mp.get("groupKey")).isEqualTo(LabPresetRunGroupKey.MULTI_TURN_UNSUPPORTED_IN_SINGLE_TURN.name());
        @SuppressWarnings("unchecked")
        Map<String, Object> es = (Map<String, Object>) out.get("evaluation_summary");
        assertThat(es).containsKey("runPlan");
        Mockito.verify(evaluationService, Mockito.never())
                .evaluateWithConfigurationForRagPresetQuestions(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any());
        Mockito.verify(evaluationService).summarizeJudgeResults(ArgumentMatchers.anyList());
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

        Map<String, Object> out =
                orchestrator()
                        .runPresetBenchmark(
                                UUID.randomUUID(),
                                new TypedBenchmarkDataset.RagPresetQuestions(List.of(q), List.of(p8)),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                null,
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("results");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(BenchmarkResultRowKeys.ITEM_OUTCOME)).isEqualTo(BenchmarkItemOutcome.SKIPPED.name());
        assertThat(String.valueOf(rows.get(0).get(BenchmarkResultRowKeys.ERROR_CODE)))
                .isEqualTo("MATERIALIZATION_NOT_SUPPORTED");
        assertThat(rows.get(0)).containsKey("metrics_payload");
        @SuppressWarnings("unchecked")
        Map<String, Object> mp = (Map<String, Object>) rows.get(0).get("metrics_payload");
        assertThat(mp.get("groupKey")).isEqualTo(LabPresetRunGroupKey.HYBRID_METADATA.name());
        @SuppressWarnings("unchecked")
        Map<String, Object> es = (Map<String, Object>) out.get("evaluation_summary");
        assertThat(es).containsKey("runPlan");
        Mockito.verify(evaluationService, Mockito.never())
                .evaluateWithConfigurationForRagPresetQuestions(
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

        Map<String, Object> out =
                orchestrator()
                        .runPresetBenchmark(
                                UUID.randomUUID(),
                                new TypedBenchmarkDataset.RagPresetQuestions(List.of(q), List.of(p4)),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                null,
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("results");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get(BenchmarkResultRowKeys.ITEM_OUTCOME)).isEqualTo(BenchmarkItemOutcome.SKIPPED.name());
        assertThat(String.valueOf(rows.get(0).get(BenchmarkResultRowKeys.ERROR_CODE)))
                .isEqualTo("METADATA_SUPPORT_REQUIRED");
        Mockito.verify(evaluationService, Mockito.never())
                .evaluateWithConfigurationForRagPresetQuestions(
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any(),
                        ArgumentMatchers.any());
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
                .thenReturn(Map.of("results", baseRowsFor(questions.size()), "evaluation_summary", Map.of()));
        when(evaluationService.summarizeJudgeResults(ArgumentMatchers.anyList())).thenReturn(Map.of());

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
    void p0_without_snapshot_executes() {
        when(experimentalSnapshotFactory.buildLlmSnapshot(null)).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(null)).thenReturn(embSnap());

        List<RagPresetQuestion> questions = List.of(sampleQuestion());
        RagPresetDefinition p0 = preset(RagExperimentalPresetCode.P0);
        when(evaluationService.evaluateWithConfigurationForRagPresetQuestions(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq(questions),
                ArgumentMatchers.any()))
                .thenReturn(Map.of("results", baseRowsFor(questions.size()), "evaluation_summary", Map.of()));
        when(evaluationService.summarizeJudgeResults(ArgumentMatchers.anyList())).thenReturn(Map.of());

        orchestrator()
                .runPresetBenchmark(
                        null,
                        new TypedBenchmarkDataset.RagPresetQuestions(questions, List.of(p0)),
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
    void catalog_three_presets_two_questions_yieldsSixItems_andPresetCodesAreAssigned() {
        when(experimentalSnapshotFactory.buildLlmSnapshot(null)).thenReturn(llmSnap());
        when(experimentalSnapshotFactory.buildEmbeddingSnapshot(null)).thenReturn(embSnap());

        List<RagPresetQuestion> questions = List.of(
                question("RAG-001"),
                question("RAG-002"));
        List<RagPresetDefinition> catalog = List.of(preset(RagExperimentalPresetCode.P0), preset(RagExperimentalPresetCode.P1), preset(RagExperimentalPresetCode.P2));

        when(evaluationService.evaluateWithConfigurationForRagPresetQuestions(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq(questions),
                ArgumentMatchers.any()))
                .thenAnswer(inv -> Map.of("results", baseRowsFor(questions.size()), "evaluation_summary", Map.of()));
        when(evaluationService.summarizeJudgeResults(ArgumentMatchers.anyList())).thenReturn(Map.of());

        Map<String, Object> out =
                orchestrator()
                        .runPresetBenchmark(
                                null,
                                new TypedBenchmarkDataset.RagPresetQuestions(questions, catalog),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                Set.of(),
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("results");
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

        when(evaluationService.evaluateWithConfigurationForRagPresetQuestions(
                ArgumentMatchers.any(),
                ArgumentMatchers.any(),
                ArgumentMatchers.eq(questions),
                ArgumentMatchers.any()))
                .thenAnswer(inv -> Map.of("results", baseRowsFor(questions.size()), "evaluation_summary", Map.of()));
        when(evaluationService.summarizeJudgeResults(ArgumentMatchers.anyList())).thenReturn(Map.of());

        Map<String, Object> out =
                orchestrator()
                        .runPresetBenchmark(
                                null,
                                new TypedBenchmarkDataset.RagPresetQuestions(questions, catalog),
                                new RagFeatureConfiguration(),
                                new RagImplementationProperties(),
                                Set.of(RagExperimentalPresetCode.P0, RagExperimentalPresetCode.P3),
                                null);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) out.get("results");
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
}
