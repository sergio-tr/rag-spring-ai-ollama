package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.application.service.evaluation.metrics.matching.ExpectedAnswerMatchCalibrator;
import com.uniovi.rag.application.service.runtime.routing.CompositionRouteTelemetryMapper;
import com.uniovi.rag.application.service.evaluation.metrics.matching.ExpectedAnswerMatchResult;
import com.uniovi.rag.application.service.evaluation.metrics.querytype.QueryTypeEvaluatorRegistry;
import com.uniovi.rag.application.service.evaluation.metrics.querytype.StructuredEvaluationResult;
import com.uniovi.rag.application.service.evaluation.preset.ExperimentalPresetCanonicalCatalog;
import com.uniovi.rag.domain.evaluation.workbook.RagExperimentalPresetCode;
import com.uniovi.rag.domain.model.QueryType;
import java.util.LinkedHashMap;
import java.util.Map;

/** Computes analysis fields for preset evaluation rows from persisted metrics and answers. */
public final class RagPresetAnalysisMetrics {

    public static final String ANALYSIS_VERSION = "2";
    public static final String KEY_SCORE_UNAVAILABLE_REASON = "scoreUnavailableReason";
    public static final String KEY_ABSTENTION_CORRECTNESS = "abstentionCorrectness";
    public static final String KEY_ABSTENTION_SCORE = "abstentionScore";

    private RagPresetAnalysisMetrics() {}

    public static void computeAndMerge(
            Map<String, Object> metrics,
            String expectedAnswer,
            String actualAnswer,
            RagExperimentalPresetCode preset) {
        if (metrics == null) {
            return;
        }
        Map<String, Object> analysis = compute(metrics, expectedAnswer, actualAnswer, preset);
        metrics.putAll(analysis);
        metrics.put("analysisVersion", ANALYSIS_VERSION);
    }

    public static Map<String, Object> compute(
            Map<String, Object> metrics,
            String expectedAnswer,
            String actualAnswer,
            RagExperimentalPresetCode preset) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> mp = metrics != null ? metrics : Map.of();

        DatasetMetricContract.ensureQueryTypeExpected(mp, str(mp.get("query_type")));

        String presetKey = str(mp.get("presetCode"));
        if (!presetKey.isBlank()) {
            out.put("presetKey", presetKey);
        }
        String presetLabel = str(mp.get("presetLabel"));
        if (!presetLabel.isBlank()) {
            out.put("presetLabel", presetLabel);
        }
        String workflow = str(mp.get("workflowName"));
        if (!workflow.isBlank()) {
            out.put("workflowName", workflow);
        }

        Answerability answerability = DatasetMetricContract.readAnswerability(mp);
        out.put(DatasetMetricContract.KEY_ANSWERABILITY, answerability.name());
        copyIfPresent(out, mp, DatasetMetricContract.KEY_ANSWERABILITY_SOURCE);
        if (!mp.containsKey(DatasetMetricContract.KEY_ANSWERABILITY_SOURCE)) {
            out.put(DatasetMetricContract.KEY_ANSWERABILITY_SOURCE, AnswerabilitySource.DEFAULT_UNKNOWN.name());
        }
        copyIfPresent(out, mp, DatasetMetricContract.KEY_ANSWERABILITY_RULE_ID);
        copyIfPresent(out, mp, DatasetMetricContract.KEY_ANSWERABILITY_CONFIDENCE);
        copyIfPresent(out, mp, DatasetMetricContract.KEY_ANSWERABILITY_RULES_VERSION);
        copyIfPresent(out, mp, DatasetMetricContract.KEY_LABELLED_DATASET_SHA256);
        copyIfPresent(out, mp, DatasetMetricContract.KEY_REVIEW_REQUIRED);
        copyIfPresent(out, mp, "subsetId");
        copyIfPresent(out, mp, "subsetName");
        copyIfPresent(out, mp, "subsetVersion");
        out.put(
                DatasetMetricContract.KEY_EXPECTED_ANSWER_PRESENT,
                mp.containsKey(DatasetMetricContract.KEY_EXPECTED_ANSWER_PRESENT)
                        ? mp.get(DatasetMetricContract.KEY_EXPECTED_ANSWER_PRESENT)
                        : expectedAnswer != null && !expectedAnswer.isBlank());

