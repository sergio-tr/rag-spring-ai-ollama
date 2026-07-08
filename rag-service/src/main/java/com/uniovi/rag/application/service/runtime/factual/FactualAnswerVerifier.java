package com.uniovi.rag.application.service.runtime.factual;

import com.uniovi.rag.application.service.runtime.DateGroundingSupport;
import com.uniovi.rag.domain.runtime.policy.AnswerGroundingPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class FactualAnswerVerifier {

    private static final Pattern AFFIRMATIVE =
            Pattern.compile(
                    "\\b(sí|si|se hablo|se comento|se mencion|se trat|se discute|se acordo|se acord|se propone|se plantea|se considera|hubo|fueron|participaron|segun las actas|son las del|son las de)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern MEETING_DATE_REFERENCE =
            Pattern.compile(
                    "\\b(reunion del|reunion de|acta del|acta de)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern INTEGER = Pattern.compile("\\b(\\d{1,4})\\b");
    private static final Set<String> DISTRACTOR_TOPICS =
            Set.of("videovigilancia", "camara", "camaras", "cámaras", "seguridad", "ascensor", "calefaccion", "calefacción", "limpieza", "presion", "presión", "agua");

    private FactualAnswerVerifier() {}

    public static FactualVerifierResult verify(
            FactualQuestionConstraints constraints, String contextText, String answerText) {
        if (constraints == null) {
            return FactualVerifierResult.pass();
        }
        String context = contextText != null ? contextText : "";
        String answer = answerText != null ? answerText.trim() : "";
        if (answer.isBlank()) {
            return FactualVerifierResult.pass();
        }
        List<FactualVerifierFailureReason> failures = new ArrayList<>();
        checkDate(constraints, context, answer, failures);
        checkTopic(constraints, context, answer, failures);
        checkEntity(constraints, context, answer, failures);
        checkNumeric(constraints, context, answer, failures);
        checkUnrelatedTopic(constraints, context, answer, failures);
        checkAbsenceTopicSubstitution(constraints, context, answer, failures);
        checkNegativeEvidence(constraints, answer, failures);
        checkAbsenceSubstantivePositive(constraints, answer, failures);
        checkUnsupportedPositive(constraints, context, answer, failures);
        checkDistractorMeetingReference(constraints, answer, failures);
        return failures.isEmpty() ? FactualVerifierResult.pass() : FactualVerifierResult.fail(failures);
    }

    private static void checkDate(
            FactualQuestionConstraints constraints, String context, String answer, List<FactualVerifierFailureReason> failures) {
        if (!constraints.requiredDateIso().isPresent()) {
            return;
        }
        String required = constraints.requiredDateIso().orElseThrow();
        Optional<DateGroundingSupport.RequestedDate> requested = parseRequestedDate(required);
        if (requested.isEmpty()) {
            return;
        }
        String normContext = FactualTextSupport.normalize(context);
        String normAnswer = FactualTextSupport.normalize(answer);
        if (!normContext.contains(required) && !normContext.contains(required.replace("-", "/"))) {
            if (constraints.groundingPolicy() == AnswerGroundingPolicy.NEGATIVE_EVIDENCE
                    || constraints.absenceQuestion()) {
                if (looksAffirmative(normAnswer) && !FactualTextSupport.hasHighPrecisionNegativePhrasing(answer)) {
                    failures.add(FactualVerifierFailureReason.DATE_MISMATCH);
                }
            } else if (looksAffirmative(normAnswer) && !contextHasDate(required, context)) {
                failures.add(FactualVerifierFailureReason.DATE_MISMATCH);
            }
        }
    }

    private static void checkTopic(
            FactualQuestionConstraints constraints, String context, String answer, List<FactualVerifierFailureReason> failures) {
        if (constraints.topicPhrases().isEmpty()) {
            return;
        }
        String normContext = FactualTextSupport.normalize(context);
        String normAnswer = FactualTextSupport.normalize(answer);
        for (String topic : constraints.topicPhrases()) {
            String normTopic = FactualTextSupport.normalize(topic);
            if (normTopic.isBlank()) {
                continue;
            }
            boolean topicInContext = normContext.contains(normTopic);
            boolean topicInAnswer = normAnswer.contains(normTopic);
            if (constraints.groundingPolicy() == AnswerGroundingPolicy.NEGATIVE_EVIDENCE || constraints.absenceQuestion()) {
                if (looksAffirmative(normAnswer) && !topicInContext) {
                    failures.add(FactualVerifierFailureReason.TOPIC_NOT_IN_CONTEXT);
                    return;
                }
            } else if (looksAffirmative(normAnswer) && topicInAnswer && !topicInContext) {
                failures.add(FactualVerifierFailureReason.TOPIC_NOT_IN_CONTEXT);
                return;
            }
        }
    }

    private static void checkEntity(
            FactualQuestionConstraints constraints, String context, String answer, List<FactualVerifierFailureReason> failures) {
        if (!constraints.requiredEntity().isPresent()) {
            return;
        }
        String entity = FactualTextSupport.normalize(constraints.requiredEntity().orElseThrow());
        String normContext = FactualTextSupport.normalize(context);
        String normAnswer = FactualTextSupport.normalize(answer);
        if (looksAffirmative(normAnswer) && normAnswer.contains(entity) && !normContext.contains(entity)) {
            failures.add(FactualVerifierFailureReason.ENTITY_NOT_IN_CONTEXT);
        }
    }

    private static void checkNumeric(
            FactualQuestionConstraints constraints, String context, String answer, List<FactualVerifierFailureReason> failures) {
        if (!constraints.numericConstraint().isPresent()) {
            return;
        }
        FactualQuestionConstraints.NumericConstraint nc = constraints.numericConstraint().orElseThrow();
        Integer answerNumber = firstInteger(answer);
        if (answerNumber == null) {
            return;
        }
        if (!contextContainsNumber(context, answerNumber)) {
            failures.add(FactualVerifierFailureReason.NUMERIC_MISMATCH);
            return;
        }
        if (nc.kind() == FactualQuestionConstraints.ComparatorKind.EXACTLY && !answerNumber.equals(nc.value())) {
            failures.add(FactualVerifierFailureReason.NUMERIC_MISMATCH);
        }
    }

    private static void checkUnrelatedTopic(
            FactualQuestionConstraints constraints, String context, String answer, List<FactualVerifierFailureReason> failures) {
        if (constraints.topicPhrases().isEmpty()) {
            return;
        }
        String normQuestionTopics = FactualTextSupport.normalize(String.join(" ", constraints.topicPhrases()));
        String normAnswer = FactualTextSupport.normalize(answer);
        String normContext = FactualTextSupport.normalize(context);
        for (String distractor : DISTRACTOR_TOPICS) {
            if (normQuestionTopics.contains(distractor)) {
                continue;
            }
            if (!normAnswer.contains(distractor)) {
                continue;
            }
            if (constraints.absenceQuestion()
                    || constraints.groundingPolicy() == AnswerGroundingPolicy.NEGATIVE_EVIDENCE) {
                if (!FactualTextSupport.hasHighPrecisionNegativePhrasing(answer)) {
                    failures.add(FactualVerifierFailureReason.UNRELATED_TOPIC);
                    return;
                }
                continue;
            }
            if (!normContext.contains(distractor)) {
                failures.add(FactualVerifierFailureReason.UNRELATED_TOPIC);
                return;
            }
        }
    }

    private static void checkAbsenceTopicSubstitution(
            FactualQuestionConstraints constraints, String context, String answer, List<FactualVerifierFailureReason> failures) {
        if (!constraints.absenceQuestion() || constraints.topicPhrases().isEmpty()) {
            return;
        }
        if (FactualTextSupport.hasHighPrecisionNegativePhrasing(answer)) {
            return;
        }
        String normAnswer = FactualTextSupport.normalize(answer);
        String normContext = FactualTextSupport.normalize(context);
        if (normAnswer.isBlank()) {
            return;
        }
        boolean requiredTopicInAnswer = false;
        for (String topic : constraints.topicPhrases()) {
            String normTopic = FactualTextSupport.normalize(topic);
            if (!normTopic.isBlank() && normAnswer.contains(normTopic)) {
                requiredTopicInAnswer = true;
                break;
            }
        }
        if (requiredTopicInAnswer) {
            return;
        }
        if (looksAffirmative(normAnswer) || looksSubstantiveDiscussion(normAnswer)) {
            failures.add(FactualVerifierFailureReason.NEGATIVE_FALSE_POSITIVE);
            return;
        }
        if (normContext.isBlank()) {
            return;
        }
        for (String token : normAnswer.split("\\s+")) {
            if (token.length() >= 6 && normContext.contains(token) && !questionTopicsContain(constraints, token)) {
                failures.add(FactualVerifierFailureReason.UNRELATED_TOPIC);
                return;
            }
        }
    }

    private static boolean questionTopicsContain(FactualQuestionConstraints constraints, String token) {
        String normTopics = FactualTextSupport.normalize(String.join(" ", constraints.topicPhrases()));
        return normTopics.contains(token);
    }

    private static boolean looksSubstantiveDiscussion(String normalizedAnswer) {
        return normalizedAnswer != null
                && (normalizedAnswer.contains("instalar")
                        || normalizedAnswer.contains("sistema")
                        || normalizedAnswer.contains("posibilidad")
                        || normalizedAnswer.length() > 60);
    }

    private static void checkNegativeEvidence(
            FactualQuestionConstraints constraints, String answer, List<FactualVerifierFailureReason> failures) {
        if (constraints.groundingPolicy() != AnswerGroundingPolicy.NEGATIVE_EVIDENCE && !constraints.absenceQuestion()) {
            return;
        }
        if (looksAffirmative(FactualTextSupport.normalize(answer))
                && !FactualTextSupport.hasHighPrecisionNegativePhrasing(answer)) {
            failures.add(FactualVerifierFailureReason.NEGATIVE_FALSE_POSITIVE);
        }
    }

    private static void checkUnsupportedPositive(
            FactualQuestionConstraints constraints, String context, String answer, List<FactualVerifierFailureReason> failures) {
        if (context == null || context.isBlank()) {
            return;
        }
        if (constraints.groundingPolicy() == AnswerGroundingPolicy.NEGATIVE_EVIDENCE) {
            return;
        }
        if (constraints.absenceQuestion()) {
            return;
        }
        if (looksAffirmative(FactualTextSupport.normalize(answer))
                && constraints.topicPhrases().isEmpty()
                && constraints.requiredEntity().isEmpty()
                && !contextHasSubstantiveOverlap(context, answer)) {
            failures.add(FactualVerifierFailureReason.UNSUPPORTED_POSITIVE_CLAIM);
        }
    }

    private static void checkAbsenceSubstantivePositive(
            FactualQuestionConstraints constraints, String answer, List<FactualVerifierFailureReason> failures) {
        if (!constraints.absenceQuestion()) {
            return;
        }
        String normalized = FactualTextSupport.normalize(answer);
        if (normalized.isBlank() || normalized.equals("no")) {
            return;
        }
        if (FactualTextSupport.hasHighPrecisionNegativePhrasing(answer)) {
            return;
        }
        if (looksAffirmative(normalized)
                || looksSubstantiveDiscussion(normalized)
                || (normalized.contains("acta") && normalized.length() > 40)) {
            failures.add(FactualVerifierFailureReason.NEGATIVE_FALSE_POSITIVE);
        }
    }

    private static void checkDistractorMeetingReference(
            FactualQuestionConstraints constraints, String answer, List<FactualVerifierFailureReason> failures) {
        if (!constraints.absenceQuestion() || constraints.numericConstraint().isEmpty()) {
            return;
        }
        String normalized = FactualTextSupport.normalize(answer);
        if (MEETING_DATE_REFERENCE.matcher(normalized).find()) {
            failures.add(FactualVerifierFailureReason.UNRELATED_TOPIC);
        }
    }

    private static boolean contextHasSubstantiveOverlap(String context, String answer) {
        String[] answerTokens = FactualTextSupport.normalize(answer).split("\\s+");
        String normContext = FactualTextSupport.normalize(context);
        int hits = 0;
        for (String token : answerTokens) {
            if (token.length() >= 5 && normContext.contains(token)) {
                hits++;
                if (hits >= 2) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean contextContainsNumber(String context, int number) {
        Matcher m = INTEGER.matcher(context);
        while (m.find()) {
            try {
                if (Integer.parseInt(m.group(1)) == number) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // continue
            }
        }
        return false;
    }

    private static Integer firstInteger(String text) {
        Matcher m = INTEGER.matcher(text != null ? text : "");
        while (m.find()) {
            try {
                return Integer.parseInt(m.group(1));
            } catch (NumberFormatException ignored) {
                // continue
            }
        }
        return null;
    }

    private static boolean looksAffirmative(String normalizedAnswer) {
        return normalizedAnswer != null && AFFIRMATIVE.matcher(normalizedAnswer).find();
    }

    private static boolean contextHasDate(String isoDate, String context) {
        return FactualTextSupport.normalize(context).contains(isoDate);
    }

    private static Optional<DateGroundingSupport.RequestedDate> parseRequestedDate(String iso) {
        if (iso == null || iso.isBlank()) {
            return Optional.empty();
        }
        if (iso.matches("\\d{4}-\\d{2}-\\d{2}")) {
            return Optional.of(new DateGroundingSupport.RequestedDate(iso, DateGroundingSupport.DatePrecision.DAY));
        }
        if (iso.matches("\\d{4}-\\d{2}")) {
            return Optional.of(new DateGroundingSupport.RequestedDate(iso, DateGroundingSupport.DatePrecision.MONTH));
        }
        if (iso.matches("\\d{4}")) {
            return Optional.of(new DateGroundingSupport.RequestedDate(iso, DateGroundingSupport.DatePrecision.YEAR));
        }
        return Optional.empty();
    }
}
