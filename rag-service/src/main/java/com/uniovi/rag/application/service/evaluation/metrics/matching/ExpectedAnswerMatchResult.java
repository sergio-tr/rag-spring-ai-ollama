package com.uniovi.rag.application.service.evaluation.metrics.matching;

import java.util.LinkedHashMap;
import java.util.Map;

/** Calibrated expected-answer match outcome with export metadata. */
public record ExpectedAnswerMatchResult(
        boolean matchedCalibrated,
        ExpectedAnswerMatchType matchType,
        ExpectedAnswerMatchConfidence confidence,
        String reason,
        String version) {

    public static final String KEY_MATCHED = "expectedAnswerMatchedCalibrated";
    public static final String KEY_CONTAINED_RAW = "expectedAnswerContainedRaw";
    public static final String KEY_MATCH_TYPE = "expectedAnswerMatchType";
    public static final String KEY_MATCH_CONFIDENCE = "expectedAnswerMatchConfidence";
    public static final String KEY_MATCH_REASON = "expectedAnswerMatchReason";
    public static final String KEY_MATCH_VERSION = "expectedAnswerMatchVersion";

    public static ExpectedAnswerMatchResult unsafeToJudge(String reason) {
        return new ExpectedAnswerMatchResult(
                false,
                ExpectedAnswerMatchType.UNSAFE_TO_JUDGE,
                ExpectedAnswerMatchConfidence.LOW,
                reason,
                ExpectedAnswerMatchCalibrator.VERSION);
    }

    public static ExpectedAnswerMatchResult noMatch(String reason) {
        return new ExpectedAnswerMatchResult(
                false,
                ExpectedAnswerMatchType.NO_MATCH,
                ExpectedAnswerMatchConfidence.HIGH,
                reason,
                ExpectedAnswerMatchCalibrator.VERSION);
    }

    public static ExpectedAnswerMatchResult match(
            ExpectedAnswerMatchType type, ExpectedAnswerMatchConfidence confidence, String reason) {
        return new ExpectedAnswerMatchResult(true, type, confidence, reason, ExpectedAnswerMatchCalibrator.VERSION);
    }

    public static ExpectedAnswerMatchResult supportOnly(String reason) {
        return new ExpectedAnswerMatchResult(
                false,
                ExpectedAnswerMatchType.SEMANTIC_SUPPORT_ONLY,
                ExpectedAnswerMatchConfidence.LOW,
                reason,
                ExpectedAnswerMatchCalibrator.VERSION);
    }

    public void mergeInto(Map<String, Object> target, boolean rawContained) {
        if (target == null) {
            return;
        }
        target.put(KEY_CONTAINED_RAW, rawContained);
        target.put(KEY_MATCHED, matchedCalibrated);
        target.put(KEY_MATCH_TYPE, matchType.name());
        target.put(KEY_MATCH_CONFIDENCE, confidence.name());
        target.put(KEY_MATCH_REASON, reason);
        target.put(KEY_MATCH_VERSION, version);
    }

    public Map<String, Object> toFieldMap(boolean rawContained) {
        Map<String, Object> out = new LinkedHashMap<>();
        mergeInto(out, rawContained);
        return out;
    }
}
