package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;

import java.util.ArrayList;
import java.util.DoubleSummaryStatistics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * MVP rollups for exports — {@code outcomeCounts} always splits EXECUTED vs other outcomes; retrieval/generation
 * means use **EXECUTED** rows only (never blended with NOT_SUPPORTED / SKIPPED).
 */
public final class BenchmarkMvpRollupCalculator {

    private BenchmarkMvpRollupCalculator() {}

    public static Map<String, Object> build(List<EvaluationResultEntity> items, EvaluationRunEntity run) {
        List<Map<String, Object>> mvps =
                items.stream().map(e -> BenchmarkMvpMetricsCalculator.computeMvpMetrics(e, run)).toList();

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("mvpSchemaVersion", BenchmarkMvpSchema.VERSION);
        root.put("globalMacro", rollupBucket(mvps));
        root.put("byQueryType", groupRollups(mvps, m -> keyOrUnknown(str(m.get("queryType")))));
        root.put("byDifficulty", groupRollups(mvps, m -> keyOrUnknown(str(m.get("difficulty")))));
        root.put("byAnswerability", groupRollups(mvps, m -> keyOrUnknown(answerabilityKey(m))));
        root.put("byWorkflow", groupRollups(mvps, m -> keyOrUnknown(workflowKey(m))));

        root.put(
                "byLlmModel",
                groupRollups(mvps, m -> keyOrUnknown(llmKey(BenchmarkMvpMetricsCalculator.operational(m)))));
        root.put(
                "byEmbeddingModel",
                groupRollups(mvps, m -> keyOrUnknown(embKey(BenchmarkMvpMetricsCalculator.operational(m)))));
        root.put(
                "byPreset",
                groupRollups(mvps, m -> keyOrUnknown(presetKey(BenchmarkMvpMetricsCalculator.operational(m)))));
        root.put("byRoute", groupRollups(mvps, m -> keyOrUnknown(routeKey(m))));
        root.put(
                "byBenchmarkSupportStatus",
                groupRollups(mvps, m -> keyOrUnknown(benchmarkSupportStatusKey(m))));
        root.put("byClassifierStatus", groupRollups(mvps, m -> keyOrUnknown(classifierStatusKey(m))));
        return root;
    }