        AbstentionDetector.Result abstention = AbstentionDetector.detect(mp, actualAnswer);
        out.put(AbstentionDetector.KEY_ABSTAINED, abstention.abstained());
        if (!abstention.reason().isBlank()) {
            out.put(AbstentionDetector.KEY_ABSTENTION_REASON, abstention.reason());
        }
        out.put(AbstentionDetector.KEY_ABSTENTION_SOURCE, abstention.source());

        boolean exact = BenchmarkMvpMetricsCalculator.normalizedExactMatch(expectedAnswer, actualAnswer);
        boolean contained = BenchmarkMvpMetricsCalculator.containsExpectedAnswer(expectedAnswer, actualAnswer);
        out.put("exactMatchNormalized", exact);
        out.put("expectedAnswerContained", contained);

        Object semantic = semanticScore(mp);
        Object faithfulness = judgeScore(mp, "groundedness");
        Object answerRelevance = judgeScore(mp, "relevance");
        Object sourceSupport = sourceSupportScore(mp);

        out.put("semanticScore", exportScore(semantic));
        out.put("faithfulness", exportScore(faithfulness));
        out.put("answerRelevance", exportScore(answerRelevance));
        out.put("sourceSupport", exportScore(sourceSupport));

        QueryType expectedType = parseQueryType(str(mp.get(DatasetMetricContract.KEY_QUERY_TYPE_EXPECTED)));
        if (expectedType == null) {
            expectedType = parseQueryType(str(mp.get("queryTypeExpected")));
        }
        if (expectedType != null) {
            out.put("queryTypeExpected", expectedType.name());
        }

        String predictedLabel = firstNonBlank(str(mp.get("classifierLabel")), str(mp.get("queryTypePredicted")));
        if (!predictedLabel.isBlank() && !"UNCLASSIFIED".equalsIgnoreCase(predictedLabel)) {
            out.put("queryTypePredicted", predictedLabel);
        }
        String classifierStatus = str(mp.get("classifierStatus"));
        if (!classifierStatus.isBlank()) {
            out.put("classifierStatus", classifierStatus);
        }
        if (bool(mp.get("classifierFallback"))) {
            out.put("classifierFallback", true);
            String reason = str(mp.get("classifierFallbackReason"));
            if (!reason.isBlank()) {
                out.put("classifierFallbackReason", reason);
            }
        }
        copyIfPresent(out, mp, RagPresetClassifierMetrics.KEY_CLASSIFIER_CONFIDENCE);
        copyIfPresent(out, mp, RagPresetClassifierMetrics.KEY_CLASSIFIER_LABEL_SET_HASH);
        copyIfPresent(out, mp, RagPresetClassifierMetrics.KEY_ROUTE_SUPPRESSED_BY_CLASSIFIER);
        copyIfPresent(out, mp, RagPresetClassifierMetrics.KEY_ROUTE_SUPPRESSED_REASON);
        copyIfPresent(out, mp, RagPresetClassifierMetrics.KEY_HEURISTIC_ROUTE_USED);

        out.put("queryTypeMatch", queryTypeMatch(expectedType, predictedLabel).name());

        String answerMode = str(mp.get(DatasetMetricContract.KEY_ANSWER_MODE));
        StructuredEvaluationResult structured =
                expectedType != null
                        ? QueryTypeEvaluatorRegistry.structuredEvaluation(
                                expectedType, expectedAnswer, actualAnswer, answerMode)
                        : StructuredEvaluationResult.notAvailable();
        structured.mergeInto(out);

        Map<String, Object> calibrationContext = new LinkedHashMap<>(mp);
        calibrationContext.putAll(out);
        ExpectedAnswerMatchResult calibratedMatch =
                ExpectedAnswerMatchCalibrator.calibrate(
                        expectedAnswer,
                        actualAnswer,
                        answerability,
                        expectedType,
                        calibrationContext,
                        contained,
                        abstention,
                        semantic);
        calibratedMatch.mergeInto(out, contained);
        out.put("calibratedMatcherApplied", true);

