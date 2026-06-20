package com.uniovi.rag.application.service.evaluation.metrics.matching;

import com.uniovi.rag.application.service.evaluation.metrics.Answerability;
import com.uniovi.rag.application.service.evaluation.metrics.AnswerabilityNegativeSignals;

/** Detects Spanish negative-equivalence between expected absence and actual denial answers. */
public final class SpanishNegativeEquivalenceDetector {

    private SpanishNegativeEquivalenceDetector() {}

    public static NegativeEquivalenceResult evaluate(
            String expectedAnswer,
            String actualAnswer,
            Answerability answerability,
            boolean abstained,
            String abstentionSource,
            String finalAnswerSource) {
        if (answerability != Answerability.UNANSWERABLE) {
            return NegativeEquivalenceResult.notApplicable("answerability_not_unanswerable");
        }
        if (!AnswerabilityNegativeSignals.indicatesAbsenceExpected(expectedAnswer)) {
            return NegativeEquivalenceResult.notApplicable("expected_not_negative");
        }
        if (AnswerabilityNegativeSignals.hasAffirmativeUnsupportedClaim(actualAnswer)) {
            return NegativeEquivalenceResult.rejected("affirmative_unsupported_claim");
        }
        if (isBareNegative(actualAnswer)) {
            if (isSafeBareNegative(expectedAnswer)) {
                return NegativeEquivalenceResult.negativeEquivalence(
                        ExpectedAnswerMatchConfidence.MEDIUM, "safe_bare_negative_boolean");
            }
            return NegativeEquivalenceResult.unsafe("bare_negative_response");
        }

        boolean topicAnchoredNegative =
                AnswerabilityNegativeSignals.hasTopicAnchoredNegativeParaphrase(
                        expectedAnswer, actualAnswer);
        boolean topicSharedNegative =
                AnswerabilityNegativeSignals.hasHighPrecisionNegativePhrasing(actualAnswer)
                        && AnswerabilityNegativeSignals.sharesSubstantiveTopicWithExpected(
                                expectedAnswer, actualAnswer);
        boolean denies =
                abstained
                        || topicAnchoredNegative
                        || topicSharedNegative
                        || isForcedAbstention(finalAnswerSource);
        if (!denies) {
            return NegativeEquivalenceResult.rejected("actual_not_negative");
        }

        if (abstained) {
            return NegativeEquivalenceResult.correctAbstention(confidenceForAbstention(finalAnswerSource));
        }

        if (AnswerabilityNegativeSignals.hasHighPrecisionNegativePhrasing(actualAnswer)) {
            if (AnswerabilityNegativeSignals.sharesSubstantiveTopicWithExpected(
                    expectedAnswer, actualAnswer)) {
                return NegativeEquivalenceResult.negativeEquivalence(
                        ExpectedAnswerMatchConfidence.HIGH, "negative_phrasing_paraphrase");
            }
        }

        if (AnswerabilityNegativeSignals.hasTopicAnchoredNegativeParaphrase(expectedAnswer, actualAnswer)) {
            return NegativeEquivalenceResult.negativeEquivalence(
                    ExpectedAnswerMatchConfidence.HIGH, "topic_anchored_negative_paraphrase");
        }

        if (isForcedAbstention(finalAnswerSource)) {
            return NegativeEquivalenceResult.correctAbstention(ExpectedAnswerMatchConfidence.HIGH);
        }

        return NegativeEquivalenceResult.rejected("negative_gate_failed");
    }

    private static ExpectedAnswerMatchConfidence confidenceForAbstention(String finalAnswerSource) {
        if (isForcedAbstention(finalAnswerSource)) {
            return ExpectedAnswerMatchConfidence.HIGH;
        }
        return ExpectedAnswerMatchConfidence.MEDIUM;
    }

    private static boolean isForcedAbstention(String finalAnswerSource) {
        if (finalAnswerSource == null || finalAnswerSource.isBlank()) {
            return false;
        }
        String upper = finalAnswerSource.toUpperCase(java.util.Locale.ROOT);
        return upper.contains("FORCED_ABSTENTION") || upper.contains("DATE_GUARD_ABSTENTION");
    }

    private static boolean isBareNegative(String actualAnswer) {
        if (actualAnswer == null) {
            return true;
        }
        String folded = ExpectedAnswerNormalizer.normalizedFold(actualAnswer);
        return folded.equals("no") || folded.matches("no[.!]?");
    }

    /** Accept bare "no" only when expected absence names a concrete topic (boolean negative guard). */
    private static boolean isSafeBareNegative(String expectedAnswer) {
        if (expectedAnswer == null || expectedAnswer.isBlank()) {
            return false;
        }
        String folded = ExpectedAnswerNormalizer.normalizedFold(expectedAnswer);
        if (!folded.startsWith("no")) {
            return false;
        }
        if (!AnswerabilityNegativeSignals.indicatesAbsenceExpected(expectedAnswer)) {
            return false;
        }
        var tokens =
                ExpectedAnswerNormalizer.normalizedTokens(
                        expectedAnswer, ExpectedAnswerNormalizer.TokenMode.ENTITY);
        long substantiveTopicTokens =
                tokens.stream()
                        .filter(token -> token.length() > 3)
                        .filter(token -> !GENERIC_ABSENCE_TOKENS.contains(token))
                        .count();
        return substantiveTopicTokens >= 1;
    }

    private static final java.util.Set<String> GENERIC_ABSENCE_TOKENS =
            java.util.Set.of(
                    "datos",
                    "dato",
                    "informacion",
                    "registro",
                    "mencion",
                    "evidencia",
                    "consta",
                    "disponible",
                    "actas",
                    "acta",
                    "fuentes",
                    "fuente",
                    "ninguna",
                    "ninguno",
                    "ningun",
                    "alguna",
                    "alguno",
                    "reunion",
                    "reuniones",
                    "celebrada",
                    "celebradas",
                    "menciona",
                    "menciono",
                    "mencionan",
                    "durante",
                    "ninguna de las");

    public record NegativeEquivalenceResult(
            boolean applicable,
            boolean matched,
            ExpectedAnswerMatchType type,
            ExpectedAnswerMatchConfidence confidence,
            String reason,
            boolean unsafe) {

        static NegativeEquivalenceResult notApplicable(String reason) {
            return new NegativeEquivalenceResult(false, false, null, null, reason, false);
        }

        static NegativeEquivalenceResult rejected(String reason) {
            return new NegativeEquivalenceResult(true, false, ExpectedAnswerMatchType.NO_MATCH, null, reason, false);
        }

        static NegativeEquivalenceResult unsafe(String reason) {
            return new NegativeEquivalenceResult(true, false, ExpectedAnswerMatchType.UNSAFE_TO_JUDGE, null, reason, true);
        }

        static NegativeEquivalenceResult negativeEquivalence(ExpectedAnswerMatchConfidence confidence, String reason) {
            return new NegativeEquivalenceResult(
                    true,
                    true,
                    ExpectedAnswerMatchType.NEGATIVE_EQUIVALENCE,
                    confidence,
                    reason,
                    false);
        }

        static NegativeEquivalenceResult correctAbstention(ExpectedAnswerMatchConfidence confidence) {
            return new NegativeEquivalenceResult(
                    true,
                    true,
                    ExpectedAnswerMatchType.CORRECT_ABSTENTION,
                    confidence,
                    "unanswerable_abstention",
                    false);
        }
    }
}
