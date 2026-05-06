package com.uniovi.rag.service.evaluation.mvp;

import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Per-row MVP metrics for TFG exports — computed from persisted {@link EvaluationResultEntity} (+ run fallbacks).
 */
public final class BenchmarkMvpMetricsCalculator {

    private BenchmarkMvpMetricsCalculator() {}

    /**
     * Returns a JSON-serializable map with retrieval, generation, operational sections (camelCase keys).
     */
    public static Map<String, Object> computeMvpMetrics(EvaluationResultEntity item, EvaluationRunEntity run) {
        Map<String, Object> mp = payload(item);
        String kind = item.getBenchmarkKind();
        String outcome = str(mp.get(BenchmarkResultRowKeys.ITEM_OUTCOME));

        Map<String, Object> retrieval = new LinkedHashMap<>();
        boolean retrievalApplicable = BenchmarkKind.EMBEDDING_RETRIEVAL.name().equals(kind);
        retrieval.put("applicable", retrievalApplicable);
        if (retrievalApplicable) {
            retrieval.put("recallAt1", dbl(mp.get("recall_at_1")));
            retrieval.put("recallAt3", dblOrDeriveAtN(mp, 3));
            retrieval.put("recallAt5", dblOrDeriveAtN(mp, 5));
            retrieval.put("mrr", dbl(mp.get("mrr")));
            retrieval.put("retrievedCount", intNum(mp.get("retrieved_count")));
            retrieval.put("goldFound", bool(mp.get("gold_found"), deriveGoldFound(mp)));
        } else {
            retrieval.put("recallAt1", BenchmarkMvpSchema.NOT_AVAILABLE);
            retrieval.put("recallAt3", BenchmarkMvpSchema.NOT_AVAILABLE);
            retrieval.put("recallAt5", BenchmarkMvpSchema.NOT_AVAILABLE);
            retrieval.put("mrr", BenchmarkMvpSchema.NOT_AVAILABLE);
            retrieval.put("retrievedCount", BenchmarkMvpSchema.NOT_AVAILABLE);
            retrieval.put("goldFound", BenchmarkMvpSchema.NOT_AVAILABLE);
        }

        String expected = item.getExpectedAnswer() != null ? item.getExpectedAnswer() : "";
        String actual = item.getActualAnswer() != null ? item.getActualAnswer() : "";
        Map<String, Object> generation = new LinkedHashMap<>();
        generation.put("normalizedExactMatch", normalizedExactMatch(expected, actual));
        generation.put("containsExpectedAnswer", containsExpectedAnswer(expected, actual));
        generation.put("answerLength", actual.length());
        Object semantic = semanticScore(mp);
        generation.put("semanticScore", semantic);

        Map<String, Object> operational = new LinkedHashMap<>();
        operational.put("latencyMs", item.getLatencyMs());
        operational.put(
                "modelId",
                firstNonBlank(str(mp.get(BenchmarkResultRowKeys.LLM_MODEL_ID)), run != null ? run.getLlmModelId() : null));
        operational.put(
                "embeddingModelId",
                firstNonBlank(
                        str(mp.get(BenchmarkResultRowKeys.EMBEDDING_MODEL_ID)),
                        run != null ? run.getEmbeddingModelId() : null));
        operational.put("presetCode", str(mp.get(BenchmarkResultRowKeys.PRESET_CODE)));
        operational.put("outcome", outcome.isBlank() ? BenchmarkItemOutcome.EXECUTED.name() : outcome);
        operational.put("failureCode", failureCode(outcome, mp));
        operational.put("unsupportedReason", unsupportedReason(outcome, mp));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("mvpSchemaVersion", BenchmarkMvpSchema.VERSION);
        root.put("retrieval", retrieval);
        root.put("generation", generation);
        root.put("operational", operational);
        root.put("queryType", item.getQueryType());
        root.put("difficulty", str(mp.get(BenchmarkResultRowKeys.DIFFICULTY)));
        root.put("datasetQuestionId", mp.get(BenchmarkResultRowKeys.DATASET_QUESTION_ID));
        return root;
    }