        Map<String, Object> compositionSeed = new LinkedHashMap<>(mp);
        compositionSeed.putAll(out);
        CompositionRouteTelemetryMapper.enrich(compositionSeed);
        copyIfPresent(out, compositionSeed, CompositionRouteTelemetryMapper.KEY_COMPONENT_ROUTE_DECISION);
        copyIfPresent(out, compositionSeed, CompositionRouteTelemetryMapper.KEY_COMPONENT_ROUTE_PRECEDENCE);
        copyIfPresent(out, compositionSeed, CompositionRouteTelemetryMapper.KEY_DETERMINISTIC_TOOL_CONSIDERED);
        copyIfPresent(out, compositionSeed, CompositionRouteTelemetryMapper.KEY_BACKEND_FUNCTION_CONSIDERED);
        copyIfPresent(out, compositionSeed, CompositionRouteTelemetryMapper.KEY_SPARSE_HYBRID_CONSIDERED);
        copyIfPresent(out, compositionSeed, CompositionRouteTelemetryMapper.KEY_FACTUAL_VERIFIER_CONSIDERED);
        copyIfPresent(out, compositionSeed, CompositionRouteTelemetryMapper.KEY_COMPOSITION_FALLBACK_REASON);

        AbstentionCorrectness abstentionCorrectness = abstentionCorrectness(answerability, abstention.abstained());
        out.put(KEY_ABSTENTION_CORRECTNESS, abstentionCorrectness.name());
        out.put(KEY_ABSTENTION_SCORE, abstentionScore(abstentionCorrectness));

        ScoreComposition composition =
                composeFinalScore(
                        answerability,
                        abstention.abstained(),
                        exact,
                        contained,
                        structured,
                        semantic,
                        abstentionCorrectness);
        out.put("finalScore", composition.finalScore());
        out.put("scoreFinal", composition.finalScore());
        if (composition.unavailableReason() != null) {
            out.put(KEY_SCORE_UNAVAILABLE_REASON, composition.unavailableReason());
        }

        if (answerability == Answerability.UNANSWERABLE) {
            out.put("correctAbstention", abstention.abstained());
            out.put("wrongAbstention", false);
            boolean negativeEvidenceFalsePositive =
                    !abstention.abstained()
                            && !AnswerabilityLabelRules.hasHighPrecisionNegativePhrasing(actualAnswer);
            out.put("negativeEvidenceFalsePositive", negativeEvidenceFalsePositive);
        } else if (answerability == Answerability.ANSWERABLE) {
            out.put("correctAbstention", false);
            out.put("wrongAbstention", abstention.abstained());
        }

        boolean retrievalPreset = preset != null && !ExperimentalPresetCanonicalCatalog.canRunWithoutRetrieval(preset);
        CoverageStatus retrievalCoverage = retrievalCoverage(mp, retrievalPreset);
        CoverageStatus sourceCoverage = sourceCoverage(mp, retrievalPreset);
        out.put("retrievalCoverageStatus", retrievalCoverage.name());
        out.put("sourceCoverageStatus", sourceCoverage.name());
        out.put("contextPresent", retrievalCoverage == CoverageStatus.HAS_CONTEXT);

        copyIfPresent(out, mp, "retrievalDenseCandidateCount");
        copyIfPresent(out, mp, "retrievalAfterFilterCount");
        copyIfPresent(out, mp, "contextChunkCount");
        copyIfPresent(out, mp, "promptContextCharCount");
        copyIfPresent(out, mp, "sourceCount");
        copyIfPresent(out, mp, "effectiveContextPresent");
        RagPresetAdvancedRetrievalMetrics.compute(mp).forEach(out::putIfAbsent);

        if (DatasetMetricContract.hasGoldLabels(mp)) {
            Map<String, Object> quality = RagPresetRetrievalQualityMetrics.compute(mp);
            out.putAll(quality);
        } else {
            out.put("retrievalQualityStatus", RetrievalQualityStatus.NOT_AVAILABLE.name());
        }

