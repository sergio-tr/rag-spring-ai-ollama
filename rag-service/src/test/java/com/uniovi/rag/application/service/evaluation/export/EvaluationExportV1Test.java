package com.uniovi.rag.application.service.evaluation.export;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpMetricsCalculator;
import com.uniovi.rag.application.service.evaluation.provenance.EvaluationBuildMetadata;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationCorpusEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationDatasetEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.KnowledgeIndexSnapshotEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.ResolvedConfigSnapshotEntity;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

class EvaluationExportV1Test {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void evaluationJsonV1ContainsRunModelProviderAndQuestionResults() {
        EvaluationRunEntity run = baseOpenAiRun();
        EvaluationResultEntity item = executedItem(run);

        Map<String, Object> json = EvaluationExportV1Builder.buildResultsJson(run, List.of(item));

        assertThat(json)
                .containsEntry("exportSchemaVersion", EvaluationExportV1Schema.VERSION)
                .containsKeys("run", "provider", "model", "results", "metrics", "manifest", "provenance");
        @SuppressWarnings("unchecked")
        Map<String, Object> model = (Map<String, Object>) json.get("model");
        assertThat(model).containsEntry("llmModelId", "gemma3:4b");
        @SuppressWarnings("unchecked")
        Map<String, Object> provider = (Map<String, Object>) json.get("provider");
        assertThat(provider)
                .containsEntry("chatProvider", "OPENAI_COMPATIBLE")
                .containsEntry("chatModel", "gemma3:4b")
                .containsEntry("embeddingModel", "mxbai-embed-large");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) json.get("results");
        assertThat(results).hasSize(1);
        assertThat(results.getFirst()).containsKeys("question", "answer", "metrics", "sources", "traceIds");
    }

    @Test
    void exportContainsBindingsDatasetConfigIndexAndBuildMetadata() {
        EvaluationRunEntity run = baseOpenAiRun();
        UUID datasetId = UUID.randomUUID();
        UUID corpusId = UUID.randomUUID();
        UUID configId = UUID.randomUUID();
        UUID indexId = UUID.randomUUID();

        EvaluationDatasetEntity dataset = Mockito.mock(EvaluationDatasetEntity.class);
        when(dataset.getId()).thenReturn(datasetId);
        run.setDataset(dataset);
        run.setDatasetSha256("sha-dataset-abc");

        EvaluationCorpusEntity corpus = Mockito.mock(EvaluationCorpusEntity.class);
        when(corpus.getId()).thenReturn(corpusId);
        run.setEvaluationCorpus(corpus);

        ResolvedConfigSnapshotEntity configSnapshot = Mockito.mock(ResolvedConfigSnapshotEntity.class);
        when(configSnapshot.getId()).thenReturn(configId);
        when(configSnapshot.getConfigHash()).thenReturn("cfg-hash-123");
        run.setResolvedConfigSnapshot(configSnapshot);

        KnowledgeIndexSnapshotEntity indexSnapshot = Mockito.mock(KnowledgeIndexSnapshotEntity.class);
        when(indexSnapshot.getId()).thenReturn(indexId);
        when(indexSnapshot.getSignatureHash()).thenReturn("idx-signature-456");
        when(indexSnapshot.getIndexProfileHash()).thenReturn("idx-profile-789");
        run.setIndexSnapshot(indexSnapshot);
        run.setClassifierModelId("classifier-v1");
        Map<String, Object> aggregates = new LinkedHashMap<>(run.getAggregatesJson());
        aggregates.put("requestedPresetCodes", List.of("P0", "P1"));
        run.setAggregatesJson(aggregates);

        Map<String, Object> json = EvaluationExportV1Builder.buildResultsJson(run, List.of(executedItem(run)));

        @SuppressWarnings("unchecked")
        Map<String, Object> runSection = (Map<String, Object>) json.get("run");
        assertThat(runSection)
                .containsEntry("datasetId", datasetId)
                .containsEntry("datasetSha256", "sha-dataset-abc")
                .containsEntry("corpusId", corpusId)
                .containsEntry("resolvedConfigSnapshotId", configId)
                .containsEntry("resolvedConfigHash", "cfg-hash-123")
                .containsEntry("indexSnapshotId", indexId)
                .containsEntry("indexSignatureHash", "idx-signature-456")
                .containsEntry("presetCodes", List.of("P0", "P1"));

        @SuppressWarnings("unchecked")
        Map<String, Object> manifest = (Map<String, Object>) json.get("manifest");
        @SuppressWarnings("unchecked")
        Map<String, Object> bindings = (Map<String, Object>) manifest.get("bindings");
        assertThat(bindings)
                .containsEntry("datasetId", datasetId)
                .containsEntry("resolvedConfigHash", "cfg-hash-123")
                .containsEntry("indexSnapshotId", indexId);

        @SuppressWarnings("unchecked")
        Map<String, Object> provenance = (Map<String, Object>) json.get("provenance");
        assertThat(provenance)
                .containsEntry("gitSha", "abc123")
                .containsEntry("buildId", "ci-42")
                .containsEntry("environmentLabel", "test-env")
                .containsKey("promptBundleSha256")
                .containsKey("promptBundleVersion")
                .containsKey("promptBundleIncludedGroups");
        assertThat(provenance.toString()).doesNotContain("Act as an expert evaluator");
    }

    @Test
    void exportUsesUnknownFallbackWhenBuildMetadataMissing() {
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(UUID.randomUUID());
        run.setBenchmarkKind(BenchmarkKind.LLM_JUDGE_QA.name());
        run.setLlmModelId("m1");

        EvaluationResultEntity item = executedItem(run);
        Map<String, Object> json = EvaluationExportV1Builder.buildResultsJson(run, List.of(item));

        @SuppressWarnings("unchecked")
        Map<String, Object> provenance = (Map<String, Object>) json.get("provenance");
        assertThat(provenance)
                .containsEntry("gitSha", EvaluationBuildMetadata.UNKNOWN)
                .containsEntry("buildId", EvaluationBuildMetadata.UNKNOWN)
                .containsEntry("environmentLabel", EvaluationBuildMetadata.UNKNOWN);
    }

    @Test
    void fullBundleZipContainsManifestSummaryAndResultsWithoutChangingScores() throws Exception {
        EvaluationRunEntity run = baseOpenAiRun();
        EvaluationResultEntity item = executedItem(run);
        Map<String, Object> mvpBefore = BenchmarkMvpMetricsCalculator.computeMvpMetrics(item, run);

        byte[] zip =
                EvaluationExportV1Builder.buildFullBundleZip(
                        run, List.of(item), null, null, null, objectMapper);

        Map<String, byte[]> entries = readZipEntries(zip);
        assertThat(entries).containsKeys(
                EvaluationExportV1Schema.RESULTS_JSON,
                EvaluationExportV1Schema.SUMMARY_CSV,
                EvaluationExportV1Schema.EVALUATION_MANIFEST_JSON);

        @SuppressWarnings("unchecked")
        Map<String, Object> results =
                objectMapper.readValue(entries.get(EvaluationExportV1Schema.RESULTS_JSON), Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> manifestFile =
                objectMapper.readValue(entries.get(EvaluationExportV1Schema.EVALUATION_MANIFEST_JSON), Map.class);
        assertThat(manifestFile).isEqualTo(results.get("manifest"));

        String csv = new String(entries.get(EvaluationExportV1Schema.SUMMARY_CSV), StandardCharsets.UTF_8);
        assertThat(csv.lines().findFirst().orElse("")).contains("itemId");

        @SuppressWarnings("unchecked")
        Map<String, Object> exported =
                ((List<Map<String, Object>>) results.get("results")).getFirst();
        assertThat(exported.get("metrics")).isEqualTo(mvpBefore);
    }

    @Test
    void evaluationSummaryCsvHasAtMostTwentyColumns() {
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(UUID.randomUUID());
        run.setLlmModelId("gemma3:4b");

        EvaluationResultEntity item = new EvaluationResultEntity();
        item.setId(UUID.randomUUID());
        item.setRun(run);
        item.setBenchmarkKind(BenchmarkKind.LLM_JUDGE_QA.name());
        item.setQuestionText("Q1");
        item.setMetricsPayload(Map.of(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name()));

        String csv = EvaluationExportV1Builder.buildSummaryCsv(run, List.of(item));
        String header = csv.lines().findFirst().orElse("");
        assertThat(header.split(",", -1)).hasSize(EvaluationExportV1Schema.SUMMARY_CSV_COLUMNS.size());
        assertThat(EvaluationExportV1Schema.SUMMARY_CSV_COLUMNS).hasSizeLessThanOrEqualTo(20);
    }

    @Test
    void exportDoesNotChangeScoringValues() {
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(UUID.randomUUID());
        run.setLlmModelId("gemma3:4b");

        EvaluationResultEntity item = new EvaluationResultEntity();
        item.setId(UUID.randomUUID());
        item.setRun(run);
        item.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        item.setQuestionText("¿Cuántos asistentes?");
        item.setExpectedAnswer("12");
        item.setActualAnswer("12 personas");
        item.setCorrectness(1);
        item.setMetricsPayload(
                Map.of(
                        BenchmarkResultRowKeys.ITEM_OUTCOME,
                        BenchmarkItemOutcome.EXECUTED.name(),
                        "finalScore",
                        0.72,
                        "scoreUnavailableReason",
                        ""));

        Map<String, Object> mvpBefore = BenchmarkMvpMetricsCalculator.computeMvpMetrics(item, run);
        @SuppressWarnings("unchecked")
        Map<String, Object> exported =
                ((List<Map<String, Object>>) EvaluationExportV1Builder.buildResultsJson(run, List.of(item)).get("results"))
                        .getFirst();
        assertThat(exported.get("metrics")).isEqualTo(mvpBefore);
    }

    @Test
    void exportIncludesProvenanceFromRunAggregates() {
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(UUID.randomUUID());
        run.setLlmModelId("gemma3:4b");
        run.setAggregatesJson(
                Map.of(
                        "evaluationProvenance",
                        Map.of(
                                "chatProvider", "OPENAI_COMPATIBLE",
                                "embeddingProvider", "OPENAI_COMPATIBLE",
                                "gitSha", "abc123",
                                "buildId", "ci-42",
                                "environmentLabel", "staging",
                                "promptProfileVersion", "baseline-lab-v1",
                                "effectiveSystemPromptSha256", "deadbeef")));
        run.setPromptProfileSnapshot(
                Map.of(
                        "profileVersion", "baseline-lab-v1",
                        "effectiveSystemPromptSha256", "deadbeef"));

        EvaluationResultEntity item = executedItem(run);
        Map<String, Object> json = EvaluationExportV1Builder.buildResultsJson(run, List.of(item));

        @SuppressWarnings("unchecked")
        Map<String, Object> provenance = (Map<String, Object>) json.get("provenance");
        assertThat(provenance)
                .containsEntry("gitSha", "abc123")
                .containsEntry("buildId", "ci-42")
                .containsEntry("environmentLabel", "staging")
                .containsEntry("promptProfileVersion", "baseline-lab-v1");
        @SuppressWarnings("unchecked")
        Map<String, Object> provider = (Map<String, Object>) json.get("provider");
        assertThat(provider).containsEntry("chatProvider", "OPENAI_COMPATIBLE");
    }

    @Test
    void includesJudgeFailureReason() {
        EvaluationRunEntity run = baseOpenAiRun();
        EvaluationResultEntity item = executedItem(run);
        Map<String, Object> metrics = new LinkedHashMap<>(item.getMetricsPayload());
        metrics.put(BenchmarkResultRowKeys.JUDGE_STATUS, "FAILED");
        metrics.put(
                BenchmarkResultRowKeys.JUDGE_FAILURE_REASON,
                "EVALUATION_JUDGE_EMPTY_RESPONSE");
        item.setMetricsPayload(metrics);

        Map<String, Object> json = EvaluationExportV1Builder.buildResultsJson(run, List.of(item));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> results = (List<Map<String, Object>>) json.get("results");
        @SuppressWarnings("unchecked")
        Map<String, Object> generation = (Map<String, Object>) results.getFirst().get("metrics");
        @SuppressWarnings("unchecked")
        Map<String, Object> gen = (Map<String, Object>) generation.get("generation");
        assertThat(gen.get("judgeFailureReason")).isEqualTo("EVALUATION_JUDGE_EMPTY_RESPONSE");
        assertThat(gen.get("judgeStatus")).isEqualTo("FAILED");
    }

    @Test
    void exportIncludesDerivedErrorClassWhenAvailable() {
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(UUID.randomUUID());

        EvaluationResultEntity item = new EvaluationResultEntity();
        item.setId(UUID.randomUUID());
        item.setRun(run);
        item.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        item.setQuestionText("Q?");
        item.setMetricsPayload(
                Map.of(
                        BenchmarkResultRowKeys.ITEM_OUTCOME,
                        BenchmarkItemOutcome.SKIPPED.name(),
                        BenchmarkResultRowKeys.ERROR_CODE,
                        "REINDEX_REQUIRED"));

        @SuppressWarnings("unchecked")
        Map<String, Object> exported =
                ((List<Map<String, Object>>) EvaluationExportV1Builder.buildResultsJson(run, List.of(item)).get("results"))
                        .getFirst();

        assertThat(exported.get("derivedErrorClass")).isEqualTo(EvaluationDerivedErrorClassifier.INDEX_PREPARATION);
    }

    private static EvaluationRunEntity baseOpenAiRun() {
        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setId(UUID.randomUUID());
        run.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        run.setLlmModelId("gemma3:4b");
        run.setEmbeddingModelId("mxbai-embed-large");
        run.setAggregatesJson(
                Map.of(
                        "evaluationProvenance",
                        Map.of(
                                "chatProvider", "OPENAI_COMPATIBLE",
                                "embeddingProvider", "OPENAI_COMPATIBLE",
                                "gitSha", "abc123",
                                "buildId", "ci-42",
                                "environmentLabel", "test-env")));
        return run;
    }

    private static EvaluationResultEntity executedItem(EvaluationRunEntity run) {
        EvaluationResultEntity item = new EvaluationResultEntity();
        item.setId(UUID.randomUUID());
        item.setRun(run);
        item.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        item.setQuestionText("¿Quién fue el presidente?");
        item.setExpectedAnswer("Ana López");
        item.setActualAnswer("Ana López fue la presidenta.");
        item.setQueryType("GET_FIELD");
        item.setCorrectness(1);
        item.setMetricsPayload(
                Map.of(
                        BenchmarkResultRowKeys.ITEM_OUTCOME,
                        BenchmarkItemOutcome.EXECUTED.name(),
                        "chatProvider",
                        "OPENAI_COMPATIBLE",
                        BenchmarkResultRowKeys.DATASET_QUESTION_ID,
                        "RAG-001",
                        "traceId",
                        "trace-abc-123"));
        return item;
    }

    private static Map<String, byte[]> readZipEntries(byte[] zipBytes) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                entries.put(entry.getName(), zis.readAllBytes());
            }
        }
        return entries;
    }
}
