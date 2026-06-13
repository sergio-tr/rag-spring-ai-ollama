package com.uniovi.rag.application.service.evaluation.metrics;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import com.uniovi.rag.domain.evaluation.BenchmarkItemOutcome;
import com.uniovi.rag.domain.evaluation.BenchmarkKind;
import com.uniovi.rag.application.service.evaluation.RagBenchmarkHumanReasons;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationResultEntity;
import com.uniovi.rag.infrastructure.persistence.jpa.EvaluationRunEntity;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Per-row MVP metrics for exports — computed from persisted {@link EvaluationResultEntity} (+ run fallbacks).
 */
public final class BenchmarkMvpMetricsCalculator {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        boolean ragPreset = BenchmarkKind.RAG_PRESET_END_TO_END.name().equals(kind);
        retrieval.put("applicable", retrievalApplicable);
        if (retrievalApplicable) {
            retrieval.put("recallAt1", dbl(mp.get("recall_at_1")));
            retrieval.put("recallAt3", dblOrDeriveAtN(mp, 3));
            retrieval.put("recallAt5", dblOrDeriveAtN(mp, 5));
            retrieval.put("mrr", dbl(mp.get("mrr")));
            retrieval.put("retrievedCount", intNum(mp.get("retrieved_count")));
            retrieval.put("goldFound", bool(mp.get("gold_found"), deriveGoldFound(mp)));
        } else if (ragPreset) {
            ensureAnalysisComputed(mp, item, run);
            retrieval.put("recallAt1", readAnalysisMetric(mp, "recallAt1"));
            retrieval.put("recallAt3", readAnalysisMetric(mp, "recallAt3"));
            retrieval.put("recallAt5", readAnalysisMetric(mp, "recallAt5"));
            retrieval.put("mrr", readAnalysisMetric(mp, "mrr"));
            retrieval.put("retrievedCount", intNum(mp.get("sourceCount")));
            retrieval.put("goldFound", mp.get("goldDocumentFound"));
            retrieval.put("runtimeCoverageApplicable", true);
            retrieval.put("retrievalCoverageStatus", str(mp.get("retrievalCoverageStatus")));
            retrieval.put("sourceCoverageStatus", str(mp.get("sourceCoverageStatus")));
            retrieval.put("retrievalQualityStatus", str(mp.get("retrievalQualityStatus")));
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
        Object correctness = correctnessMetric(expected, actual, mp);
        generation.put("correctness", correctness);
        generation.put("llmJudgeScore", llmJudgeScore(mp));
        generation.put("hallucinationRate", hallucinationRate(mp));
        generation.put("faithfulness", normalizedJudgeScore(mp, "groundedness"));
        generation.put("sourceSupport", normalizedJudgeScore(mp, "context_sufficiency"));
        generation.put("dateCorrectness", dateCorrectness(mp));

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
        operational.put("embeddingDimensions", embeddingDimensions(mp, run));
        operational.put("embeddingCompatibilityStatus", str(mp.get("embedding_compatibility_status")));
        operational.put("embeddingCompatibilityErrorCode", str(mp.get("embedding_compatibility_error_code")));
        operational.put("embeddingCompatibilityReason", str(mp.get("embedding_compatibility_reason")));
        String presetCode = str(mp.get(BenchmarkResultRowKeys.PRESET_CODE));
        operational.put("presetCode", presetCode);
        operational.put(
                "benchmarkSupportStatus",
                firstNonBlank(
                        str(mp.get("benchmarkSupportStatus")),
                        BenchmarkExportSupport.resolveBenchmarkSupportStatus(presetCode, mp)));
        operational.put("outcome", outcome.isBlank() ? BenchmarkItemOutcome.EXECUTED.name() : outcome);
        operational.put("failureCode", failureCode(outcome, mp));
        operational.put("unsupportedReason", unsupportedReason(outcome, mp));
        operational.put("skipReasonCode", skipReasonCode(outcome, mp));
        operational.put("skipReason", skipReason(outcome, mp));
        operational.put("humanReason", humanReason(outcome, mp));
        operational.put("runPlanVersion", str(mp.get("runPlanVersion")));

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("mvpSchemaVersion", BenchmarkMvpSchema.VERSION);
        root.put("retrieval", retrieval);
        root.put("generation", generation);
        root.put("operational", operational);
        root.put("analysis", buildAnalysisView(mp));
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
        row.put("evaluationRunId", run != null && run.getId() != null ? run.getId().toString() : "");
        row.put(
                "evaluationDatasetId",
                run != null && run.getDataset() != null && run.getDataset().getId() != null
                        ? run.getDataset().getId().toString()
                        : "");
        row.put("evaluationDatasetSha256", run != null && run.getDatasetSha256() != null ? run.getDatasetSha256() : "");
        row.put(
                "projectId",
                run != null && run.getProject() != null && run.getProject().getId() != null
                        ? run.getProject().getId().toString()
                        : "");
        row.put(
                "corpusDocumentSet",
                run != null && run.getProject() != null && run.getProject().getId() != null
                        ? "project:" + run.getProject().getId()
                        : "");
        row.put(
                "resolvedConfigSnapshotId",
                run != null && run.getResolvedConfigSnapshot() != null && run.getResolvedConfigSnapshot().getId() != null
                        ? run.getResolvedConfigSnapshot().getId().toString()
                        : "");
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
        row.put("correctness", csvVal(gen.get("correctness")));
        row.put("llmJudgeScore", csvVal(gen.get("llmJudgeScore")));
        row.put("hallucinationRate", csvVal(gen.get("hallucinationRate")));
        row.put("faithfulness", csvVal(gen.get("faithfulness")));
        row.put("sourceSupport", csvVal(gen.get("sourceSupport")));
        row.put("dateCorrectness", csvVal(gen.get("dateCorrectness")));

        row.put("latencyMs", csvVal(op.get("latencyMs")));
        row.put("modelId", csvVal(op.get("modelId")));
        row.put("embeddingModelId", csvVal(op.get("embeddingModelId")));
        row.put("embeddingDimensions", csvVal(op.get("embeddingDimensions")));
        row.put("embeddingCompatibilityStatus", csvVal(op.get("embeddingCompatibilityStatus")));
        row.put("embeddingCompatibilityErrorCode", csvVal(op.get("embeddingCompatibilityErrorCode")));
        row.put("embeddingCompatibilityReason", csvVal(op.get("embeddingCompatibilityReason")));
        row.put("classifierModelId", run != null && run.getClassifierModelId() != null ? run.getClassifierModelId() : "");
        row.put("presetCode", csvVal(op.get("presetCode")));
        row.put("outcome", csvVal(op.get("outcome")));
        row.put("failureCode", csvVal(op.get("failureCode")));
        row.put("unsupportedReason", csvVal(op.get("unsupportedReason")));
        row.put("skipReasonCode", csvVal(op.get("skipReasonCode")));
        row.put("skipReason", csvVal(op.get("skipReason")));
        row.put("humanReason", csvVal(op.get("humanReason")));
        row.put("runPlanVersion", csvVal(op.get("runPlanVersion")));

        // Embedding retrieval: export gold + retrieved id lists for reproducibility/debugging.
        row.put("retrievalGoldMode", csvVal(mp.get("retrieval_gold_mode")));
        row.put("goldChunkIds", joinIds(mp.get("gold_chunk_ids")));
        row.put("goldDocumentIds", joinIds(mp.get("gold_document_ids")));
        row.put("retrievedChunkIds", joinIds(mp.get("retrieved_chunk_ids")));
        row.put("retrievedDocumentIds", joinIds(mp.get("retrieved_document_ids")));
        RagPresetRetrievalExportSupport.putCsvExportFields(row, mp);
        @SuppressWarnings("unchecked")
        Map<String, Object> analysis = (Map<String, Object>) mvp.get("analysis");
        putAnalysisCsvFields(row, analysis != null && !analysis.isEmpty() ? analysis : mp);

        row.put("groupKey", csvVal(mp.get("groupKey")));
        row.put("indexCompatibilityStatus", csvVal(mp.get("indexCompatibilityStatus")));
        row.put("requiresReindex", csvVal(mp.get("requiresReindex")));
        row.put("indexSnapshotId", csvVal(mp.get("indexSnapshotId")));
        row.put("indexProfileHash", csvVal(mp.get("indexProfileHash")));
        row.put("effectiveGroupSnapshotId", csvVal(mp.get("effectiveGroupSnapshotId")));
        row.put("groupIndexProfileHash", csvVal(mp.get("groupIndexProfileHash")));
        row.put("reindexAction", csvVal(mp.get("reindexAction")));
        row.put("reindexStatus", csvVal(mp.get("reindexStatus")));
        row.put("forcedSnapshotSelection", csvVal(mp.get("forcedSnapshotSelection")));
        row.put("reindexEventId", csvVal(mp.get("reindexEventId")));
        row.put("reindexStartedAt", csvVal(mp.get("reindexStartedAt")));
        row.put("reindexCompletedAt", csvVal(mp.get("reindexCompletedAt")));
        row.put("reindexErrorCode", csvVal(mp.get("reindexErrorCode")));
        row.put("reindexErrorReason", csvVal(mp.get("reindexErrorReason")));
        row.put("presetIndexRequirements", jsonCell(mp.get("presetIndexRequirements")));
        row.put("activeSnapshotCapabilities", jsonCell(mp.get("activeSnapshotCapabilities")));

        row.put("presetLabel", firstNonBlank(str(mp.get("presetLabel")), str(mp.get(BenchmarkResultRowKeys.PRESET_LABEL))));
        row.put("productPresetId", csvVal(mp.get("productPresetId")));
        putPresetLadderCsvFields(row, mp);
        row.put("workflowName", csvVal(mp.get("workflowName")));
        row.put("activeFeatures", jsonCell(mp.get("activeFeatures")));
        row.put("useRetrieval", csvVal(mp.get("useRetrieval")));
        row.put("naiveFullCorpusInPromptEnabled", csvVal(mp.get("naiveFullCorpusInPromptEnabled")));
        row.put("materializationStrategy", csvVal(mp.get("materializationStrategy")));
        row.put("metadataEnabled", csvVal(mp.get("metadataEnabled")));
        row.put("expansionEnabled", csvVal(mp.get("expansionEnabled")));
        row.put("nerEnabled", csvVal(mp.get("nerEnabled")));
        row.put("reasoningEnabled", csvVal(mp.get("reasoningEnabled")));
        row.put("toolsEnabled", csvVal(mp.get("toolsEnabled")));
        row.put("functionCallingEnabled", csvVal(mp.get("functionCallingEnabled")));
        row.put("rankerEnabled", csvVal(mp.get("rankerEnabled")));
        row.put("postRetrievalEnabled", csvVal(mp.get("postRetrievalEnabled")));
        row.put("useAdvisor", csvVal(mp.get("useAdvisor")));
        row.put("adaptiveRoutingEnabled", csvVal(mp.get("adaptiveRoutingEnabled")));
        row.put("judgeEnabled", csvVal(mp.get("judgeEnabled")));
        row.put("clarificationEnabled", csvVal(mp.get("clarificationEnabled")));
        row.put("memoryEnabled", csvVal(mp.get("memoryEnabled")));
        row.put("corpusRequired", csvVal(mp.get("corpusRequired")));
        row.put("corpusAvailable", csvVal(mp.get("corpusAvailable")));
        row.put("corpusChars", csvVal(mp.get("corpusChars")));
        row.put("corpusTruncated", csvVal(mp.get("corpusTruncated")));
        row.put("selectedSnapshotIds", joinIds(mp.get("selectedSnapshotIds")));
        row.put("groundingPolicy", csvVal(mp.get("groundingPolicy")));
        row.put("timestamp", item.getEvaluatedAt() != null ? item.getEvaluatedAt().toString() : "");
        return row;
    }