        return out;
    }

    private static AbstentionCorrectness abstentionCorrectness(Answerability answerability, boolean abstained) {
        return switch (answerability) {
            case UNANSWERABLE -> abstained ? AbstentionCorrectness.CORRECT : AbstentionCorrectness.WRONG;
            case ANSWERABLE -> abstained ? AbstentionCorrectness.WRONG : AbstentionCorrectness.NOT_APPLICABLE;
            case AMBIGUOUS, UNKNOWN, NEEDS_REVIEW -> AbstentionCorrectness.UNKNOWN;
        };
    }

    private static Object abstentionScore(AbstentionCorrectness correctness) {
        return switch (correctness) {
            case CORRECT -> 1.0;
            case WRONG -> 0.0;
            case NOT_APPLICABLE, UNKNOWN -> BenchmarkMvpSchema.NOT_AVAILABLE;
        };
    }

    private static ScoreComposition composeFinalScore(
            Answerability answerability,
            boolean abstained,
            boolean exact,
            boolean contained,
            StructuredEvaluationResult structured,
            Object semantic,
            AbstentionCorrectness abstentionCorrectness) {
        if (answerability == Answerability.UNANSWERABLE) {
            return new ScoreComposition(abstained ? 1.0 : 0.0, null);
        }
        if (answerability == Answerability.ANSWERABLE && abstained) {
            return new ScoreComposition(0.0, null);
        }
        if (structured.status() == StructuredScoreStatus.COMPUTED && structured.score() != null) {
            return new ScoreComposition(structured.score(), null);
        }
        if (exact) {
            return new ScoreComposition(1.0, null);
        }
        if (contained) {
            return new ScoreComposition(0.85, null);
        }
        if (semantic instanceof Number n) {
            return new ScoreComposition(n.doubleValue(), null);
        }
        if (abstentionCorrectness == AbstentionCorrectness.UNKNOWN && abstained) {
            return new ScoreComposition(0.0, "abstention_without_answerability");
        }
        return new ScoreComposition(0.0, "no_scoring_signal");
    }

    private static QueryTypeMatch queryTypeMatch(QueryType expected, String predictedLabel) {
        if (expected == null || predictedLabel == null || predictedLabel.isBlank()) {
            return QueryTypeMatch.UNKNOWN;
        }
        String norm = predictedLabel.trim().toUpperCase().replace(' ', '_');
        if (expected.name().equals(norm) || expected.name().equalsIgnoreCase(predictedLabel)) {
            return QueryTypeMatch.MATCH;
        }
        return QueryTypeMatch.MISMATCH;
    }

    private static CoverageStatus retrievalCoverage(Map<String, Object> mp, boolean retrievalPreset) {
        if (!retrievalPreset) {
            return CoverageStatus.NOT_APPLICABLE;
        }
        if (intVal(mp.get("sourceCount")) > 0
                || intVal(mp.get("contextChunkCount")) > 0
                || intVal(mp.get("promptContextCharCount")) > 0
                || bool(mp.get("effectiveContextPresent"))) {
            return CoverageStatus.HAS_CONTEXT;
        }
        if (hasIds(mp, "retrieved_chunk_ids", "retrievedChunkIds", "retrieved_document_ids", "retrievedDocumentIds")) {
            return CoverageStatus.HAS_CONTEXT;
        }
        return CoverageStatus.NO_CONTEXT;
    }

    private static CoverageStatus sourceCoverage(Map<String, Object> mp, boolean retrievalPreset) {
        if (!retrievalPreset) {
            return CoverageStatus.NOT_APPLICABLE;
        }
        return intVal(mp.get("sourceCount")) > 0 ? CoverageStatus.HAS_CONTEXT : CoverageStatus.NO_CONTEXT;
    }

    private static Object sourceSupportScore(Map<String, Object> mp) {
        if (intVal(mp.get("sourceCount")) <= 0 && !hasIds(mp, "retrieved_chunk_ids", "retrievedChunkIds")) {
            return BenchmarkMvpSchema.NOT_AVAILABLE;
        }
        return judgeScore(mp, "context_sufficiency");
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

    private static Object judgeScore(Map<String, Object> mp, String key) {
        Object js = mp.get("judge_scores");
        if (!(js instanceof Map<?, ?> m)) {
            return BenchmarkMvpSchema.NOT_AVAILABLE;
        }
        Object raw = m.get(key);
        if (!(raw instanceof Number n)) {
            return BenchmarkMvpSchema.NOT_AVAILABLE;
        }
        return Math.max(1.0, Math.min(5.0, n.doubleValue())) / 5.0;
    }

    private static Object exportScore(Object score) {
        return score != null ? score : BenchmarkMvpSchema.NOT_AVAILABLE;
    }

    private static QueryType parseQueryType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return QueryType.valueOf(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static void copyIfPresent(Map<String, Object> out, Map<String, Object> mp, String key) {
        if (mp.containsKey(key)) {
            out.put(key, mp.get(key));
        }
    }

    private static boolean hasIds(Map<String, Object> mp, String... keys) {
        for (String key : keys) {
            Object raw = mp.get(key);
            if (raw instanceof java.util.List<?> list && !list.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static int intVal(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        return 0;
    }

    private static boolean bool(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return false;
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

    public enum QueryTypeMatch {
        MATCH,
        MISMATCH,
        UNKNOWN
    }

    private record ScoreComposition(double finalScore, String unavailableReason) {}
}
