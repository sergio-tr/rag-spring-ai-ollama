package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkMvpMetricsCalculatorTest {

    @Test
    void normalizedExactMatch_ignoreCaseAndWhitespace() {
        assertThat(BenchmarkMvpMetricsCalculator.normalizedExactMatch("  Hello\nWorld ", "hello world"))
                .isTrue();
    }

    @Test
    void containsExpectedAnswer_substringNormalized() {
        assertThat(BenchmarkMvpMetricsCalculator.containsExpectedAnswer("Paris", "The answer is paris today."))
                .isTrue();
    }

    @Test
    void embeddingRow_handChecked_retrievalAndOperational() {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setId(UUID.randomUUID());
        e.setBenchmarkKind(BenchmarkKind.EMBEDDING_RETRIEVAL.name());
        e.setQueryType("FACTOID");
        e.setExpectedAnswer("Acta 12");
        e.setActualAnswer("top chunk id");
        e.setLatencyMs(99L);
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
        mp.put("recall_at_1", 0.0);
        mp.put("recall_at_3", 1.0);
        mp.put("recall_at_5", 1.0);
        mp.put("mrr", 0.5);
        mp.put("retrieved_count", 5);
        mp.put("gold_found", true);
        mp.put(BenchmarkResultRowKeys.DIFFICULTY, "EASY");
        mp.put(BenchmarkResultRowKeys.DATASET_QUESTION_ID, "q1");
        mp.put("embedding_dimensions", 1024);
        mp.put("embedding_compatibility_status", "COMPATIBLE");
        e.setMetricsPayload(mp);

        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setLlmModelId("run-llm");
        run.setEmbeddingModelId("run-emb");

        Map<String, Object> mvp = BenchmarkMvpMetricsCalculator.computeMvpMetrics(e, run);
        @SuppressWarnings("unchecked")
        Map<String, Object> ret = (Map<String, Object>) mvp.get("retrieval");
        assertThat(ret.get("applicable")).isEqualTo(true);
        assertThat(ret.get("recallAt3")).isEqualTo(1.0);
        assertThat(ret.get("recallAt5")).isEqualTo(1.0);
        assertThat(ret.get("mrr")).isEqualTo(0.5);
        assertThat(ret.get("retrievedCount")).isEqualTo(5);
        assertThat(ret.get("goldFound")).isEqualTo(true);

        @SuppressWarnings("unchecked")
        Map<String, Object> gen = (Map<String, Object>) mvp.get("generation");
        assertThat(gen.get("answerLength")).isEqualTo("top chunk id".length());
        assertThat(gen.get("semanticScore")).isEqualTo(BenchmarkMvpSchema.NOT_AVAILABLE);

        @SuppressWarnings("unchecked")
        Map<String, Object> op = (Map<String, Object>) mvp.get("operational");
        assertThat(op.get("latencyMs")).isEqualTo(99L);
        assertThat(op.get("embeddingModelId")).isEqualTo("run-emb");
        assertThat(op.get("embeddingDimensions")).isEqualTo(1024);
        assertThat(op.get("embeddingCompatibilityStatus")).isEqualTo("COMPATIBLE");
        assertThat(op.get("failureCode")).isEqualTo("");
        assertThat(op.get("unsupportedReason")).isEqualTo("");

        Map<String, String> csv = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(e, run);
        assertThat(csv)
                .containsEntry("embeddingModelId", "run-emb")
                .containsEntry("embeddingDimensions", "1024")
                .containsEntry("embeddingCompatibilityStatus", "COMPATIBLE");
    }

    @Test
    void ragRow_retrievalMarkedNotAvailable_semanticFromJudge() {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        e.setExpectedAnswer("yes");
        e.setActualAnswer("Yes.");
        e.setLatencyMs(10L);
        Map<String, Object> judge = Map.of("correctness", 5);
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
        mp.put("judge_scores", judge);
        mp.put(BenchmarkResultRowKeys.PRESET_CODE, "P2");
        mp.put(BenchmarkResultRowKeys.LLM_MODEL_ID, "m1");
        mp.put(BenchmarkResultRowKeys.EMBEDDING_MODEL_ID, "e1");
        e.setMetricsPayload(mp);

        Map<String, Object> mvp = BenchmarkMvpMetricsCalculator.computeMvpMetrics(e, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> ret = (Map<String, Object>) mvp.get("retrieval");
        assertThat(ret.get("recallAt1")).isEqualTo(BenchmarkMvpSchema.NOT_AVAILABLE);

        @SuppressWarnings("unchecked")
        Map<String, Object> gen = (Map<String, Object>) mvp.get("generation");
        assertThat(gen.get("normalizedExactMatch")).isEqualTo(false);
        assertThat(gen.get("semanticScore")).isEqualTo(1.0);
        assertThat(gen.get("correctness")).isEqualTo(1.0);
        assertThat(gen.get("llmJudgeScore")).isEqualTo(1.0);
    }

    @Test
    void ragRow_exposesPrimaryAndSecondaryMetricsForExports() {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        e.setExpectedAnswer("president is Ada");
        e.setActualAnswer("The evidence does not include the requested date.");
        e.setLatencyMs(42L);
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
        mp.put("judge_scores", Map.of(
                "correctness", 2,
                "context_sufficiency", 4,
                "relevance", 5,
                "independence", 5,
                "groundedness", 3));
        mp.put("requestedDate", "2026-02-25");
        mp.put("dateMismatchDetected", true);
        mp.put("abstentionTriggered", true);
        e.setMetricsPayload(mp);

        Map<String, Object> mvp = BenchmarkMvpMetricsCalculator.computeMvpMetrics(e, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> gen = (Map<String, Object>) mvp.get("generation");

        assertThat(gen.get("correctness")).isEqualTo(0.4);
        assertThat(gen.get("llmJudgeScore")).isEqualTo(0.76);
        assertThat(gen.get("hallucinationRate")).isEqualTo(0.4);
        assertThat(gen.get("faithfulness")).isEqualTo(0.6);
        assertThat(gen.get("sourceSupport")).isEqualTo(0.8);
        assertThat(gen.get("dateCorrectness")).isEqualTo(1.0);

        Map<String, String> csv = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(e, null);
        assertThat(csv)
                .containsEntry("correctness", "0.4")
                .containsEntry("llmJudgeScore", "0.76")
                .containsEntry("hallucinationRate", "0.4")
                .containsEntry("faithfulness", "0.6")
                .containsEntry("sourceSupport", "0.8")
                .containsEntry("dateCorrectness", "1.0");
    }

    @Test
    void notSupportedRow_setsUnsupportedReasonFromErrorCode() {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        e.setExpectedAnswer("x");
        e.setActualAnswer("");
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.NOT_SUPPORTED.name());
        mp.put(BenchmarkResultRowKeys.ERROR_CODE, "PRESET_CLARIFICATION_BENCHMARK_NOT_SUPPORTED");
        e.setMetricsPayload(mp);

        Map<String, Object> mvp = BenchmarkMvpMetricsCalculator.computeMvpMetrics(e, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> op = (Map<String, Object>) mvp.get("operational");
        assertThat(op.get("unsupportedReason"))
                .isEqualTo("Clarification preset (P13) is not supported in the Lab single-turn harness.");
    }

    @Test
    void embeddingRow_incompatible_exportsCompatibilityFieldsWithoutRetrievalMetrics() {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.EMBEDDING_RETRIEVAL.name());
        e.setQueryType("FACTOID");
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.NOT_SUPPORTED.name());
        mp.put(BenchmarkResultRowKeys.ERROR_CODE, "EMBEDDING_DIMENSION_MISMATCH");
        mp.put(BenchmarkResultRowKeys.EMBEDDING_MODEL_ID, "nomic-embed-text");
        mp.put("embedding_dimensions", 768);
        mp.put("embedding_compatibility_status", "INCOMPATIBLE");
        mp.put("embedding_compatibility_error_code", "EMBEDDING_DIMENSION_MISMATCH");
        mp.put(
                "embedding_compatibility_reason",
                "EMBEDDING_DIMENSION_MISMATCH: model 'nomic-embed-text' outputs 768 dimensions but this deployment's vector_store.embedding column is fixed to 1024");
        e.setMetricsPayload(mp);

        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setEmbeddingModelId("nomic-embed-text");

        Map<String, String> csv = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(e, run);
        assertThat(csv)
                .containsEntry("embeddingModelId", "nomic-embed-text")
                .containsEntry("embeddingDimensions", "768")
                .containsEntry("embeddingCompatibilityStatus", "INCOMPATIBLE")
                .containsEntry("embeddingCompatibilityErrorCode", "EMBEDDING_DIMENSION_MISMATCH")
                .containsEntry("outcome", "NOT_SUPPORTED")
                .containsEntry(
                        "unsupportedReason",
                        "The embedding model is not compatible with the vector index.");
        assertThat(csv.get("embeddingCompatibilityReason")).contains("768");
        assertThat(csv.get("failureCode")).isEmpty();
    }

    @Test
    void deriveRecallFromRank_whenRecallAt3Missing() {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.EMBEDDING_RETRIEVAL.name());
        e.setMetricsPayload(
                Map.of(
                        BenchmarkResultRowKeys.ITEM_OUTCOME,
                        BenchmarkItemOutcome.EXECUTED.name(),
                        "recall_at_1",
                        0.0,
                        "first_relevant_rank",
                        2,
                        "retrieved_count",
                        10,
                        "mrr",
                        0.5));
        Map<String, Object> mvp = BenchmarkMvpMetricsCalculator.computeMvpMetrics(e, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> ret = (Map<String, Object>) mvp.get("retrieval");
        assertThat(ret.get("recallAt3")).isEqualTo(1.0);
        assertThat(ret.get("recallAt5")).isEqualTo(1.0);
    }

    @Test
    void ragPresetRow_exportsRetrievalTelemetryFlatColumns() {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setId(UUID.randomUUID());
        e.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        e.setMetricsPayload(
                Map.of(
                        BenchmarkResultRowKeys.ITEM_OUTCOME,
                        BenchmarkItemOutcome.EXECUTED.name(),
                        BenchmarkResultRowKeys.PRESET_CODE,
                        "P3",
                        "retrievalDenseCandidateCount",
                        1,
                        "retrievalAfterFilterCount",
                        1,
                        "contextChunkCount",
                        1,
                        "promptContextCharCount",
                        241,
                        "sourceCount",
                        1,
                        "retrieved_chunk_ids",
                        List.of("snap:doc:0")));

        Map<String, String> csv = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(e, new EvaluationRunEntity());

        assertThat(csv.get("retrievalDenseCandidateCount")).isEqualTo("1");
        assertThat(csv.get("contextChunkCount")).isEqualTo("1");
        assertThat(csv.get("promptContextCharCount")).isEqualTo("241");
        assertThat(csv.get("sourceCount")).isEqualTo("1");
        assertThat(csv.get("retrievedChunkIds")).isEqualTo("snap:doc:0");
        assertThat(csv.get("effectiveContextPresent")).isEqualTo("true");
    }

    @Test
    void ragRow_analysisSectionIncludesAnswerabilityAndFinalScore() {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        e.setExpectedAnswer("Paris");
        e.setActualAnswer("Paris");
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
        mp.put(BenchmarkResultRowKeys.PRESET_CODE, "P3");
        mp.put(DatasetMetricContract.KEY_ANSWERABILITY, Answerability.ANSWERABLE.name());
        mp.put(DatasetMetricContract.KEY_QUERY_TYPE_EXPECTED, "GET_FIELD");
        mp.put("sourceCount", 1);
        e.setMetricsPayload(mp);

        Map<String, Object> mvp = BenchmarkMvpMetricsCalculator.computeMvpMetrics(e, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> analysis = (Map<String, Object>) mvp.get("analysis");
        assertThat(analysis.get("answerability")).isEqualTo("ANSWERABLE");
        assertThat(analysis.get("finalScore")).isEqualTo(1.0);
        assertThat(analysis.get("retrievalQualityStatus")).isEqualTo(RetrievalQualityStatus.NOT_AVAILABLE.name());

        Map<String, String> csv = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(e, null);
        assertThat(csv.get("answerability")).isEqualTo("ANSWERABLE");
        assertThat(csv.get("finalScore")).isEqualTo("1.0");
        assertThat(csv.get("retrievalQualityStatus")).isEqualTo(RetrievalQualityStatus.NOT_AVAILABLE.name());
    }

    @Test
    void ragRow_withGoldLabels_exportsComputedRecallInRetrievalSection() {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        e.setExpectedAnswer("x");
        e.setActualAnswer("y");
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
        mp.put(BenchmarkResultRowKeys.PRESET_CODE, "P4");
        mp.put(DatasetMetricContract.KEY_GOLD_DOCUMENT_IDS, List.of("ACTA_1"));
        mp.put("retrieved_chunk_ids", List.of("snap:ACTA_1:0"));
        mp.put("sourceCount", 1);
        e.setMetricsPayload(mp);

        Map<String, Object> mvp = BenchmarkMvpMetricsCalculator.computeMvpMetrics(e, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> ret = (Map<String, Object>) mvp.get("retrieval");
        assertThat(ret.get("retrievalQualityStatus")).isEqualTo(RetrievalQualityStatus.COMPUTED.name());
        assertThat(ret.get("recallAt1")).isEqualTo(1.0);
    }

    @Test
    void ragRow_backfillsQueryTypeExpectedFromEntityAtExportTime() {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        e.setQueryType("BOOLEAN_QUERY");
        e.setExpectedAnswer("yes");
        e.setActualAnswer("Yes.");
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
        mp.put(BenchmarkResultRowKeys.PRESET_CODE, "P3");
        mp.put("analysisVersion", RagPresetAnalysisMetrics.ANALYSIS_VERSION);
        mp.put("queryTypeMatch", RagPresetAnalysisMetrics.QueryTypeMatch.UNKNOWN.name());
        e.setMetricsPayload(mp);

        Map<String, Object> mvp = BenchmarkMvpMetricsCalculator.computeMvpMetrics(e, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> analysis = (Map<String, Object>) mvp.get("analysis");
        assertThat(analysis.get("queryTypeExpected")).isEqualTo("BOOLEAN_QUERY");

        Map<String, String> csv = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(e, null);
        assertThat(csv.get("queryTypeExpected")).isEqualTo("BOOLEAN_QUERY");
    }

    @Test
    void ragRow_exportsRoutingRouteKindFromExecutionRouteWhenMissing() {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        e.setQueryType("COUNT_DOCUMENTS");
        e.setExpectedAnswer("1");
        e.setActualAnswer("1");
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
        mp.put(BenchmarkResultRowKeys.PRESET_CODE, "P9");
        mp.put(RagPresetToolMetrics.KEY_EXECUTION_ROUTE, "FUNCTION_CALLING_ROUTE");
        mp.put(RagPresetToolMetrics.KEY_FUNCTION_CALL_ATTEMPTED, true);
        e.setMetricsPayload(mp);

        Map<String, String> csv = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(e, null);

        assertThat(csv.get("executionRoute")).isEqualTo("FUNCTION_CALLING_ROUTE");
        assertThat(csv.get("routingRouteKind")).isEqualTo("FUNCTION_CALLING_ROUTE");
    }

    @Test
    void ragRow_exportsFunctionProposalFieldsToCsv() {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        e.setQueryType("COUNT_DOCUMENTS");
        e.setExpectedAnswer("1");
        e.setActualAnswer("1");
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
        mp.put(BenchmarkResultRowKeys.PRESET_CODE, "P9");
        mp.put(RagPresetToolMetrics.KEY_FUNCTION_PROPOSAL_MODE, "BACKEND_DETERMINISTIC");
        mp.put(RagPresetToolMetrics.KEY_FUNCTION_PROPOSAL_SOURCE, "QUERY_SHAPE");
        mp.put(RagPresetToolMetrics.KEY_FUNCTION_PROPOSAL_VALID, true);
        mp.put(RagPresetToolMetrics.KEY_BACKEND_FUNCTION_CALL_ATTEMPTED, true);
        e.setMetricsPayload(mp);

        Map<String, String> csv = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(e, null);

        assertThat(csv.get("functionProposalMode")).isEqualTo("BACKEND_DETERMINISTIC");
        assertThat(csv.get("functionProposalSource")).isEqualTo("QUERY_SHAPE");
        assertThat(csv.get("functionProposalValid")).isEqualTo("true");
        assertThat(csv.get("backendFunctionCallAttempted")).isEqualTo("true");
    }

    @Test
    void ragRow_exportsSparseHybridExtendedFieldsToCsv() {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
        mp.put(BenchmarkResultRowKeys.PRESET_CODE, "P8");
        mp.put(RagPresetAdvancedRetrievalMetrics.KEY_SPARSE_QUERY_REWRITTEN, true);
        mp.put(RagPresetAdvancedRetrievalMetrics.KEY_SPARSE_FALLBACK_STAGE, "SYNONYM");
        mp.put(RagPresetAdvancedRetrievalMetrics.KEY_SPARSE_HIT, true);
        mp.put(RagPresetAdvancedRetrievalMetrics.KEY_FUSION_STRATEGY, "RRF");
        mp.put(RagPresetAdvancedRetrievalMetrics.KEY_PRE_FUSION_COUNT, 12);
        mp.put(RagPresetAdvancedRetrievalMetrics.KEY_POST_FUSION_COUNT, 8);
        mp.put(RagPresetAdvancedRetrievalMetrics.KEY_METADATA_CANDIDATE_COUNT, 3);
        mp.put(RagPresetAdvancedRetrievalMetrics.KEY_METADATA_FILTER_APPLIED, true);
        mp.put(RagPresetAdvancedRetrievalMetrics.KEY_METADATA_FILTER_FALLBACK, false);
        e.setMetricsPayload(mp);

        Map<String, String> csv = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(e, null);

        assertThat(csv.get("sparseQueryRewritten")).isEqualTo("true");
        assertThat(csv.get("sparseFallbackStage")).isEqualTo("SYNONYM");
        assertThat(csv.get("fusionStrategy")).isEqualTo("RRF");
        assertThat(csv.get("metadataFilterApplied")).isEqualTo("true");
    }

    @Test
    void ragRow_goldSubsetRun_enrichesAnswerabilityFromManifestAtExportTime() {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        e.setExpectedAnswer("x");
        e.setActualAnswer("y");
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
        mp.put(BenchmarkResultRowKeys.PRESET_CODE, "P6");
        mp.put(BenchmarkResultRowKeys.DATASET_QUESTION_ID, "RAG-001");
        e.setMetricsPayload(mp);

        EvaluationRunEntity run = new EvaluationRunEntity();
        run.setAggregatesJson(
                Map.of(
                        DatasetQuestionSubsetSupport.AGG_KEY_DATASET_QUESTION_FILTER,
                        DatasetQuestionSubsetSupport.FILTER_GOLD_SUBSET,
                        DatasetQuestionSubsetSupport.AGG_KEY_SUBSET_ID,
                        GoldSubsetManifestLoader.GOLD_SUBSET_V1,
                        DatasetQuestionSubsetSupport.AGG_KEY_FILTERED_QUESTION_IDS,
                        List.of("RAG-001"),
                        DatasetQuestionSubsetSupport.AGG_KEY_FILTERED_QUESTION_COUNT,
                        1));

        Map<String, String> csv = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(e, run);

        assertThat(csv.get("answerability")).isEqualTo("ANSWERABLE");
        assertThat(csv.get("answerabilitySource")).isEqualTo(AnswerabilitySource.GOLD_SUBSET_MANIFEST.name());
    }

    @Test
    void ragRow_unanswerable_exportsNegativeEvidenceFalsePositiveToCsv() {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        e.setExpectedAnswer("none");
        e.setActualAnswer("The document says 42.");
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
        mp.put(BenchmarkResultRowKeys.PRESET_CODE, "P3");
        mp.put(DatasetMetricContract.KEY_ANSWERABILITY, Answerability.UNANSWERABLE.name());
        e.setMetricsPayload(mp);

        Map<String, String> csv = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(e, null);

        assertThat(csv.get("negativeEvidenceFalsePositive")).isEqualTo("true");
    }

    @Test
    void ragRow_exportsBaselineFloorTelemetryToCsv() {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        e.setExpectedAnswer("expected");
        e.setActualAnswer("parent answer");
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
        mp.put(BenchmarkResultRowKeys.PRESET_CODE, "P15");
        mp.put("selectedCandidateSource", "PARENT_P7");
        mp.put("parentCampaignOutcomeReused", true);
        mp.put("parentFinalAnswerPreserved", true);
        mp.put("parentFinalAnswerHash", "abc");
        mp.put("selectedFinalAnswerHash", "abc");
        mp.put("baselineCandidateSource", "PARENT_P7");
        mp.put("baselineCandidatePresetCode", "P7");
        mp.put("baselineCandidateSelected", true);
        mp.put("baselineOverrideAttempted", false);
        mp.put("baselineOverrideAccepted", false);
        mp.put("baselineFloorApplied", true);
        mp.put("baselineFloorReason", "parent_p7_over_abstention");
        mp.put("monotonicFloorApplied", true);
        mp.put("monotonicFloorPreventedRegression", true);
        e.setMetricsPayload(mp);

        Map<String, String> csv = BenchmarkMvpMetricsCalculator.computeMvpFlatCsvRow(e, null);

        assertThat(csv.get("baselineCandidateSelected")).isEqualTo("true");
        assertThat(csv.get("baselineCandidateSource")).isEqualTo("PARENT_P7");
        assertThat(csv.get("baselineCandidatePresetCode")).isEqualTo("P7");
        assertThat(csv.get("monotonicFloorApplied")).isEqualTo("true");
        assertThat(csv.get("monotonicFloorPreventedRegression")).isEqualTo("true");
        assertThat(csv.get("parentFinalAnswerHash")).isEqualTo("abc");
        assertThat(csv.get("selectedFinalAnswerHash")).isEqualTo("abc");
    }
}