    private static Map<String, Object> groupRollups(
            List<Map<String, Object>> mvps, Function<Map<String, Object>, String> keyFn) {
        Map<String, List<Map<String, Object>>> groups = new LinkedHashMap<>();
        for (Map<String, Object> mvp : mvps) {
            groups.computeIfAbsent(keyFn.apply(mvp), k -> new ArrayList<>()).add(mvp);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> sortedKeys = groups.keySet().stream().sorted().toList();
        for (String k : sortedKeys) {
            out.put(k, rollupBucket(groups.get(k)));
        }
        return out;
    }

    static Map<String, Object> rollupBucket(List<Map<String, Object>> bucketMvps) {
        Map<String, Long> outcomeCounts = new TreeMap<>();
        Map<String, Long> unsupportedReasons = new TreeMap<>();
        Map<String, Long> failureCodes = new TreeMap<>();

        DoubleSummaryStatistics recall1 = new DoubleSummaryStatistics();
        DoubleSummaryStatistics recall3 = new DoubleSummaryStatistics();
        DoubleSummaryStatistics recall5 = new DoubleSummaryStatistics();
        DoubleSummaryStatistics mrr = new DoubleSummaryStatistics();
        DoubleSummaryStatistics semanticWherePresent = new DoubleSummaryStatistics();
        DoubleSummaryStatistics normalizedExact = new DoubleSummaryStatistics();
        DoubleSummaryStatistics correctness = new DoubleSummaryStatistics();
        DoubleSummaryStatistics llmJudge = new DoubleSummaryStatistics();
        DoubleSummaryStatistics hallucination = new DoubleSummaryStatistics();
        DoubleSummaryStatistics faithfulness = new DoubleSummaryStatistics();
        DoubleSummaryStatistics sourceSupport = new DoubleSummaryStatistics();
        DoubleSummaryStatistics dateCorrectness = new DoubleSummaryStatistics();
        DoubleSummaryStatistics latencyMs = new DoubleSummaryStatistics();
        DoubleSummaryStatistics finalScore = new DoubleSummaryStatistics();
        DoubleSummaryStatistics answerableFinalScore = new DoubleSummaryStatistics();
        DoubleSummaryStatistics unanswerableFinalScore = new DoubleSummaryStatistics();
        DoubleSummaryStatistics ambiguousFinalScore = new DoubleSummaryStatistics();
        DoubleSummaryStatistics unknownAnswerabilityFinalScore = new DoubleSummaryStatistics();
        DoubleSummaryStatistics structuredWhereComputed = new DoubleSummaryStatistics();

        int executedGenerationN = 0;
        int retrievalContributors = 0;
        int abstainedCount = 0;
        int correctAbstentionCount = 0;
        int wrongAbstentionCount = 0;
        int retrievalCoverageApplicable = 0;
        int retrievalCoverageHits = 0;
        int sourceCoverageApplicable = 0;
        int sourceCoverageHits = 0;
        int queryTypeMatchKnown = 0;
        int queryTypeMatchHits = 0;
        int classifierFallbackCount = 0;
        int classifierStatusKnown = 0;
        int classifierValidCount = 0;
        int classifierInvalidCount = 0;
        int classifierUnavailableCount = 0;
        int classifierLowConfidenceCount = 0;
        int routeSuppressionCount = 0;
        int routeSuppressionKnown = 0;
        int toolApplicableCount = 0;
        int toolExecutedCount = 0;
        int toolSucceededCount = 0;
        int toolFallbackCount = 0;
        int toolFinalAnswerCount = 0;
        int toolRouteAttemptCount = 0;
        int functionCallAttemptCount = 0;
        int functionCallSuccessCount = 0;
        int functionCallFallbackCount = 0;
        int functionFinalAnswerCount = 0;
        Map<String, int[]> functionUsageByQueryType = new LinkedHashMap<>();
        int advisorAttemptCount = 0;
        int advisorAppliedCount = 0;
        int advisorFallbackCount = 0;
        int advisorContributionCount = 0;
        int advisorQueryChangeCount = 0;
        int advisorContextChangeCount = 0;
        int advisorValidationCount = 0;
        Map<String, int[]> advisorUsageByQueryType = new LinkedHashMap<>();
        int toolCoverageApplicableCount = 0;
        int toolCoverageApplicableSuccess = 0;
        DoubleSummaryStatistics structuredToolApplicable = new DoubleSummaryStatistics();
        int advancedRetrievalApplicable = 0;
        int advancedRetrievalAppliedCount = 0;
        int hybridAppliedCount = 0;
        int sparseHitCount = 0;
        int rerankAppliedCount = 0;
        int rerankChangedOrderCount = 0;
        int compressionAppliedCount = 0;
        int advancedRetrievalFallbackCount = 0;
        DoubleSummaryStatistics denseCandidates = new DoubleSummaryStatistics();
        DoubleSummaryStatistics sparseCandidates = new DoubleSummaryStatistics();
        DoubleSummaryStatistics mergedCandidates = new DoubleSummaryStatistics();
        DoubleSummaryStatistics finalContextChunks = new DoubleSummaryStatistics();
        DoubleSummaryStatistics promptContextChars = new DoubleSummaryStatistics();
        DoubleSummaryStatistics compressedContextChars = new DoubleSummaryStatistics();

        for (Map<String, Object> mvp : bucketMvps) {
            Map<String, Object> op = BenchmarkMvpMetricsCalculator.operational(mvp);
            String oc = str(op.get("outcome"));
            if (oc.isBlank()) {
                oc = BenchmarkItemOutcome.EXECUTED.name();
            }
            outcomeCounts.merge(oc, 1L, Long::sum);

            if (BenchmarkItemOutcome.NOT_SUPPORTED.name().equals(oc)) {
                String reason = str(op.get("unsupportedReason"));
                unsupportedReasons.merge(
                        reason.isBlank() ? LabBenchmarkExportLabels.MISSING_METADATA : reason, 1L, Long::sum);
            }
            if (BenchmarkItemOutcome.FAILED.name().equals(oc)) {
                String fc = str(op.get("failureCode"));
                failureCodes.merge(
                        fc.isBlank() ? LabBenchmarkExportLabels.MISSING_METADATA : fc, 1L, Long::sum);
            }

            if (!BenchmarkItemOutcome.EXECUTED.name().equals(oc)) {
                continue;
            }

            executedGenerationN++;
            Map<String, Object> gen = BenchmarkMvpMetricsCalculator.generation(mvp);
            Object nem = gen.get("normalizedExactMatch");
            normalizedExact.accept(Boolean.TRUE.equals(nem) ? 1.0 : 0.0);

            Object ss = gen.get("semanticScore");
            if (ss instanceof Number n) {
                semanticWherePresent.accept(n.doubleValue());
            }
            acceptMetric(gen.get("correctness"), correctness);
            acceptMetric(gen.get("llmJudgeScore"), llmJudge);
            acceptMetric(gen.get("hallucinationRate"), hallucination);
            acceptMetric(gen.get("faithfulness"), faithfulness);
            acceptMetric(gen.get("sourceSupport"), sourceSupport);
            acceptMetric(gen.get("dateCorrectness"), dateCorrectness);

            Object lm = op.get("latencyMs");
            if (lm instanceof Number n) {
                latencyMs.accept(n.doubleValue());
            }

            Optional<Double> v1 = BenchmarkMvpMetricsCalculator.recallAt1ForRollup(mvp);
            if (v1.isPresent()) {
                retrievalContributors++;
                recall1.accept(v1.get());
                BenchmarkMvpMetricsCalculator.recallAt3ForRollup(mvp).ifPresent(recall3::accept);
                BenchmarkMvpMetricsCalculator.recallAt5ForRollup(mvp).ifPresent(recall5::accept);
                BenchmarkMvpMetricsCalculator.mrrForRollup(mvp).ifPresent(mrr::accept);
            }

            Map<String, Object> analysis = BenchmarkMvpMetricsCalculator.analysis(mvp);
            if (analysis != null && !analysis.isEmpty() && ScoreExportSupport.isFinalScoreAvailable(analysis)) {
                Object fs = firstNonNull(analysis.get("finalScore"), analysis.get("scoreFinal"));
                acceptMetric(fs, finalScore);
                String answerability = str(analysis.get(DatasetMetricContract.KEY_ANSWERABILITY));
                if (fs instanceof Number n) {
                    switch (answerability) {
                        case "ANSWERABLE" -> answerableFinalScore.accept(n.doubleValue());
                        case "UNANSWERABLE" -> unanswerableFinalScore.accept(n.doubleValue());
                        case "AMBIGUOUS" -> ambiguousFinalScore.accept(n.doubleValue());
                        case "UNKNOWN" -> unknownAnswerabilityFinalScore.accept(n.doubleValue());
                        default -> { }
                    }
                }
                Object structuredScoreValue = analysis.get("structuredScore");
                if (structuredScoreValue instanceof Number structuredN
                        && StructuredScoreStatus.COMPUTED
                                .name()
                                .equals(str(analysis.get("structuredScoreStatus")))) {
                    structuredWhereComputed.accept(structuredN.doubleValue());
                }
                if (Boolean.TRUE.equals(analysis.get("abstained"))) {
                    abstainedCount++;
                }
                if (Boolean.TRUE.equals(analysis.get("correctAbstention"))) {
                    correctAbstentionCount++;
                }
                if (Boolean.TRUE.equals(analysis.get("wrongAbstention"))) {
                    wrongAbstentionCount++;
                }
                String retrievalCoverage = str(analysis.get("retrievalCoverageStatus"));
                if (!retrievalCoverage.isBlank() && !CoverageStatus.NOT_APPLICABLE.name().equals(retrievalCoverage)) {
                    retrievalCoverageApplicable++;
                    if (CoverageStatus.HAS_CONTEXT.name().equals(retrievalCoverage)) {
                        retrievalCoverageHits++;
                    }
                }
                String sourceCoverage = str(analysis.get("sourceCoverageStatus"));
                if (!sourceCoverage.isBlank() && !CoverageStatus.NOT_APPLICABLE.name().equals(sourceCoverage)) {
                    sourceCoverageApplicable++;
                    if (CoverageStatus.HAS_CONTEXT.name().equals(sourceCoverage)) {
                        sourceCoverageHits++;
                    }
                }
                String queryTypeMatch = str(analysis.get("queryTypeMatch"));
                if (!queryTypeMatch.isBlank()
                        && !RagPresetAnalysisMetrics.QueryTypeMatch.UNKNOWN.name().equals(queryTypeMatch)) {
                    queryTypeMatchKnown++;
                    if (RagPresetAnalysisMetrics.QueryTypeMatch.MATCH.name().equals(queryTypeMatch)) {
                        queryTypeMatchHits++;
                    }
                }
                if (Boolean.TRUE.equals(analysis.get("classifierFallback"))) {
                    classifierFallbackCount++;
                }
                String classifierStatus = str(analysis.get("classifierStatus"));
                if (!classifierStatus.isBlank()) {
                    classifierStatusKnown++;
                    if ("OK".equalsIgnoreCase(classifierStatus)) {
                        classifierValidCount++;
                    } else if ("INVALID_OUTPUT".equalsIgnoreCase(classifierStatus)) {
                        classifierInvalidCount++;
                    } else if ("LOW_CONFIDENCE".equalsIgnoreCase(classifierStatus)) {
                        classifierLowConfidenceCount++;
                    } else if ("UNAVAILABLE".equalsIgnoreCase(classifierStatus)
                            || "TIMEOUT".equalsIgnoreCase(classifierStatus)
                            || "INVALID_REQUEST".equalsIgnoreCase(classifierStatus)) {
                        classifierUnavailableCount++;
                    }
                }
                if (analysis.containsKey(RagPresetClassifierMetrics.KEY_ROUTE_SUPPRESSED_BY_CLASSIFIER)) {
                    routeSuppressionKnown++;
                    if (Boolean.TRUE.equals(analysis.get(RagPresetClassifierMetrics.KEY_ROUTE_SUPPRESSED_BY_CLASSIFIER))) {
                        routeSuppressionCount++;
                    }
                }
                if (Boolean.TRUE.equals(analysis.get(RagPresetToolMetrics.KEY_TOOL_APPLICABLE))) {
                    toolApplicableCount++;
                }
                if (Boolean.TRUE.equals(analysis.get(RagPresetToolMetrics.KEY_TOOL_EXECUTED))) {
                    toolExecutedCount++;
                }
                if (Boolean.TRUE.equals(analysis.get(RagPresetToolMetrics.KEY_TOOL_SUCCEEDED))) {
                    toolSucceededCount++;
                }
                if (Boolean.TRUE.equals(analysis.get(RagPresetToolMetrics.KEY_DETERMINISTIC_TOOL_ROUTE))) {
                    toolRouteAttemptCount++;
                }
                String toolFallback = str(analysis.get(RagPresetToolMetrics.KEY_TOOL_FALLBACK_REASON));
                if (!toolFallback.isBlank()) {
                    toolFallbackCount++;
                }
                if (Boolean.TRUE.equals(analysis.get(RagPresetToolMetrics.KEY_TOOL_RESULT_USED_AS_FINAL))) {
                    toolFinalAnswerCount++;
                }
                if (ToolCoverageStatus.APPLICABLE.name().equals(str(analysis.get(RagPresetToolMetrics.KEY_TOOL_COVERAGE_STATUS)))) {
                    toolCoverageApplicableCount++;
                    Object structured = analysis.get("structuredScore");
                    if (structured instanceof Number n) {
                        structuredToolApplicable.accept(n.doubleValue());
                    }
                    if (Boolean.TRUE.equals(analysis.get(RagPresetToolMetrics.KEY_TOOL_SUCCEEDED))) {
                        toolCoverageApplicableSuccess++;
                    }
                }
                if (Boolean.TRUE.equals(analysis.get(RagPresetToolMetrics.KEY_FUNCTION_CALL_ATTEMPTED))) {
                    functionCallAttemptCount++;
                    String qt = keyOrUnknown(str(analysis.get(DatasetMetricContract.KEY_QUERY_TYPE_EXPECTED)));
                    int[] usage = functionUsageByQueryType.computeIfAbsent(qt, k -> new int[2]);
                    usage[0]++;
                }
                if (Boolean.TRUE.equals(analysis.get(RagPresetToolMetrics.KEY_FUNCTION_CALLING_USED))) {
                    functionCallSuccessCount++;
                    String qt = keyOrUnknown(str(analysis.get(DatasetMetricContract.KEY_QUERY_TYPE_EXPECTED)));
                    int[] usage = functionUsageByQueryType.computeIfAbsent(qt, k -> new int[2]);
                    usage[1]++;
                }
                String functionFallback = str(analysis.get(RagPresetToolMetrics.KEY_FUNCTION_CALL_FALLBACK_REASON));
                if (!functionFallback.isBlank()) {
                    functionCallFallbackCount++;
                }
                if (Boolean.TRUE.equals(analysis.get(RagPresetToolMetrics.KEY_FUNCTION_RESULT_USED_AS_FINAL))) {
                    functionFinalAnswerCount++;
                }
                if (Boolean.TRUE.equals(analysis.get(RagPresetAdvisorMetrics.KEY_ADVISOR_ATTEMPTED))) {
                    advisorAttemptCount++;
                    String qt = keyOrUnknown(str(analysis.get(DatasetMetricContract.KEY_QUERY_TYPE_EXPECTED)));
                    int[] usage = advisorUsageByQueryType.computeIfAbsent(qt, k -> new int[2]);
                    usage[0]++;
                }
                if (Boolean.TRUE.equals(analysis.get(RagPresetAdvisorMetrics.KEY_ADVISOR_APPLIED))) {
                    advisorAppliedCount++;
                    String qt = keyOrUnknown(str(analysis.get(DatasetMetricContract.KEY_QUERY_TYPE_EXPECTED)));
                    int[] usage = advisorUsageByQueryType.computeIfAbsent(qt, k -> new int[2]);
                    usage[1]++;
                }
                String advisorFallback = str(analysis.get(RagPresetAdvisorMetrics.KEY_ADVISOR_FALLBACK_REASON));
                if (!advisorFallback.isBlank()) {
                    advisorFallbackCount++;
                }
                if (Boolean.TRUE.equals(analysis.get(RagPresetAdvisorMetrics.KEY_ADVISOR_RESULT_USED))) {
                    advisorContributionCount++;
                }
                if (Boolean.TRUE.equals(analysis.get(RagPresetAdvisorMetrics.KEY_ADVISOR_CHANGED_QUERY))) {
                    advisorQueryChangeCount++;
                }
                if (Boolean.TRUE.equals(analysis.get(RagPresetAdvisorMetrics.KEY_ADVISOR_CHANGED_CONTEXT))) {
                    advisorContextChangeCount++;
                }
                if (Boolean.TRUE.equals(analysis.get(RagPresetAdvisorMetrics.KEY_ADVISOR_VALIDATED_ANSWER))) {
                    advisorValidationCount++;
                }
                if (analysis.containsKey(RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_APPLIED)) {
                    advancedRetrievalApplicable++;
                    if (Boolean.TRUE.equals(analysis.get(RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_APPLIED))) {
                        advancedRetrievalAppliedCount++;
                    }
                }
                if (Boolean.TRUE.equals(analysis.get(RagPresetAdvancedRetrievalMetrics.KEY_HYBRID_APPLIED))) {
                    hybridAppliedCount++;
                }
                int sparseCount =
                        intMetric(analysis.get(RagPresetAdvancedRetrievalMetrics.KEY_SPARSE_CANDIDATE_COUNT));
                if (sparseCount > 0) {
                    sparseHitCount++;
                }
                if (Boolean.TRUE.equals(analysis.get(RagPresetAdvancedRetrievalMetrics.KEY_RERANK_APPLIED))) {
                    rerankAppliedCount++;
                }
                if (Boolean.TRUE.equals(analysis.get(RagPresetAdvancedRetrievalMetrics.KEY_RERANK_CHANGED_ORDER))) {
                    rerankChangedOrderCount++;
                }
                if (Boolean.TRUE.equals(analysis.get(RagPresetAdvancedRetrievalMetrics.KEY_COMPRESSION_APPLIED))) {
                    compressionAppliedCount++;
                }
                String advFallback =
                        str(analysis.get(RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_FALLBACK_REASON));
                if (!advFallback.isBlank()) {
                    advancedRetrievalFallbackCount++;
                }
                acceptMetric(analysis.get(RagPresetAdvancedRetrievalMetrics.KEY_DENSE_CANDIDATE_COUNT), denseCandidates);
                acceptMetric(analysis.get(RagPresetAdvancedRetrievalMetrics.KEY_SPARSE_CANDIDATE_COUNT), sparseCandidates);
                acceptMetric(analysis.get(RagPresetAdvancedRetrievalMetrics.KEY_MERGED_CANDIDATE_COUNT), mergedCandidates);
                acceptMetric(analysis.get(RagPresetAdvancedRetrievalMetrics.KEY_FINAL_CONTEXT_CHUNK_COUNT), finalContextChunks);
                acceptMetric(analysis.get("promptContextCharCount"), promptContextChars);
                acceptMetric(
                        analysis.get(RagPresetAdvancedRetrievalMetrics.KEY_COMPRESSED_CONTEXT_CHAR_COUNT),
                        compressedContextChars);
            }
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("outcomeCounts", outcomeCounts);
        out.put("unsupportedReasons", unsupportedReasons);
        out.put("failureCodes", failureCodes);
        Map<String, Object> onExecuted = new LinkedHashMap<>();
        onExecuted.put("n", executedGenerationN);
        onExecuted.put(
                "meanNormalizedExactMatch",
                executedGenerationN > 0 ? normalizedExact.getAverage() : null);
        onExecuted.put(
                "meanSemanticScoreWhereJudgePresent",
                semanticWherePresent.getCount() > 0 ? semanticWherePresent.getAverage() : null);
        onExecuted.put("semanticJudgePresentCount", semanticWherePresent.getCount());
        onExecuted.put("meanCorrectness", averageOrNull(correctness));
        onExecuted.put("meanLlmJudgeScore", averageOrNull(llmJudge));
        onExecuted.put("meanHallucinationRate", averageOrNull(hallucination));
        onExecuted.put("meanFaithfulness", averageOrNull(faithfulness));
        onExecuted.put("meanSourceSupport", averageOrNull(sourceSupport));
        onExecuted.put("meanDateCorrectness", averageOrNull(dateCorrectness));
        onExecuted.put("dateCorrectnessSampleCount", dateCorrectness.getCount());
        onExecuted.put("meanLatencyMsWherePresent", latencyMs.getCount() > 0 ? latencyMs.getAverage() : null);
        onExecuted.put("latencySampleCount", latencyMs.getCount());
        onExecuted.put("finalScoreSampleCount", finalScore.getCount());
        onExecuted.put("meanFinalScore", averageOrNull(finalScore));
        onExecuted.put("scoreGlobal", averageOrNull(finalScore));
        onExecuted.put("meanAnswerableFinalScore", averageOrNull(answerableFinalScore));
        onExecuted.put("scoreAnswerable", averageOrNull(answerableFinalScore));
        onExecuted.put("meanUnanswerableFinalScore", averageOrNull(unanswerableFinalScore));
        onExecuted.put("scoreUnanswerable", averageOrNull(unanswerableFinalScore));
        onExecuted.put("meanAmbiguousFinalScore", averageOrNull(ambiguousFinalScore));
        onExecuted.put("scoreAmbiguous", averageOrNull(ambiguousFinalScore));
        onExecuted.put("meanUnknownAnswerabilityFinalScore", averageOrNull(unknownAnswerabilityFinalScore));
        onExecuted.put("scoreUnknownAnswerability", averageOrNull(unknownAnswerabilityFinalScore));
        onExecuted.put(
                "meanStructuredScoreWhereComputed",
                structuredWhereComputed.getCount() > 0 ? structuredWhereComputed.getAverage() : null);
        onExecuted.put(
                "perQueryTypeStructuredScore",
                structuredWhereComputed.getCount() > 0 ? structuredWhereComputed.getAverage() : null);
        onExecuted.put("abstentionRate", rate(abstainedCount, executedGenerationN));
        onExecuted.put("perQueryTypeAbstentionRate", rate(abstainedCount, executedGenerationN));
        onExecuted.put("perQueryTypeMacroScore", averageOrNull(finalScore));
        onExecuted.put("correctAbstentionRate", rate(correctAbstentionCount, executedGenerationN));
        onExecuted.put("wrongAbstentionRate", rate(wrongAbstentionCount, executedGenerationN));
        onExecuted.put("retrievalCoverageRate", rate(retrievalCoverageHits, retrievalCoverageApplicable));
        onExecuted.put("sourceCoverageRate", rate(sourceCoverageHits, sourceCoverageApplicable));
        onExecuted.put("queryTypeMatchRate", rate(queryTypeMatchHits, queryTypeMatchKnown));
        onExecuted.put("classifierFallbackRate", rate(classifierFallbackCount, classifierStatusKnown));
        onExecuted.put("classifierValidRate", rate(classifierValidCount, classifierStatusKnown));
        onExecuted.put("classifierInvalidRate", rate(classifierInvalidCount, classifierStatusKnown));
        onExecuted.put("classifierUnavailableRate", rate(classifierUnavailableCount, classifierStatusKnown));
        onExecuted.put("classifierLowConfidenceRate", rate(classifierLowConfidenceCount, classifierStatusKnown));
        onExecuted.put("routeSuppressionRate", rate(routeSuppressionCount, routeSuppressionKnown));
        onExecuted.put("toolCoverageRate", rate(toolApplicableCount, executedGenerationN));
        onExecuted.put("toolExecutionRate", rate(toolExecutedCount, toolApplicableCount));
        onExecuted.put("toolSuccessRate", rate(toolSucceededCount, toolExecutedCount));
        onExecuted.put("toolFallbackRate", rate(toolFallbackCount, toolRouteAttemptCount));
        onExecuted.put("toolFinalAnswerRate", rate(toolFinalAnswerCount, toolSucceededCount));
        onExecuted.put("functionCallAttemptRate", rate(functionCallAttemptCount, executedGenerationN));
        onExecuted.put("functionCallUsageRate", rate(functionCallSuccessCount, executedGenerationN));
        onExecuted.put("functionCallSuccessRate", rate(functionCallSuccessCount, functionCallAttemptCount));
        onExecuted.put("functionCallFallbackRate", rate(functionCallFallbackCount, functionCallAttemptCount));
        onExecuted.put("functionFinalAnswerRate", rate(functionFinalAnswerCount, functionCallSuccessCount));
        onExecuted.put("functionUsageByQueryType", buildFunctionUsageByQueryType(functionUsageByQueryType));
        onExecuted.put("advisorAttemptRate", rate(advisorAttemptCount, executedGenerationN));
        onExecuted.put("advisorAppliedRate", rate(advisorAppliedCount, executedGenerationN));
        onExecuted.put("advisorFallbackRate", rate(advisorFallbackCount, advisorAttemptCount));
        onExecuted.put("advisorContributionRate", rate(advisorContributionCount, advisorAppliedCount));
        onExecuted.put("advisorQueryChangeRate", rate(advisorQueryChangeCount, advisorAttemptCount));
        onExecuted.put("advisorContextChangeRate", rate(advisorContextChangeCount, advisorAttemptCount));
        onExecuted.put("advisorValidationRate", rate(advisorValidationCount, advisorAttemptCount));
        onExecuted.put("advisorUsageByQueryType", buildFunctionUsageByQueryType(advisorUsageByQueryType));
        onExecuted.put("deterministicToolRouteRate", rate(toolRouteAttemptCount, executedGenerationN));
        onExecuted.put("toolCoverageApplicableSuccessRate", rate(toolCoverageApplicableSuccess, toolCoverageApplicableCount));
        onExecuted.put(
                "meanStructuredScoreToolApplicable",
                structuredToolApplicable.getCount() > 0 ? structuredToolApplicable.getAverage() : null);
        onExecuted.put(
                "advancedRetrievalCoverageRate",
                rate(advancedRetrievalAppliedCount, advancedRetrievalApplicable));
        onExecuted.put("sparseHitRate", rate(sparseHitCount, advancedRetrievalApplicable));
        onExecuted.put("hybridAppliedRate", rate(hybridAppliedCount, advancedRetrievalApplicable));
        onExecuted.put("rerankAppliedRate", rate(rerankAppliedCount, advancedRetrievalApplicable));
        onExecuted.put("rerankChangedOrderRate", rate(rerankChangedOrderCount, rerankAppliedCount));
        onExecuted.put("compressionAppliedRate", rate(compressionAppliedCount, advancedRetrievalApplicable));
        onExecuted.put(
                "advancedRetrievalFallbackRate",
                rate(advancedRetrievalFallbackCount, advancedRetrievalApplicable));
        onExecuted.put(
                "averageDenseCandidates",
                denseCandidates.getCount() > 0 ? denseCandidates.getAverage() : null);
        onExecuted.put(
                "averageSparseCandidates",
                sparseCandidates.getCount() > 0 ? sparseCandidates.getAverage() : null);
        onExecuted.put(
                "averageMergedCandidates",
                mergedCandidates.getCount() > 0 ? mergedCandidates.getAverage() : null);
        onExecuted.put(
                "averageFinalContextChunks",
                finalContextChunks.getCount() > 0 ? finalContextChunks.getAverage() : null);
        onExecuted.put(
                "averagePromptContextChars",
                promptContextChars.getCount() > 0 ? promptContextChars.getAverage() : null);
        onExecuted.put(
                "averageCompressedContextChars",
                compressedContextChars.getCount() > 0 ? compressedContextChars.getAverage() : null);
        out.put("onExecuted", onExecuted);

        Map<String, Object> retrievalOnExec = new LinkedHashMap<>();
        retrievalOnExec.put("n", retrievalContributors);
        retrievalOnExec.put("meanRecallAt1", retrievalContributors > 0 ? recall1.getAverage() : null);
        retrievalOnExec.put("meanRecallAt3", retrievalContributors > 0 ? recall3.getAverage() : null);
        retrievalOnExec.put("meanRecallAt5", retrievalContributors > 0 ? recall5.getAverage() : null);
        retrievalOnExec.put("meanMrr", retrievalContributors > 0 ? mrr.getAverage() : null);
        out.put("retrievalOnExecutedWhereApplicable", retrievalOnExec);
        return out;
    }

    private static String answerabilityKey(Map<String, Object> mvp) {
        Map<String, Object> analysis = BenchmarkMvpMetricsCalculator.analysis(mvp);
        if (analysis == null || analysis.isEmpty()) {
            return Answerability.UNKNOWN.name();
        }
        return str(analysis.get(DatasetMetricContract.KEY_ANSWERABILITY));
    }

    private static String workflowKey(Map<String, Object> mvp) {
        Map<String, Object> analysis = BenchmarkMvpMetricsCalculator.analysis(mvp);
        if (analysis != null && !str(analysis.get("workflowName")).isBlank()) {
            return str(analysis.get("workflowName"));
        }
        Map<String, Object> op = BenchmarkMvpMetricsCalculator.operational(mvp);
        return str(op.get("workflowName"));
    }

    private static String classifierStatusKey(Map<String, Object> mvp) {
        Map<String, Object> analysis = BenchmarkMvpMetricsCalculator.analysis(mvp);
        if (analysis == null) {
            return LabBenchmarkExportLabels.MISSING_METADATA;
        }
        String status = str(analysis.get("classifierStatus"));
        return status.isBlank() ? LabBenchmarkExportLabels.MISSING_METADATA : status;
    }

    private static Double rate(int numerator, int denominator) {
        return denominator > 0 ? (double) numerator / denominator : null;
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    private static String llmKey(Map<String, Object> op) {
        return str(op.get("modelId"));
    }

    private static String embKey(Map<String, Object> op) {
        return str(op.get("embeddingModelId"));
    }

    private static String presetKey(Map<String, Object> op) {
        return str(op.get("presetCode"));
    }

    private static String routeKey(Map<String, Object> mvp) {
        Map<String, Object> analysis = BenchmarkMvpMetricsCalculator.analysis(mvp);
        if (analysis != null) {
            String route = str(analysis.get(RagPresetToolMetrics.KEY_ROUTING_ROUTE_KIND));
            if (!route.isBlank()) {
                return route;
            }
            route = str(analysis.get(RagPresetToolMetrics.KEY_EXECUTION_ROUTE));
            if (!route.isBlank()) {
                return route;
            }
        }
        Map<String, Object> op = BenchmarkMvpMetricsCalculator.operational(mvp);
        return str(op.get("workflowName"));
    }

    private static String benchmarkSupportStatusKey(Map<String, Object> mvp) {
        Map<String, Object> op = BenchmarkMvpMetricsCalculator.operational(mvp);
        return str(op.get("benchmarkSupportStatus"));
    }

    private static String keyOrUnknown(String raw) {
        return LabBenchmarkExportLabels.normalizeGroupKey(raw);
    }

    private static Map<String, Object> buildFunctionUsageByQueryType(Map<String, int[]> usage) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> keys = usage.keySet().stream().sorted().toList();
        for (String key : keys) {
            int[] counts = usage.get(key);
            Map<String, Object> bucket = new LinkedHashMap<>();
            bucket.put("attempts", counts[0]);
            bucket.put("successes", counts[1]);
            out.put(key, bucket);
        }
        return out;
    }

    private static void acceptMetric(Object raw, DoubleSummaryStatistics stats) {
        if (raw instanceof Number n) {
            stats.accept(n.doubleValue());
        }
    }

    private static int intMetric(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static Double averageOrNull(DoubleSummaryStatistics stats) {
        return stats.getCount() > 0 ? stats.getAverage() : null;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
