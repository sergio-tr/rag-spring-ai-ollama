package com.uniovi.rag.application.service.evaluation;

import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregated per-item outcomes for RAG / preset benchmark closure validation. */
public record RagBenchmarkOutcomeTally(
        long expectedItems,
        long executed,
        long failed,
        long skipped,
        long notSupported,
        long modelNotAvailable,
        long other,
        long totalRows,
        long judgeDegradedExecuted,
        boolean skippedMissingReason,
        boolean notSupportedMissingReason) {

    /** At least one item executed successfully (success with results). */
    public static final String CLASSIFICATION_COMPLETED_OK = "COMPLETED_OK";

    public static final String CLASSIFICATION_SUCCESS_WITH_RESULTS = CLASSIFICATION_COMPLETED_OK;
    public static final String CLASSIFICATION_COMPLETED_WITH_FAILURES = "COMPLETED_WITH_FAILURES";
    public static final String CLASSIFICATION_COMPLETED_WITH_UNSUPPORTED = "COMPLETED_WITH_UNSUPPORTED";
    public static final String CLASSIFICATION_COMPLETED_WITH_NO_EXECUTED_ITEMS =
            "COMPLETED_WITH_NO_EXECUTED_ITEMS";
    public static final String CLASSIFICATION_FAILED_NO_EXECUTED_ITEMS =
            CLASSIFICATION_COMPLETED_WITH_NO_EXECUTED_ITEMS;
    public static final String CLASSIFICATION_COMPLETED_WITH_EVALUATION_WARNINGS =
            "COMPLETED_WITH_EVALUATION_WARNINGS";
    public static final String CLASSIFICATION_FAILED_EVALUATION_JUDGE_UNAVAILABLE =
            "FAILED_EVALUATION_JUDGE_UNAVAILABLE";

    public long accountedItems() {
        return executed + failed + skipped + notSupported + modelNotAvailable + other;
    }

    public static RagBenchmarkOutcomeTally fromResultRows(
            List<EvaluationResultEntity> items, long expectedItems) {
        long executed = 0;
        long failed = 0;
        long skipped = 0;
        long notSupported = 0;
        long modelNotAvailable = 0;
        long other = 0;
        long judgeFailedExecuted = 0;
        boolean skippedMissingReason = false;
        boolean notSupportedMissingReason = false;

        for (EvaluationResultEntity item : items) {
            if (item == null) {
                continue;
            }
            String outcome = readOutcome(item);
            Map<String, Object> mp = item.getMetricsPayload() != null ? item.getMetricsPayload() : Map.of();
            switch (outcome) {
                case "EXECUTED" -> {
                    executed++;
                    if (isJudgeDegraded(mp)) {
                        judgeFailedExecuted++;
                    }
                }
                case "FAILED" -> failed++;
                case "SKIPPED" -> {
                    skipped++;
                    if (!hasSkipReason(mp)) {
                        skippedMissingReason = true;
                    }
                }
                case "NOT_SUPPORTED" -> {
                    notSupported++;
                    if (!hasUnsupportedReason(mp)) {
                        notSupportedMissingReason = true;
                    }
                }
                case "MODEL_NOT_AVAILABLE" -> modelNotAvailable++;
                default -> other++;
            }
        }
        long totalRows = items.size();
        long resolvedExpected = expectedItems > 0 ? expectedItems : totalRows;
        return new RagBenchmarkOutcomeTally(
                resolvedExpected,
                executed,
                failed,
                skipped,
                notSupported,
                modelNotAvailable,
                other,
                totalRows,
                judgeFailedExecuted,
                skippedMissingReason,
                notSupportedMissingReason);
    }

    public String classifyCompletion() {
        if (expectedItems > 0 && executed <= 0) {
            return CLASSIFICATION_COMPLETED_WITH_NO_EXECUTED_ITEMS;
        }
        if (executed > 0 && judgeDegradedExecuted == executed) {
            return CLASSIFICATION_COMPLETED_WITH_EVALUATION_WARNINGS;
        }
        if (executed > 0 && notSupported > 0 && failed == 0 && skipped == 0) {
            return CLASSIFICATION_COMPLETED_WITH_UNSUPPORTED;
        }
        if (executed > 0 && (failed > 0 || skipped > 0)) {
            return CLASSIFICATION_COMPLETED_WITH_FAILURES;
        }
        if (executed > 0) {
            return CLASSIFICATION_COMPLETED_OK;
        }
        return CLASSIFICATION_COMPLETED_WITH_NO_EXECUTED_ITEMS;
    }

    public Map<String, Object> toClosureMap() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("expectedItems", expectedItems);
        m.put("executedItems", executed);
        m.put("failedItems", failed);
        m.put("skippedItems", skipped);
        m.put("notSupportedItems", notSupported);
        m.put("modelNotAvailableItems", modelNotAvailable);
        m.put("otherOutcomeItems", other);
        m.put("totalRows", totalRows);
        m.put("classification", classifyCompletion());
        return Map.copyOf(m);
    }

    private static String readOutcome(EvaluationResultEntity item) {
        Map<String, Object> mp = item.getMetricsPayload();
        if (mp == null) {
            return BenchmarkItemOutcome.SKIPPED.name();
        }
        Object raw = mp.get(BenchmarkResultRowKeys.ITEM_OUTCOME);
        if (raw == null) {
            return BenchmarkItemOutcome.SKIPPED.name();
        }
        String s = raw.toString().trim();
        return s.isEmpty() ? BenchmarkItemOutcome.SKIPPED.name() : s;
    }

    private static boolean hasSkipReason(Map<String, Object> mp) {
        return hasText(mp, "skippedReason")
                || hasText(mp, "skippedReasonCode")
                || hasText(mp, "humanReason")
                || hasText(mp, BenchmarkResultRowKeys.ERROR_CODE)
                || hasText(mp, BenchmarkResultRowKeys.REASON);
    }

    private static boolean hasUnsupportedReason(Map<String, Object> mp) {
        return hasText(mp, BenchmarkResultRowKeys.ERROR_CODE)
                || hasText(mp, "unsupportedReason")
                || hasText(mp, BenchmarkResultRowKeys.REASON)
                || hasText(mp, "skippedReason")
                || hasText(mp, "skippedReasonCode")
                || hasText(mp, "humanReason");
    }

    private static boolean hasText(Map<String, Object> mp, String key) {
        Object v = mp.get(key);
        return v != null && !v.toString().isBlank();
    }

    private static boolean isJudgeDegraded(Map<String, Object> mp) {
        Object status = mp.get(BenchmarkResultRowKeys.JUDGE_STATUS);
        return status != null && "FAILED".equalsIgnoreCase(String.valueOf(status).trim());
    }
}
