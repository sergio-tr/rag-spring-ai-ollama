package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkMvpRollupCalculatorTest {

    @Test
    void globalMacro_separatesOutcomeCounts_fromExecutedMeans() {
        EvaluationResultEntity embExec = embeddingRow(BenchmarkItemOutcome.EXECUTED, 1.0, "FACTOID", "EASY");
        EvaluationResultEntity embNs =
                ragLikeRow(BenchmarkItemOutcome.NOT_SUPPORTED, "FACTOID", "EASY", "PRESET_X");

        Map<String, Object> roll = BenchmarkMvpRollupCalculator.build(List.of(embExec, embNs), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> macro = (Map<String, Object>) roll.get("globalMacro");
        @SuppressWarnings("unchecked")
        Map<String, Long> oc = (Map<String, Long>) macro.get("outcomeCounts");
        assertThat(oc.get(BenchmarkItemOutcome.EXECUTED.name())).isEqualTo(1L);
        assertThat(oc.get(BenchmarkItemOutcome.NOT_SUPPORTED.name())).isEqualTo(1L);

        @SuppressWarnings("unchecked")
        Map<String, Object> retr = (Map<String, Object>) macro.get("retrievalOnExecutedWhereApplicable");
        assertThat(retr.get("n")).isEqualTo(1);
        assertThat(retr.get("meanRecallAt1")).isEqualTo(1.0);

        @SuppressWarnings("unchecked")
        Map<String, Long> unsup = (Map<String, Long>) macro.get("unsupportedReasons");
        assertThat(unsup.get("PRESET_X")).isEqualTo(1L);
    }

    @Test
    void unavailableFinalScores_excludedFromGlobalMean() {
        EvaluationResultEntity available = ragAnalysisRow(Answerability.ANSWERABLE.name(), false, 1.0, "HAS_CONTEXT", "MATCH");
        EvaluationResultEntity unavailable = ragAnalysisRow(Answerability.ANSWERABLE.name(), false, 0.0, "HAS_CONTEXT", "MATCH");
        unavailable.getMetricsPayload().put(RagPresetAnalysisMetrics.KEY_SCORE_UNAVAILABLE_REASON, "no_scoring_signal");

        Map<String, Object> roll = BenchmarkMvpRollupCalculator.build(List.of(available, unavailable), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> macro = (Map<String, Object>) roll.get("globalMacro");
        @SuppressWarnings("unchecked")
        Map<String, Object> onExecuted = (Map<String, Object>) macro.get("onExecuted");
        assertThat(onExecuted.get("scoreGlobal")).isEqualTo(1.0);
        assertThat(onExecuted.get("finalScoreSampleCount")).isEqualTo(1L);
    }

    @Test
    void byRoute_groupsExecutedRowsByRoutingKind() {
        EvaluationResultEntity routed = ragAnalysisRow(Answerability.ANSWERABLE.name(), false, 1.0, "HAS_CONTEXT", "MATCH");
        routed.getMetricsPayload().put(RagPresetToolMetrics.KEY_ROUTING_ROUTE_KIND, "DETERMINISTIC_TOOL");

        Map<String, Object> roll = BenchmarkMvpRollupCalculator.build(List.of(routed), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> byRoute = (Map<String, Object>) roll.get("byRoute");
        assertThat(byRoute).containsKey("DETERMINISTIC_TOOL");
    }

    @Test
    void byAnswerability_andAnalysisRates_onExecutedRows() {
        EvaluationResultEntity answerable =
                ragAnalysisRow(Answerability.ANSWERABLE.name(), false, 1.0, "HAS_CONTEXT", "MATCH");
        EvaluationResultEntity unanswerable =
                ragAnalysisRow(Answerability.UNANSWERABLE.name(), true, 1.0, "NO_CONTEXT", "UNKNOWN");

        Map<String, Object> roll = BenchmarkMvpRollupCalculator.build(List.of(answerable, unanswerable), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> byAnswerability = (Map<String, Object>) roll.get("byAnswerability");
        @SuppressWarnings("unchecked")
        Map<String, Object> answerableBucket = (Map<String, Object>) byAnswerability.get("ANSWERABLE");
        @SuppressWarnings("unchecked")
        Map<String, Object> onExecuted =
                (Map<String, Object>) answerableBucket.get("onExecuted");
        assertThat(onExecuted.get("meanFinalScore")).isEqualTo(1.0);

        @SuppressWarnings("unchecked")
        Map<String, Object> macro = (Map<String, Object>) roll.get("globalMacro");
        @SuppressWarnings("unchecked")
        Map<String, Object> macroExecuted = (Map<String, Object>) macro.get("onExecuted");
        assertThat(macroExecuted.get("abstentionRate")).isEqualTo(0.5);
        assertThat(macroExecuted.get("correctAbstentionRate")).isEqualTo(0.5);
        assertThat(macroExecuted.get("retrievalCoverageRate")).isEqualTo(0.5);
        assertThat(macroExecuted.get("queryTypeMatchRate")).isEqualTo(1.0);
    }

    @Test
    void globalMacro_functionCallingRollupRates_onExecutedRows() {
        EvaluationResultEntity attempted =
                ragFunctionCallingRow(true, false, false, "COUNT_DOCUMENTS", "model_declined");
        EvaluationResultEntity success =
                ragFunctionCallingRow(true, true, true, "COUNT_DOCUMENTS", "");
        EvaluationResultEntity noAttempt = ragFunctionCallingRow(false, false, false, "SUMMARIZE_TOPIC", "");

        Map<String, Object> roll =
                BenchmarkMvpRollupCalculator.build(List.of(attempted, success, noAttempt), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> macro = (Map<String, Object>) roll.get("globalMacro");
        @SuppressWarnings("unchecked")
        Map<String, Object> onExecuted = (Map<String, Object>) macro.get("onExecuted");

        assertThat(onExecuted.get("functionCallAttemptRate")).isEqualTo(2.0 / 3.0);
        assertThat(onExecuted.get("functionCallUsageRate")).isEqualTo(1.0 / 3.0);
        assertThat(onExecuted.get("functionCallSuccessRate")).isEqualTo(0.5);
        assertThat(onExecuted.get("functionCallFallbackRate")).isEqualTo(0.5);
        assertThat(onExecuted.get("functionFinalAnswerRate")).isEqualTo(1.0);
        @SuppressWarnings("unchecked")
        Map<String, Object> byQt = (Map<String, Object>) onExecuted.get("functionUsageByQueryType");
        @SuppressWarnings("unchecked")
        Map<String, Object> countBucket = (Map<String, Object>) byQt.get("COUNT_DOCUMENTS");
        assertThat(countBucket.get("attempts")).isEqualTo(2);
        assertThat(countBucket.get("successes")).isEqualTo(1);
    }

    @Test
    void globalMacro_advancedRetrievalRollupRates_onHybridRows() {
        EvaluationResultEntity hybrid = ragAdvancedRetrievalRow(true, true, true, true, 5, 3, 4, 200);
        hybrid.getMetricsPayload().put(RagPresetAdvancedRetrievalMetrics.KEY_RERANK_CHANGED_ORDER, true);
        EvaluationResultEntity denseOnly = ragAdvancedRetrievalRow(false, false, false, false, 4, 0, 3, 150);

        Map<String, Object> roll = BenchmarkMvpRollupCalculator.build(List.of(hybrid, denseOnly), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> macro = (Map<String, Object>) roll.get("globalMacro");
        @SuppressWarnings("unchecked")
        Map<String, Object> onExecuted = (Map<String, Object>) macro.get("onExecuted");

        assertThat(onExecuted.get("advancedRetrievalCoverageRate")).isEqualTo(0.5);
        assertThat(onExecuted.get("hybridAppliedRate")).isEqualTo(0.5);
        assertThat(onExecuted.get("rerankAppliedRate")).isEqualTo(0.5);
        assertThat(onExecuted.get("compressionAppliedRate")).isEqualTo(0.5);
        assertThat(onExecuted.get("averageDenseCandidates")).isEqualTo(4.5);
        assertThat(onExecuted.get("averageSparseCandidates")).isEqualTo(1.5);
        assertThat(onExecuted.get("averageFinalContextChunks")).isEqualTo(3.5);
        assertThat(onExecuted.get("averagePromptContextChars")).isEqualTo(175.0);
        assertThat(onExecuted.get("sparseHitRate")).isEqualTo(0.5);
        assertThat(onExecuted.get("rerankChangedOrderRate")).isEqualTo(1.0);
    }

    @Test
    void globalMacro_sparseHitAndMergedRollups_whenSparsePresent() {
        EvaluationResultEntity withSparse = ragAdvancedRetrievalRow(true, true, true, false, 4, 2, 3, 180);
        withSparse.getMetricsPayload().put("mergedCandidateCount", 3);
        withSparse.getMetricsPayload().put("compressedContextCharCount", 120);
        withSparse.getMetricsPayload().put(RagPresetAdvancedRetrievalMetrics.KEY_RERANK_CHANGED_ORDER, true);

        Map<String, Object> roll = BenchmarkMvpRollupCalculator.build(List.of(withSparse), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> onExecuted = (Map<String, Object>) ((Map<String, Object>) roll.get("globalMacro")).get("onExecuted");

        assertThat(onExecuted.get("sparseHitRate")).isEqualTo(1.0);
        assertThat(onExecuted.get("averageMergedCandidates")).isEqualTo(3.0);
        assertThat(onExecuted.get("averageCompressedContextChars")).isEqualTo(120.0);
    }

    @Test
    void globalMacro_advisorRollupRates_onExecutedRows() {
        EvaluationResultEntity attempted =
                ragAdvisorRow(true, false, false, false, false, "SUMMARIZE_TOPIC", "retrieval_failed");
        EvaluationResultEntity success =
                ragAdvisorRow(true, true, true, true, true, "COUNT_DOCUMENTS", "");
        EvaluationResultEntity noAttempt = ragAdvisorRow(false, false, false, false, false, "FACTOID", "");

        Map<String, Object> roll =
                BenchmarkMvpRollupCalculator.build(List.of(attempted, success, noAttempt), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> macro = (Map<String, Object>) roll.get("globalMacro");
        @SuppressWarnings("unchecked")
        Map<String, Object> onExecuted = (Map<String, Object>) macro.get("onExecuted");

        assertThat(onExecuted.get("advisorAttemptRate")).isEqualTo(2.0 / 3.0);
        assertThat(onExecuted.get("advisorAppliedRate")).isEqualTo(1.0 / 3.0);
        assertThat(onExecuted.get("advisorFallbackRate")).isEqualTo(0.5);
        assertThat(onExecuted.get("advisorContributionRate")).isEqualTo(1.0);
        assertThat(onExecuted.get("advisorQueryChangeRate")).isEqualTo(0.0);
        assertThat(onExecuted.get("advisorContextChangeRate")).isEqualTo(0.5);
        assertThat(onExecuted.get("advisorValidationRate")).isEqualTo(0.5);
        @SuppressWarnings("unchecked")
        Map<String, Object> byQt = (Map<String, Object>) onExecuted.get("advisorUsageByQueryType");
        @SuppressWarnings("unchecked")
        Map<String, Object> countBucket = (Map<String, Object>) byQt.get("COUNT_DOCUMENTS");
        assertThat(countBucket.get("attempts")).isEqualTo(1);
        assertThat(countBucket.get("successes")).isEqualTo(1);
    }

    @Test
    void globalMacro_sourceCoverageRate_whenRetrievalRowsPresent() {
        EvaluationResultEntity retrieval =
                ragAnalysisRow(Answerability.UNKNOWN.name(), false, 0.0, "HAS_CONTEXT", "MATCH");
        Map<String, Object> roll = BenchmarkMvpRollupCalculator.build(List.of(retrieval), null);
        @SuppressWarnings("unchecked")
        Map<String, Object> macro = (Map<String, Object>) roll.get("globalMacro");
        @SuppressWarnings("unchecked")
        Map<String, Object> onExecuted = (Map<String, Object>) macro.get("onExecuted");
        assertThat(onExecuted.get("sourceCoverageRate")).isEqualTo(1.0);
    }

    private static EvaluationResultEntity ragAdvisorRow(
            boolean attempted,
            boolean applied,
            boolean resultUsed,
            boolean changedContext,
            boolean validated,
            String queryTypeExpected,
            String fallbackReason) {
        EvaluationResultEntity e = ragAnalysisRow(Answerability.ANSWERABLE.name(), false, 1.0, "HAS_CONTEXT", "MATCH");
        Map<String, Object> mp = new LinkedHashMap<>(e.getMetricsPayload());
        mp.put(DatasetMetricContract.KEY_QUERY_TYPE_EXPECTED, queryTypeExpected);
        mp.put(RagPresetAdvisorMetrics.KEY_ADVISOR_ATTEMPTED, attempted);
        mp.put(RagPresetAdvisorMetrics.KEY_ADVISOR_APPLIED, applied);
        mp.put(RagPresetAdvisorMetrics.KEY_ADVISOR_RESULT_USED, resultUsed);
        mp.put(RagPresetAdvisorMetrics.KEY_ADVISOR_CHANGED_CONTEXT, changedContext);
        mp.put(RagPresetAdvisorMetrics.KEY_ADVISOR_VALIDATED_ANSWER, validated);
        if (!fallbackReason.isBlank()) {
            mp.put(RagPresetAdvisorMetrics.KEY_ADVISOR_FALLBACK_REASON, fallbackReason);
        }
        e.setMetricsPayload(mp);
        return e;
    }

    private static EvaluationResultEntity ragFunctionCallingRow(
            boolean attempted,
            boolean succeeded,
            boolean usedAsFinal,
            String queryTypeExpected,
            String fallbackReason) {
        EvaluationResultEntity e = ragAnalysisRow(Answerability.ANSWERABLE.name(), false, 1.0, "HAS_CONTEXT", "MATCH");
        Map<String, Object> mp = new LinkedHashMap<>(e.getMetricsPayload());
        mp.put(DatasetMetricContract.KEY_QUERY_TYPE_EXPECTED, queryTypeExpected);
        mp.put(RagPresetToolMetrics.KEY_FUNCTION_CALL_ATTEMPTED, attempted);
        mp.put(RagPresetToolMetrics.KEY_FUNCTION_CALLING_USED, succeeded);
        mp.put(RagPresetToolMetrics.KEY_FUNCTION_CALL_SUCCEEDED, succeeded);
        mp.put(RagPresetToolMetrics.KEY_FUNCTION_RESULT_USED_AS_FINAL, usedAsFinal);
        if (!fallbackReason.isBlank()) {
            mp.put(RagPresetToolMetrics.KEY_FUNCTION_CALL_FALLBACK_REASON, fallbackReason);
        }
        e.setMetricsPayload(mp);
        return e;
    }

    private static EvaluationResultEntity ragAdvancedRetrievalRow(
            boolean advancedApplied,
            boolean hybridApplied,
            boolean rerankApplied,
            boolean compressionApplied,
            int dense,
            int sparse,
            int finalChunks,
            int promptChars) {
        EvaluationResultEntity e = ragAnalysisRow(Answerability.ANSWERABLE.name(), false, 1.0, "HAS_CONTEXT", "MATCH");
        Map<String, Object> mp = new LinkedHashMap<>(e.getMetricsPayload());
        mp.put(RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_APPLIED, advancedApplied);
        mp.put(RagPresetAdvancedRetrievalMetrics.KEY_HYBRID_APPLIED, hybridApplied);
        mp.put(RagPresetAdvancedRetrievalMetrics.KEY_RERANK_APPLIED, rerankApplied);
        mp.put(RagPresetAdvancedRetrievalMetrics.KEY_COMPRESSION_APPLIED, compressionApplied);
        mp.put(RagPresetAdvancedRetrievalMetrics.KEY_DENSE_CANDIDATE_COUNT, dense);
        mp.put(RagPresetAdvancedRetrievalMetrics.KEY_SPARSE_CANDIDATE_COUNT, sparse);
        mp.put(RagPresetAdvancedRetrievalMetrics.KEY_FINAL_CONTEXT_CHUNK_COUNT, finalChunks);
        mp.put("promptContextCharCount", promptChars);
        e.setMetricsPayload(mp);
        return e;
    }

    private static EvaluationResultEntity ragAnalysisRow(
            String answerability,
            boolean abstained,
            double finalScore,
            String retrievalCoverage,
            String queryTypeMatch) {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        e.setExpectedAnswer("a");
        e.setActualAnswer(abstained ? "Insufficient evidence." : "a");
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, BenchmarkItemOutcome.EXECUTED.name());
        mp.put(BenchmarkResultRowKeys.PRESET_CODE, "P3");
        mp.put("analysisVersion", RagPresetAnalysisMetrics.ANALYSIS_VERSION);
        mp.put(DatasetMetricContract.KEY_ANSWERABILITY, answerability);
        mp.put("abstained", abstained);
        mp.put("finalScore", finalScore);
        mp.put("scoreFinal", finalScore);
        mp.put("retrievalCoverageStatus", retrievalCoverage);
        mp.put("sourceCoverageStatus", retrievalCoverage);
        mp.put("queryTypeMatch", queryTypeMatch);
        mp.put("workflowName", "ChunkDenseWorkflow");
        if (Answerability.UNANSWERABLE.name().equals(answerability)) {
            mp.put("correctAbstention", abstained);
            mp.put("wrongAbstention", false);
        } else {
            mp.put("correctAbstention", false);
            mp.put("wrongAbstention", abstained);
        }
        e.setMetricsPayload(mp);
        return e;
    }

    private static EvaluationResultEntity embeddingRow(
            BenchmarkItemOutcome outcome, double r1, String queryType, String difficulty) {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.EMBEDDING_RETRIEVAL.name());
        e.setQueryType(queryType);
        e.setExpectedAnswer("gold");
        e.setActualAnswer("x");
        e.setLatencyMs(1L);
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, outcome.name());
        mp.put(BenchmarkResultRowKeys.DIFFICULTY, difficulty);
        mp.put("recall_at_1", r1);
        mp.put("recall_at_3", r1);
        mp.put("recall_at_5", r1);
        mp.put("mrr", r1);
        mp.put("retrieved_count", 3);
        mp.put("gold_found", r1 > 0);
        e.setMetricsPayload(mp);
        return e;
    }

    private static EvaluationResultEntity ragLikeRow(
            BenchmarkItemOutcome outcome, String queryType, String difficulty, String errorCode) {
        EvaluationResultEntity e = new EvaluationResultEntity();
        e.setBenchmarkKind(BenchmarkKind.RAG_PRESET_END_TO_END.name());
        e.setQueryType(queryType);
        e.setExpectedAnswer("a");
        e.setActualAnswer("");
        Map<String, Object> mp = new LinkedHashMap<>();
        mp.put(BenchmarkResultRowKeys.ITEM_OUTCOME, outcome.name());
        mp.put(BenchmarkResultRowKeys.DIFFICULTY, difficulty);
        mp.put(BenchmarkResultRowKeys.ERROR_CODE, errorCode);
        e.setMetricsPayload(mp);
        return e;
    }
}