    /** Flat row for CSV (string cells; use {@link BenchmarkMvpSchema#NOT_AVAILABLE} where needed). */
    public static Map<String, String> computeMvpFlatCsvRow(EvaluationResultEntity item, EvaluationRunEntity run) {
        Map<String, Object> mvp = computeMvpMetrics(item, run);
        Map<String, Object> mp = payload(item);
        @SuppressWarnings("unchecked")
        Map<String, Object> ret = (Map<String, Object>) mvp.get("retrieval");
        @SuppressWarnings("unchecked")
        Map<String, Object> gen = (Map<String, Object>) mvp.get("generation");
        @SuppressWarnings("unchecked")
        Map<String, Object> op = (Map<String, Object>) mvp.get("operational");

        Map<String, String> row = new LinkedHashMap<>();
        row.put("mvpSchemaVersion", BenchmarkMvpSchema.VERSION);
        row.put("itemId", item.getId() != null ? item.getId().toString() : "");
        row.put("benchmarkKind", str(item.getBenchmarkKind()));
        row.put("queryType", str(mvp.get("queryType")));
        row.put("difficulty", str(mvp.get("difficulty")));
        row.put("datasetQuestionId", str(mvp.get("datasetQuestionId")));

        row.put("recallAt1", csvVal(ret.get("recallAt1")));
        row.put("recallAt3", csvVal(ret.get("recallAt3")));
        row.put("recallAt5", csvVal(ret.get("recallAt5")));
        row.put("mrr", csvVal(ret.get("mrr")));
        row.put("retrievedCount", csvVal(ret.get("retrievedCount")));
        row.put("goldFound", csvVal(ret.get("goldFound")));

        row.put("normalizedExactMatch", csvVal(gen.get("normalizedExactMatch")));
        row.put("containsExpectedAnswer", csvVal(gen.get("containsExpectedAnswer")));
        row.put("answerLength", csvVal(gen.get("answerLength")));
        row.put("semanticScore", csvVal(gen.get("semanticScore")));

        row.put("latencyMs", csvVal(op.get("latencyMs")));
        row.put("modelId", csvVal(op.get("modelId")));
        row.put("embeddingModelId", csvVal(op.get("embeddingModelId")));
        row.put("presetCode", csvVal(op.get("presetCode")));
        row.put("outcome", csvVal(op.get("outcome")));
        row.put("failureCode", csvVal(op.get("failureCode")));
        row.put("unsupportedReason", csvVal(op.get("unsupportedReason")));

        // Embedding retrieval: export gold + retrieved id lists for reproducibility/debugging.
        row.put("retrievalGoldMode", csvVal(mp.get("retrieval_gold_mode")));
        row.put("goldChunkIds", joinIds(mp.get("gold_chunk_ids")));
        row.put("goldDocumentIds", joinIds(mp.get("gold_document_ids")));
        row.put("retrievedChunkIds", joinIds(mp.get("retrieved_chunk_ids")));
        row.put("retrievedDocumentIds", joinIds(mp.get("retrieved_document_ids")));
        return row;
    }

    private static Map<String, Object> payload(EvaluationResultEntity item) {
        return item.getMetricsPayload() != null ? item.getMetricsPayload() : Map.of();
    }

    private static Object semanticScore(Map<String, Object> mp) {
        Object js = mp.get("judge_scores");
        if (!(js instanceof Map<?, ?> m)) {
            return BenchmarkMvpSchema.NOT_AVAILABLE;
        }
        Object c = m.get("correctness");
        if (!(c instanceof Number n)) {
            return BenchmarkMvpSchema.NOT_AVAILABLE;
        }
        return n.doubleValue() / 5.0;
    }

