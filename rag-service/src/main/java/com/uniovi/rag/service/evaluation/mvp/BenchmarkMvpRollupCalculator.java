package com.uniovi.rag.service.evaluation.mvp;

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
 * MVP rollups for thesis exports — {@code outcomeCounts} always splits EXECUTED vs other outcomes; retrieval/generation
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

        root.put(
                "byLlmModel",
                groupRollups(mvps, m -> keyOrUnknown(llmKey(BenchmarkMvpMetricsCalculator.operational(m)))));
        root.put(
                "byEmbeddingModel",
                groupRollups(mvps, m -> keyOrUnknown(embKey(BenchmarkMvpMetricsCalculator.operational(m)))));
        root.put(
                "byPreset",
                groupRollups(mvps, m -> keyOrUnknown(presetKey(BenchmarkMvpMetricsCalculator.operational(m)))));
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
        DoubleSummaryStatistics latencyMs = new DoubleSummaryStatistics();

        int executedGenerationN = 0;
        int retrievalContributors = 0;

        for (Map<String, Object> mvp : bucketMvps) {
            Map<String, Object> op = BenchmarkMvpMetricsCalculator.operational(mvp);
            String oc = str(op.get("outcome"));
            if (oc.isBlank()) {
                oc = BenchmarkItemOutcome.EXECUTED.name();
            }
            outcomeCounts.merge(oc, 1L, Long::sum);

            if (BenchmarkItemOutcome.NOT_SUPPORTED.name().equals(oc)) {
                String reason = str(op.get("unsupportedReason"));
                unsupportedReasons.merge(reason.isBlank() ? "_UNKNOWN" : reason, 1L, Long::sum);
            }
            if (BenchmarkItemOutcome.FAILED.name().equals(oc)) {
                String fc = str(op.get("failureCode"));
                failureCodes.merge(fc.isBlank() ? "_UNKNOWN" : fc, 1L, Long::sum);
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
        onExecuted.put("meanLatencyMsWherePresent", latencyMs.getCount() > 0 ? latencyMs.getAverage() : null);
        onExecuted.put("latencySampleCount", latencyMs.getCount());
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

    private static String llmKey(Map<String, Object> op) {
        return str(op.get("modelId"));
    }

    private static String embKey(Map<String, Object> op) {
        return str(op.get("embeddingModelId"));
    }

    private static String presetKey(Map<String, Object> op) {
        return str(op.get("presetCode"));
    }

    private static String keyOrUnknown(String raw) {
        String t = raw != null ? raw.trim() : "";
        return t.isEmpty() ? "_UNKNOWN" : t;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }
}