    private static void putPresetLadderCsvFields(Map<String, String> row, Map<String, Object> mp) {
        row.put("protocolStageIndex", csvVal(mp.get("protocolStageIndex")));
        row.put("presetStage", csvVal(mp.get("presetStage")));
        row.put("presetLadderScope", csvVal(mp.get("presetLadderScope")));
        row.put("requiresMultiTurn", csvVal(mp.get("requiresMultiTurn")));
        row.put("singleTurnBenchmarkSelectable", csvVal(mp.get("singleTurnBenchmarkSelectable")));
        row.put("comparableSingleTurnMetric", csvVal(mp.get("comparableSingleTurnMetric")));
        String presetCode = str(mp.get(BenchmarkResultRowKeys.PRESET_CODE));
        row.put(
                "benchmarkSupportStatus",
                csvVal(
                        firstNonBlank(
                                str(mp.get("benchmarkSupportStatus")),
                                BenchmarkExportSupport.resolveBenchmarkSupportStatus(presetCode, mp))));
    }

    private static String jsonCell(Object o) {
        if (o == null) {
            return "";
        }
        if (o instanceof String s) {
            return s;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    private static Map<String, Object> payload(EvaluationResultEntity item) {
        Map<String, Object> raw = item.getMetricsPayload();
        if (raw == null || raw.isEmpty()) {
            return new LinkedHashMap<>();
        }
        return raw instanceof LinkedHashMap<String, Object> linked ? linked : new LinkedHashMap<>(raw);
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

    private static Object correctnessMetric(String expected, String actual, Map<String, Object> mp) {
        if (normalizedExactMatch(expected, actual)) {
            return 1.0;
        }
        Object judge = normalizedJudgeScore(mp, "correctness");
        return judge instanceof Number ? judge : BenchmarkMvpSchema.NOT_AVAILABLE;
    }

    private static Object llmJudgeScore(Map<String, Object> mp) {
        Object js = mp.get("judge_scores");
        if (!(js instanceof Map<?, ?> m)) {
            return BenchmarkMvpSchema.NOT_AVAILABLE;
        }
        double sum = 0.0;
        int count = 0;
        for (String key : List.of("correctness", "context_sufficiency", "relevance", "independence", "groundedness")) {
            Object raw = m.get(key);
            if (raw instanceof Number n) {
                sum += clampJudge(n.doubleValue()) / 5.0;
                count++;
            }
        }
        return count > 0 ? sum / count : BenchmarkMvpSchema.NOT_AVAILABLE;
    }

    private static Object hallucinationRate(Map<String, Object> mp) {
        Object faithfulness = normalizedJudgeScore(mp, "groundedness");
        if (faithfulness instanceof Number n) {
            return 1.0 - n.doubleValue();
        }
        return BenchmarkMvpSchema.NOT_AVAILABLE;
    }

    private static Object dateCorrectness(Map<String, Object> mp) {
        String requestedDate = str(mp.get("requestedDate"));
        if (requestedDate.isBlank()) {
            return BenchmarkMvpSchema.NOT_AVAILABLE;
        }
        if (boolObj(mp.get("exactDateMatch"))) {
            return 1.0;
        }
        if (boolObj(mp.get("dateMismatchDetected"))) {
            return boolObj(mp.get("abstentionTriggered")) ? 1.0 : 0.0;
        }
        return BenchmarkMvpSchema.NOT_AVAILABLE;
    }

    private static Object normalizedJudgeScore(Map<String, Object> mp, String key) {
        Object js = mp.get("judge_scores");
        if (!(js instanceof Map<?, ?> m)) {
            return BenchmarkMvpSchema.NOT_AVAILABLE;
        }
        Object raw = m.get(key);
        if (!(raw instanceof Number n)) {
            return BenchmarkMvpSchema.NOT_AVAILABLE;
        }
        return clampJudge(n.doubleValue()) / 5.0;
    }

    private static double clampJudge(double value) {
        return Math.max(1.0, Math.min(5.0, value));
    }

    private static boolean boolObj(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return false;
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
        String human = humanReason(outcome, mp);
        if (!human.isBlank()) {
            return human;
        }
        String code = str(mp.get(BenchmarkResultRowKeys.ERROR_CODE));
        return code.isBlank() ? RagBenchmarkHumanReasons.humanize("NOT_SUPPORTED") : RagBenchmarkHumanReasons.humanize(code);
    }

    private static String humanReason(String outcome, Map<String, Object> mp) {
        String fromMetrics = str(mp.get("humanReason"));
        if (!fromMetrics.isBlank()) {
            return truncate(fromMetrics, 200);
        }
        if (BenchmarkItemOutcome.SKIPPED.name().equals(outcome)) {
            return skipReason(outcome, mp);
        }
        if (BenchmarkItemOutcome.NOT_SUPPORTED.name().equals(outcome)) {
            String reason = str(mp.get(BenchmarkResultRowKeys.REASON));
            if (!reason.isBlank()) {
                return truncate(reason, 200);
            }
            String code = str(mp.get(BenchmarkResultRowKeys.ERROR_CODE));
            return code.isBlank() ? "" : RagBenchmarkHumanReasons.humanize(code);
        }
        return "";
    }

    private static String skipReasonCode(String outcome, Map<String, Object> mp) {
        if (!BenchmarkItemOutcome.SKIPPED.name().equals(outcome)) {
            return "";
        }
        String fromMetrics = str(mp.get("skippedReasonCode"));
        if (!fromMetrics.isBlank()) {
            return fromMetrics;
        }
        String code = str(mp.get(BenchmarkResultRowKeys.ERROR_CODE));
        return code.isBlank() ? "SKIPPED" : code;
    }

    private static String skipReason(String outcome, Map<String, Object> mp) {
        if (!BenchmarkItemOutcome.SKIPPED.name().equals(outcome)) {
            return "";
        }
        String fromMetrics = str(mp.get("skippedReason"));
        if (!fromMetrics.isBlank()) {
            return truncate(fromMetrics, 200);
        }
        String reason = str(mp.get(BenchmarkResultRowKeys.REASON));
        if (!reason.isBlank()) {
            return truncate(reason, 200);
        }
        String code = str(mp.get(BenchmarkResultRowKeys.ERROR_CODE));
        return code.isBlank() ? "SKIPPED" : code;
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

    private static Object embeddingDimensions(Map<String, Object> mp, EvaluationRunEntity run) {
        Object raw = mp.get("embedding_dimensions");
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (run != null && run.getEmbeddingDimensions() != null) {
            return run.getEmbeddingDimensions();
        }
        return "";
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

    public static boolean normalizedExactMatch(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return normalizeAnswer(expected).equals(normalizeAnswer(actual));
    }

    public static boolean containsExpectedAnswer(String expected, String actual) {
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
        if (ret == null) {
            return Optional.empty();
        }
        boolean embeddingApplicable = Boolean.TRUE.equals(ret.get("applicable"));
        boolean ragGoldApplicable =
                Boolean.TRUE.equals(ret.get("runtimeCoverageApplicable"))
                        && RetrievalQualityStatus.COMPUTED.name().equals(str(ret.get("retrievalQualityStatus")));
        if (!embeddingApplicable && !ragGoldApplicable) {
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

    @SuppressWarnings("unchecked")
    public static Map<String, Object> analysis(Map<String, Object> mvpRoot) {
        return (Map<String, Object>) mvpRoot.get("analysis");
    }

    private static void ensureAnalysisComputed(
            Map<String, Object> mp, EvaluationResultEntity item, EvaluationRunEntity run) {
        boolean hadExpected = mp.containsKey(DatasetMetricContract.KEY_QUERY_TYPE_EXPECTED);
        DatasetMetricContract.ensureQueryTypeExpected(mp, item.getQueryType());
        boolean needsAnalysis =
                !mp.containsKey("analysisVersion")
                        || (!hadExpected && mp.containsKey(DatasetMetricContract.KEY_QUERY_TYPE_EXPECTED));
        if (!needsAnalysis) {
            return;
        }
        RagExperimentalPresetCode preset = parsePreset(str(mp.get(BenchmarkResultRowKeys.PRESET_CODE)));
        String expected = item.getExpectedAnswer() != null ? item.getExpectedAnswer() : "";
        String actual = item.getActualAnswer() != null ? item.getActualAnswer() : "";
        RagPresetAnalysisMetrics.computeAndMerge(mp, expected, actual, preset);
    }

    private static RagExperimentalPresetCode parsePreset(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return RagExperimentalPresetCode.tryParse(code).orElse(null);
    }

    private static Object readAnalysisMetric(Map<String, Object> mp, String key) {
        if (!RetrievalQualityStatus.COMPUTED.name().equals(str(mp.get("retrievalQualityStatus")))) {
            return BenchmarkMvpSchema.NOT_AVAILABLE;
        }
        Object v = mp.get(key);
        return v instanceof Number ? v : BenchmarkMvpSchema.NOT_AVAILABLE;
    }

    private static Map<String, Object> buildAnalysisView(Map<String, Object> mp) {
        ScoreExportSupport.mergeAvailabilityFields(mp);
        Map<String, Object> out = new LinkedHashMap<>();
        for (String key :
                List.of(
                        "presetKey",
                        "presetLabel",
                        "workflowName",
                        "queryTypeExpected",
                        "queryTypePredicted",
                        "queryTypeMatch",
                        DatasetMetricContract.KEY_ANSWERABILITY,
                        DatasetMetricContract.KEY_ANSWERABILITY_SOURCE,
                        DatasetMetricContract.KEY_EXPECTED_ANSWER_PRESENT,
                        AbstentionDetector.KEY_ABSTAINED,
                        AbstentionDetector.KEY_ABSTENTION_REASON,
                        RagPresetAnalysisMetrics.KEY_ABSTENTION_CORRECTNESS,
                        RagPresetAnalysisMetrics.KEY_ABSTENTION_SCORE,
                        "finalScore",
                        "scoreFinal",
                        "structuredScore",
                        "structuredScoreStatus",
                        "semanticScore",
                        "exactMatchNormalized",
                        "expectedAnswerContained",
                        "countMatch",
                        "booleanMatch",
                        "dateMatch",
                        "durationMatch",
                        "fieldMatchScore",
                        "entityPrecision",
                        "entityRecall",
                        "entityF1",
                        "listPrecision",
                        "listRecall",
                        "listF1",
                        "sourceSupport",
                        "faithfulness",
                        "answerRelevance",
                        RagPresetAnalysisMetrics.KEY_SCORE_UNAVAILABLE_REASON,
                        "finalScoreAvailable",
                        "finalScoreStatus",
                        "retrievalCoverageStatus",
                        "sourceCoverageStatus",
                        "retrievalQualityStatus",
                        "retrievalDenseCandidateCount",
                        "retrievalAfterFilterCount",
                        "contextChunkCount",
                        "promptContextCharCount",
                        "sourceCount",
                        "recallAt1",
                        "recallAt3",
                        "recallAt5",
                        "recallAtK",
                        "mrr",
                        "ndcgAt5",
                        "classifierStatus",
                        "classifierConfidence",
                        "classifierModelId",
                        "classifierLabelSetHash",
                        "classifierFallback",
                        "classifierFallbackReason",
                        RagPresetClassifierMetrics.KEY_ROUTE_SUPPRESSED_BY_CLASSIFIER,
                        RagPresetClassifierMetrics.KEY_ROUTE_SUPPRESSED_REASON,
                        RagPresetClassifierMetrics.KEY_HEURISTIC_ROUTE_USED,
                        "correctAbstention",
                        "wrongAbstention",
                        RagPresetToolMetrics.KEY_TOOL_APPLICABLE,
                        RagPresetToolMetrics.KEY_TOOL_SELECTED,
                        RagPresetToolMetrics.KEY_TOOL_NAME,
                        RagPresetToolMetrics.KEY_TOOL_EXECUTED,
                        RagPresetToolMetrics.KEY_TOOL_SUCCEEDED,
                        RagPresetToolMetrics.KEY_TOOL_FALLBACK_REASON,
                        RagPresetToolMetrics.KEY_TOOL_RESULT_USED_AS_FINAL,
                        RagPresetToolMetrics.KEY_DETERMINISTIC_TOOL_ROUTE,
                        RagPresetToolMetrics.KEY_FUNCTION_CALLING_USED,
                        RagPresetToolMetrics.KEY_FUNCTION_CALL_ATTEMPTED,
                        RagPresetToolMetrics.KEY_FUNCTION_CALL_NAME,
                        RagPresetToolMetrics.KEY_FUNCTION_CALL_ARGUMENTS_VALID,
                        RagPresetToolMetrics.KEY_FUNCTION_CALL_SUCCEEDED,
                        RagPresetToolMetrics.KEY_FUNCTION_CALL_FALLBACK_REASON,
                        RagPresetToolMetrics.KEY_FUNCTION_RESULT_USED_AS_FINAL,
                        RagPresetToolMetrics.KEY_FUNCTION_RESULT_USED_AS_CONTEXT,
                        RagPresetToolMetrics.KEY_FUNCTION_CALL_ROUTE,
                        RagPresetToolMetrics.KEY_EXECUTION_ROUTE,
                        RagPresetToolMetrics.KEY_QUERY_TYPE_SOURCE,
                        RagPresetToolMetrics.KEY_TOOL_COVERAGE_STATUS,
                        RagPresetToolMetrics.KEY_ROUTING_ROUTE_KIND,
                        RagPresetAdvisorMetrics.KEY_ADVISOR_ENABLED,
                        RagPresetAdvisorMetrics.KEY_ADVISOR_ROUTE,
                        RagPresetAdvisorMetrics.KEY_ADVISOR_ATTEMPTED,
                        RagPresetAdvisorMetrics.KEY_ADVISOR_APPLIED,
                        RagPresetAdvisorMetrics.KEY_ADVISOR_NAME,
                        RagPresetAdvisorMetrics.KEY_ADVISOR_TYPE,
                        RagPresetAdvisorMetrics.KEY_ADVISOR_CONTRIBUTION_TYPE,
                        RagPresetAdvisorMetrics.KEY_ADVISOR_CHANGED_QUERY,
                        RagPresetAdvisorMetrics.KEY_ADVISOR_CHANGED_CONTEXT,
                        RagPresetAdvisorMetrics.KEY_ADVISOR_CHANGED_PROMPT,
                        RagPresetAdvisorMetrics.KEY_ADVISOR_VALIDATED_ANSWER,
                        RagPresetAdvisorMetrics.KEY_ADVISOR_FALLBACK_REASON,
                        RagPresetAdvisorMetrics.KEY_ADVISOR_RESULT_USED,
                        RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_APPLIED,
                        RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_STRATEGY,
                        RagPresetAdvancedRetrievalMetrics.KEY_DENSE_CANDIDATE_COUNT,
                        RagPresetAdvancedRetrievalMetrics.KEY_SPARSE_CANDIDATE_COUNT,
                        RagPresetAdvancedRetrievalMetrics.KEY_HYBRID_CANDIDATE_COUNT,
                        RagPresetAdvancedRetrievalMetrics.KEY_MERGED_CANDIDATE_COUNT,
                        RagPresetAdvancedRetrievalMetrics.KEY_DEDUPED_CANDIDATE_COUNT,
                        RagPresetAdvancedRetrievalMetrics.KEY_RERANKED_CANDIDATE_COUNT,
                        RagPresetAdvancedRetrievalMetrics.KEY_FINAL_CONTEXT_CHUNK_COUNT,
                        RagPresetAdvancedRetrievalMetrics.KEY_RERANK_APPLIED,
                        RagPresetAdvancedRetrievalMetrics.KEY_RERANK_CHANGED_ORDER,
                        RagPresetAdvancedRetrievalMetrics.KEY_COMPRESSION_APPLIED,
                        RagPresetAdvancedRetrievalMetrics.KEY_COMPRESSED_CONTEXT_CHAR_COUNT,
                        RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_FALLBACK_REASON,
                        RagPresetAdvancedRetrievalMetrics.KEY_CANDIDATE_ORIGINS,
                        RagPresetAdvancedRetrievalMetrics.KEY_SPARSE_RETRIEVAL_STATUS,
                        RagPresetAdvancedRetrievalMetrics.KEY_HYBRID_APPLIED)) {
            if (mp.containsKey(key)) {
                out.put(key, mp.get(key));
            }
        }
        return out;
    }

    private static void putAnalysisCsvFields(Map<String, String> row, Map<String, Object> mp) {
        row.put("answerability", csvVal(mp.get(DatasetMetricContract.KEY_ANSWERABILITY)));
        row.put("answerabilitySource", csvVal(mp.get(DatasetMetricContract.KEY_ANSWERABILITY_SOURCE)));
        row.put("expectedAnswerPresent", csvVal(mp.get(DatasetMetricContract.KEY_EXPECTED_ANSWER_PRESENT)));
        row.put("queryTypeExpected", csvVal(mp.get("queryTypeExpected")));
        row.put("queryTypePredicted", csvVal(mp.get("queryTypePredicted")));
        row.put("queryTypeMatch", csvVal(mp.get("queryTypeMatch")));
        row.put("abstained", csvVal(firstNonNull(mp.get(AbstentionDetector.KEY_ABSTAINED), mp.get("abstained"))));
        row.put("abstentionReason", csvVal(mp.get(AbstentionDetector.KEY_ABSTENTION_REASON)));
        row.put("abstentionCorrectness", csvVal(mp.get(RagPresetAnalysisMetrics.KEY_ABSTENTION_CORRECTNESS)));
        row.put("finalScore", ScoreExportSupport.formatFinalScoreForCsv(mp));
        row.put("structuredScore", csvVal(mp.get("structuredScore")));
        row.put("structuredScoreStatus", csvVal(mp.get("structuredScoreStatus")));
        row.put("analysisSemanticScore", csvVal(mp.get("semanticScore")));
        row.put("abstentionScore", csvVal(mp.get(RagPresetAnalysisMetrics.KEY_ABSTENTION_SCORE)));
        row.put("exactMatchNormalized", csvVal(mp.get("exactMatchNormalized")));
        row.put("expectedAnswerContained", csvVal(mp.get("expectedAnswerContained")));
        row.put("countMatch", csvVal(mp.get("countMatch")));
        row.put("booleanMatch", csvVal(mp.get("booleanMatch")));
        row.put("dateMatch", csvVal(mp.get("dateMatch")));
        row.put("durationMatch", csvVal(mp.get("durationMatch")));
        row.put("fieldMatchScore", csvVal(mp.get("fieldMatchScore")));
        row.put("entityPrecision", csvVal(mp.get("entityPrecision")));
        row.put("entityRecall", csvVal(mp.get("entityRecall")));
        row.put("entityF1", csvVal(mp.get("entityF1")));
        row.put("listPrecision", csvVal(mp.get("listPrecision")));
        row.put("listRecall", csvVal(mp.get("listRecall")));
        row.put("listF1", csvVal(mp.get("listF1")));
        row.put("faithfulness", csvVal(mp.get("faithfulness")));
        row.put("answerRelevance", csvVal(mp.get("answerRelevance")));
        row.put("scoreUnavailableReason", csvVal(mp.get(RagPresetAnalysisMetrics.KEY_SCORE_UNAVAILABLE_REASON)));
        row.put("finalScoreAvailable", csvVal(ScoreExportSupport.isFinalScoreAvailable(mp)));
        row.put("finalScoreStatus", csvVal(ScoreExportSupport.finalScoreStatus(mp)));
        row.put("retrievalCoverageStatus", csvVal(mp.get("retrievalCoverageStatus")));
        row.put("retrievalQualityStatus", csvVal(mp.get("retrievalQualityStatus")));
        row.put("recallAtK", csvVal(firstNonNull(mp.get("recallAtK"), mp.get("recallAt5"))));
        row.put("ndcg", csvVal(mp.get("ndcgAt5")));
        row.put("classifierStatus", csvVal(mp.get("classifierStatus")));
        row.put("classifierConfidence", csvVal(mp.get("classifierConfidence")));
        row.put("classifierModelId", csvVal(firstNonNull(mp.get("classifierModelId"), mp.get("classifierModelIdUsed"))));
        row.put("classifierLabelSetHash", csvVal(mp.get("classifierLabelSetHash")));
        row.put("classifierFallback", csvVal(mp.get("classifierFallback")));
        row.put("classifierFallbackReason", csvVal(mp.get("classifierFallbackReason")));
        row.put(
                RagPresetClassifierMetrics.KEY_ROUTE_SUPPRESSED_BY_CLASSIFIER,
                csvVal(mp.get(RagPresetClassifierMetrics.KEY_ROUTE_SUPPRESSED_BY_CLASSIFIER)));
        row.put(
                RagPresetClassifierMetrics.KEY_ROUTE_SUPPRESSED_REASON,
                csvVal(mp.get(RagPresetClassifierMetrics.KEY_ROUTE_SUPPRESSED_REASON)));
        row.put(
                RagPresetClassifierMetrics.KEY_HEURISTIC_ROUTE_USED,
                csvVal(mp.get(RagPresetClassifierMetrics.KEY_HEURISTIC_ROUTE_USED)));
        row.put(RagPresetToolMetrics.KEY_DETERMINISTIC_TOOL_ROUTE, csvVal(mp.get(RagPresetToolMetrics.KEY_DETERMINISTIC_TOOL_ROUTE)));
        row.put(RagPresetToolMetrics.KEY_FUNCTION_CALLING_USED, csvVal(mp.get(RagPresetToolMetrics.KEY_FUNCTION_CALLING_USED)));
        row.put(RagPresetToolMetrics.KEY_FUNCTION_CALL_ATTEMPTED, csvVal(mp.get(RagPresetToolMetrics.KEY_FUNCTION_CALL_ATTEMPTED)));
        row.put(RagPresetToolMetrics.KEY_FUNCTION_CALL_NAME, csvVal(mp.get(RagPresetToolMetrics.KEY_FUNCTION_CALL_NAME)));
        row.put(
                RagPresetToolMetrics.KEY_FUNCTION_CALL_ARGUMENTS_VALID,
                csvVal(mp.get(RagPresetToolMetrics.KEY_FUNCTION_CALL_ARGUMENTS_VALID)));
        row.put(RagPresetToolMetrics.KEY_FUNCTION_CALL_SUCCEEDED, csvVal(mp.get(RagPresetToolMetrics.KEY_FUNCTION_CALL_SUCCEEDED)));
        row.put(
                RagPresetToolMetrics.KEY_FUNCTION_CALL_FALLBACK_REASON,
                csvVal(mp.get(RagPresetToolMetrics.KEY_FUNCTION_CALL_FALLBACK_REASON)));
        row.put(
                RagPresetToolMetrics.KEY_FUNCTION_RESULT_USED_AS_FINAL,
                csvVal(mp.get(RagPresetToolMetrics.KEY_FUNCTION_RESULT_USED_AS_FINAL)));
        row.put(
                RagPresetToolMetrics.KEY_FUNCTION_RESULT_USED_AS_CONTEXT,
                csvVal(mp.get(RagPresetToolMetrics.KEY_FUNCTION_RESULT_USED_AS_CONTEXT)));
        row.put(RagPresetToolMetrics.KEY_FUNCTION_CALL_ROUTE, csvVal(mp.get(RagPresetToolMetrics.KEY_FUNCTION_CALL_ROUTE)));
        row.put(RagPresetToolMetrics.KEY_EXECUTION_ROUTE, csvVal(mp.get(RagPresetToolMetrics.KEY_EXECUTION_ROUTE)));
        row.put(
                RagPresetToolMetrics.KEY_ROUTING_ROUTE_KIND,
                csvVal(
                        firstNonNull(
                                mp.get(RagPresetToolMetrics.KEY_ROUTING_ROUTE_KIND),
                                mp.get(RagPresetToolMetrics.KEY_EXECUTION_ROUTE))));
        row.put(RagPresetToolMetrics.KEY_TOOL_APPLICABLE, csvVal(mp.get(RagPresetToolMetrics.KEY_TOOL_APPLICABLE)));
        row.put(RagPresetToolMetrics.KEY_TOOL_SELECTED, csvVal(mp.get(RagPresetToolMetrics.KEY_TOOL_SELECTED)));
        row.put(RagPresetToolMetrics.KEY_TOOL_NAME, csvVal(mp.get(RagPresetToolMetrics.KEY_TOOL_NAME)));
        row.put(RagPresetToolMetrics.KEY_TOOL_EXECUTED, csvVal(mp.get(RagPresetToolMetrics.KEY_TOOL_EXECUTED)));
        row.put(RagPresetToolMetrics.KEY_TOOL_SUCCEEDED, csvVal(mp.get(RagPresetToolMetrics.KEY_TOOL_SUCCEEDED)));
        row.put(RagPresetToolMetrics.KEY_TOOL_FALLBACK_REASON, csvVal(mp.get(RagPresetToolMetrics.KEY_TOOL_FALLBACK_REASON)));
        row.put(RagPresetToolMetrics.KEY_TOOL_RESULT_USED_AS_FINAL, csvVal(mp.get(RagPresetToolMetrics.KEY_TOOL_RESULT_USED_AS_FINAL)));
        row.put(RagPresetToolMetrics.KEY_QUERY_TYPE_SOURCE, csvVal(mp.get(RagPresetToolMetrics.KEY_QUERY_TYPE_SOURCE)));
        row.put(RagPresetToolMetrics.KEY_TOOL_COVERAGE_STATUS, csvVal(mp.get(RagPresetToolMetrics.KEY_TOOL_COVERAGE_STATUS)));
        row.put(RagPresetAdvisorMetrics.KEY_ADVISOR_ENABLED, csvVal(mp.get(RagPresetAdvisorMetrics.KEY_ADVISOR_ENABLED)));
        row.put(RagPresetAdvisorMetrics.KEY_ADVISOR_ROUTE, csvVal(mp.get(RagPresetAdvisorMetrics.KEY_ADVISOR_ROUTE)));
        row.put(RagPresetAdvisorMetrics.KEY_ADVISOR_ATTEMPTED, csvVal(mp.get(RagPresetAdvisorMetrics.KEY_ADVISOR_ATTEMPTED)));
        row.put(RagPresetAdvisorMetrics.KEY_ADVISOR_APPLIED, csvVal(mp.get(RagPresetAdvisorMetrics.KEY_ADVISOR_APPLIED)));
        row.put(RagPresetAdvisorMetrics.KEY_ADVISOR_NAME, csvVal(mp.get(RagPresetAdvisorMetrics.KEY_ADVISOR_NAME)));
        row.put(RagPresetAdvisorMetrics.KEY_ADVISOR_TYPE, csvVal(mp.get(RagPresetAdvisorMetrics.KEY_ADVISOR_TYPE)));
        row.put(
                RagPresetAdvisorMetrics.KEY_ADVISOR_CONTRIBUTION_TYPE,
                csvVal(mp.get(RagPresetAdvisorMetrics.KEY_ADVISOR_CONTRIBUTION_TYPE)));
        row.put(RagPresetAdvisorMetrics.KEY_ADVISOR_CHANGED_QUERY, csvVal(mp.get(RagPresetAdvisorMetrics.KEY_ADVISOR_CHANGED_QUERY)));
        row.put(RagPresetAdvisorMetrics.KEY_ADVISOR_CHANGED_CONTEXT, csvVal(mp.get(RagPresetAdvisorMetrics.KEY_ADVISOR_CHANGED_CONTEXT)));
        row.put(RagPresetAdvisorMetrics.KEY_ADVISOR_CHANGED_PROMPT, csvVal(mp.get(RagPresetAdvisorMetrics.KEY_ADVISOR_CHANGED_PROMPT)));
        row.put(
                RagPresetAdvisorMetrics.KEY_ADVISOR_VALIDATED_ANSWER,
                csvVal(mp.get(RagPresetAdvisorMetrics.KEY_ADVISOR_VALIDATED_ANSWER)));
        row.put(
                RagPresetAdvisorMetrics.KEY_ADVISOR_FALLBACK_REASON,
                csvVal(mp.get(RagPresetAdvisorMetrics.KEY_ADVISOR_FALLBACK_REASON)));
        row.put(RagPresetAdvisorMetrics.KEY_ADVISOR_RESULT_USED, csvVal(mp.get(RagPresetAdvisorMetrics.KEY_ADVISOR_RESULT_USED)));
    }

    private static Object firstNonNull(Object a, Object b) {
        return a != null ? a : b;
    }
}