    private static String failureCode(String outcome, Map<String, Object> mp) {
        if (!BenchmarkItemOutcome.FAILED.name().equals(outcome)) {
            return "";
        }
        String code = str(mp.get(BenchmarkResultRowKeys.ERROR_CODE));
        if (!code.isBlank()) {
            return code;
        }
        String err = str(mp.get("error"));
        return err.isBlank() ? "UNKNOWN_FAILURE" : truncate(err, 120);
    }

    private static String unsupportedReason(String outcome, Map<String, Object> mp) {
        if (!BenchmarkItemOutcome.NOT_SUPPORTED.name().equals(outcome)) {
            return "";
        }
        String code = str(mp.get(BenchmarkResultRowKeys.ERROR_CODE));
        return code.isBlank() ? "NOT_SUPPORTED" : code;
    }

    private static boolean deriveGoldFound(Map<String, Object> mp) {
        int rank = intNum(mp.get("first_relevant_rank"));
        return rank > 0;
    }

    private static Double dblOrDeriveAtN(Map<String, Object> mp, int n) {
        Object raw = mp.get(n == 3 ? "recall_at_3" : "recall_at_5");
        if (raw instanceof Number num) {
            return num.doubleValue();
        }
        int rank = intNum(mp.get("first_relevant_rank"));
        int retrieved = intNum(mp.get("retrieved_count"));
        if (rank <= 0 || retrieved <= 0) {
            return 0.0;
        }
        return rank <= n ? 1.0 : 0.0;
    }

    private static Double dbl(Object o) {
        if (o instanceof Number n) {
            return n.doubleValue();
        }
        return 0.0;
    }

    private static int intNum(Object o) {
        if (o instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private static boolean bool(Object primary, boolean fallback) {
        if (primary instanceof Boolean b) {
            return b;
        }
        return fallback;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b != null ? b : "";
    }

    private static String normalizeAnswer(String s) {
        if (s == null) {
            return "";
        }
        String n = Normalizer.normalize(s, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return n.replaceAll("\\s+", " ").trim();
    }

    static boolean normalizedExactMatch(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return normalizeAnswer(expected).equals(normalizeAnswer(actual));
    }

    static boolean containsExpectedAnswer(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        String ne = normalizeAnswer(expected);
        String na = normalizeAnswer(actual);
        if (ne.isEmpty()) {
            return false;
        }
        if (ne.length() < 3) {
            return na.contains(ne);
        }
        return na.contains(ne);
    }

    private static String csvVal(Object o) {
        if (o == null) {
            return "";
        }
        return String.valueOf(o);
    }

    private static String joinIds(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object o : list) {
            if (o == null) {
                continue;
            }
            String s = String.valueOf(o).trim();
            if (s.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append(s);
        }
        return sb.toString();
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    /** Numeric summaries for rollups (executed-only retrieval slice). */
    public static Optional<Double> recallAt1ForRollup(Map<String, Object> mvpRoot) {
        return recallDouble(mvpRoot, "recallAt1");
    }

    public static Optional<Double> recallAt3ForRollup(Map<String, Object> mvpRoot) {
        return recallDouble(mvpRoot, "recallAt3");
    }

    public static Optional<Double> recallAt5ForRollup(Map<String, Object> mvpRoot) {
        return recallDouble(mvpRoot, "recallAt5");
    }

    public static Optional<Double> mrrForRollup(Map<String, Object> mvpRoot) {
        return recallDouble(mvpRoot, "mrr");
    }

    @SuppressWarnings("unchecked")
    private static Optional<Double> recallDouble(Map<String, Object> mvpRoot, String key) {
        Map<String, Object> ret = (Map<String, Object>) mvpRoot.get("retrieval");
        if (ret == null || !Boolean.TRUE.equals(ret.get("applicable"))) {
            return Optional.empty();
        }
        Object v = ret.get(key);
        if (v instanceof Number n) {
            return Optional.of(n.doubleValue());
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> operational(Map<String, Object> mvpRoot) {
        return (Map<String, Object>) mvpRoot.get("operational");
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> generation(Map<String, Object> mvpRoot) {
        return (Map<String, Object>) mvpRoot.get("generation");
    }
}
