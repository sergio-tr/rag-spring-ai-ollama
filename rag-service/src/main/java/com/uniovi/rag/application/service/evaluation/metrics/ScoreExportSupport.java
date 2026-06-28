package com.uniovi.rag.application.service.evaluation.metrics;

import java.util.Map;

/** Export-layer helpers for final score availability without changing score composition. */
public final class ScoreExportSupport {

    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_UNAVAILABLE = "UNAVAILABLE";

    private ScoreExportSupport() {}

    public static boolean isFinalScoreAvailable(Map<String, Object> analysis) {
        if (analysis == null || analysis.isEmpty()) {
            return false;
        }
        String reason = str(analysis.get(RagPresetAnalysisMetrics.KEY_SCORE_UNAVAILABLE_REASON));
        return reason.isBlank();
    }

    public static String finalScoreStatus(Map<String, Object> analysis) {
        return isFinalScoreAvailable(analysis) ? STATUS_AVAILABLE : STATUS_UNAVAILABLE;
    }

    public static String formatFinalScoreForCsv(Map<String, Object> analysis) {
        if (!isFinalScoreAvailable(analysis)) {
            return BenchmarkMvpSchema.NOT_AVAILABLE;
        }
        Object raw = firstNonNull(analysis.get("finalScore"), analysis.get("scoreFinal"));
        if (raw == null) {
            return BenchmarkMvpSchema.NOT_AVAILABLE;
        }
        return String.valueOf(raw);
    }

    public static void mergeAvailabilityFields(Map<String, Object> analysis) {
        if (analysis == null) {
            return;
        }
        boolean available = isFinalScoreAvailable(analysis);
        analysis.put("finalScoreAvailable", available);
        analysis.put("finalScoreStatus", available ? STATUS_AVAILABLE : STATUS_UNAVAILABLE);
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o).trim();
    }
}
