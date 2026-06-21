package com.uniovi.rag.application.service.evaluation.metrics.querytype;

import com.uniovi.rag.application.service.evaluation.metrics.BenchmarkMvpSchema;
import com.uniovi.rag.application.service.evaluation.metrics.StructuredScoreStatus;
import java.util.LinkedHashMap;
import java.util.Map;

/** Deterministic structured evaluation output for a single item. */
public record StructuredEvaluationResult(
        StructuredScoreStatus status,
        Double score,
        Boolean countMatch,
        Boolean booleanMatch,
        Boolean dateMatch,
        Boolean durationMatch,
        Double fieldMatchScore,
        Double entityPrecision,
        Double entityRecall,
        Double entityF1,
        Double listPrecision,
        Double listRecall,
        Double listF1) {

    public static final String KEY_STRUCTURED_SCORE = "structuredScore";
    public static final String KEY_STRUCTURED_SCORE_STATUS = "structuredScoreStatus";

    public static StructuredEvaluationResult notAvailable() {
        return new StructuredEvaluationResult(
                StructuredScoreStatus.NOT_AVAILABLE,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public void mergeInto(Map<String, Object> out) {
        if (out == null) {
            return;
        }
        out.put(KEY_STRUCTURED_SCORE_STATUS, status.name());
        if (status == StructuredScoreStatus.COMPUTED && score != null) {
            out.put(KEY_STRUCTURED_SCORE, score);
        } else {
            out.put(KEY_STRUCTURED_SCORE, BenchmarkMvpSchema.NOT_AVAILABLE);
        }
        putIfPresent(out, "countMatch", countMatch);
        putIfPresent(out, "booleanMatch", booleanMatch);
        putIfPresent(out, "dateMatch", dateMatch);
        putIfPresent(out, "durationMatch", durationMatch);
        putIfPresent(out, "fieldMatchScore", fieldMatchScore);
        putIfPresent(out, "entityPrecision", entityPrecision);
        putIfPresent(out, "entityRecall", entityRecall);
        putIfPresent(out, "entityF1", entityF1);
        putIfPresent(out, "listPrecision", listPrecision);
        putIfPresent(out, "listRecall", listRecall);
        putIfPresent(out, "listF1", listF1);
    }

    private static void putIfPresent(Map<String, Object> out, String key, Object value) {
        if (value != null) {
            out.put(key, value);
        }
    }
}
