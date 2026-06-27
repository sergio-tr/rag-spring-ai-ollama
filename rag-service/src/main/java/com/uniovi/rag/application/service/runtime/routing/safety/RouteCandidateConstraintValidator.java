package com.uniovi.rag.application.service.runtime.routing.safety;

import com.uniovi.rag.application.service.evaluation.metrics.matching.ExpectedAnswerNormalizer;
import com.uniovi.rag.application.service.runtime.tool.DeterministicToolKindMappings;
import com.uniovi.rag.application.service.runtime.retrieval.RetrievalEntityMatchingSupport;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.query.ExpectedAnswerShape;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/** Runtime-only validation of route candidates against query constraints. */
@Component
public class RouteCandidateConstraintValidator {

    private static final Pattern AFFIRMATIVE_START =
            Pattern.compile("^\\s*(s[ií]|yes)\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern ABSTENTION =
            Pattern.compile(
                    "\\bno consta\\b|\\bno hay (?:informacion|información|registro|datos)\\b|\\binsufficient evidence\\b|\\bno se mencion",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern YEAR_IN_ANSWER = Pattern.compile("\\b(19|20)\\d{2}\\b");
    private static final Pattern SPANISH_MONTH =
            Pattern.compile(
                    "\\b(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern FIND_PARAGRAPH_HEDGE =
            Pattern.compile(
                    "no se proporciona informacion adicional|no se detalla|queda pendiente de estudio|aunque no se detalla|no se ofrecen comentarios|no se ofrece informacion especifica",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CONCRETE_COUNT =
            Pattern.compile(
                    "\\b(cero|uno|un|una|dos|tres|cuatro|cinco|seis|siete|ocho|nueve|diez|\\d+)\\s+(actas?|reuniones?|personas?|asistentes?)\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern CONCRETE_ACTA_DATE =
            Pattern.compile(
                    "\\b\\d{1,2}\\s+de\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre)\\s+de\\s+\\d{4}\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern SLASH_DATE = Pattern.compile("\\b\\d{1,2}/\\d{1,2}/\\d{4}\\b");
    private static final Pattern ISO_DATE = Pattern.compile("\\b(19|20)\\d{2}-(\\d{2})-(\\d{2})\\b");
    private static final Map<String, Integer> SPANISH_MONTH_TO_NUMBER =
            Map.ofEntries(
                    Map.entry("enero", 1),
                    Map.entry("febrero", 2),
                    Map.entry("marzo", 3),
                    Map.entry("abril", 4),
                    Map.entry("mayo", 5),
                    Map.entry("junio", 6),
                    Map.entry("julio", 7),
                    Map.entry("agosto", 8),
                    Map.entry("septiembre", 9),
                    Map.entry("octubre", 10),
                    Map.entry("noviembre", 11),
                    Map.entry("diciembre", 12));
    private static final Pattern FILTER_LIST_FUERON_DESCRIPTOR =
            Pattern.compile(
                    "\\bfueron\\s+(?:las?|los?)\\s+([^.]{8,140})",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern FUNCTION_SENTINEL_ABSTENTION =
            Pattern.compile(
                    "\\btopic_not_in_context\\b|\\bnot_in_context\\b|\\bno_hay_contexto\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern FILTER_LIST_VAGUE_ABSENCE =
            Pattern.compile(
                    "\\bno\\s+hay\\s+informacion\\s+suficiente\\b|\\bno\\s+hay\\s+informacion\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    public RouteCandidateValidationResult validateToolOrFunctionAnswer(
            QueryPlan plan, String answerText, Optional<DeterministicToolKind> toolKind) {
        if (answerText == null || answerText.isBlank()) {
            return RouteCandidateValidationResult.rejected("empty_answer");
        }
        QueryConstraintSignals signals = QueryConstraintSignals.fromPlan(plan);
        List<String> failures = new ArrayList<>();
        String folded = ExpectedAnswerNormalizer.normalizedFold(answerText);
        Optional<QueryType> qt = signals.queryType().or(() -> toolKind.map(DeterministicToolKindMappings::toQueryType));

        if (topicOccurrenceAcrossActasQuery(plan)
                && qt.filter(t -> t == QueryType.COUNT_AND_EXPLAIN).isPresent()
                && lexicalTopicFrequencyAnswer(folded)
                && !hasConcreteActaReference(folded)) {
            failures.add("topic_occurrence_lexical_frequency");
        }

        if (signals.filterAndList()) {
            for (String topic : signals.topicTokens()) {
                if (filterListTopicDisclaimed(folded, topic)) {
                    failures.add("filter_list_topic_disclaimed:" + topic);
                }
            }
        }

        if (signals.filterAndList()
                && !signals.absenceLikely()
                && isNegativeOrAbstentionAnswer(folded)) {
            failures.add("filter_list_unsupported_abstention");
            if (functionSentinelAbstention(folded)) {
                failures.add("function_sentinel_abstention");
            }
            if (filterListVagueAbsence(folded)) {
                failures.add("filter_list_vague_absence");
            }
        }

        if (signals.findParagraphLookup() && findParagraphHedgedAnswer(folded)) {
            failures.add("find_paragraph_hedged_answer");
        }

        if (signals.findParagraphLookup()
                && !isNegativeOrAbstentionAnswer(folded)
                && !hasConcreteActaReference(folded)) {
            failures.add("find_paragraph_missing_acta_reference");
        }

        if (signals.absenceLikely() && concreteAffirmationWithoutAbstention(folded)) {
            failures.add("absence_query_concrete_affirmation");
        }

        for (String topic : signals.topicTokens()) {
            if (relaxedGetFieldStructuredAnswer(qt, folded, plan) && !isNegativeOrAbstentionAnswer(folded)) {
                continue;
            }
            if (!answerContainsTopicToken(folded, topic) && !isNegativeOrAbstentionAnswer(folded)) {
                failures.add("topic_missing:" + topic);
            }
        }

        for (String entity : signals.entityTokens()) {
            if (relaxedGetFieldStructuredAnswer(qt, folded, plan) && !isNegativeOrAbstentionAnswer(folded)) {
                continue;
            }
            if (relaxedGetDurationStructuredAnswer(qt, folded) && !isNegativeOrAbstentionAnswer(folded)) {
                continue;
            }
            if (relaxedStructuredMetadataAnswer(qt, folded, entity, signals) && !isNegativeOrAbstentionAnswer(folded)) {
                continue;
            }
            if (!entity.isBlank() && !folded.contains(entity) && !isNegativeOrAbstentionAnswer(folded)) {
                failures.add("entity_missing:" + entity);
            }
        }

        for (Integer year : signals.years()) {
            if (relaxedGetFieldStructuredAnswer(qt, folded, plan) && !isNegativeOrAbstentionAnswer(folded)) {
                continue;
            }
            if (!answerContainsYear(folded, year) && !isNegativeOrAbstentionAnswer(folded)) {
                if (signals.booleanVerify()
                        || plan.expectedAnswerShape() == ExpectedAnswerShape.SCALAR_BOOLEAN
                        || signals.filterAndList()
                        || summarizeMeetingAbsenceYearRequired(qt, signals)) {
                    failures.add("year_constraint_missing:" + year);
                }
            }
        }

        for (String month : signals.monthNames()) {
            if (relaxedGetFieldStructuredAnswer(qt, folded, plan) && !isNegativeOrAbstentionAnswer(folded)) {
                continue;
            }
            if (relaxedGetDurationStructuredAnswer(qt, folded) && !isNegativeOrAbstentionAnswer(folded)) {
                continue;
            }
            if (isNegativeOrAbstentionAnswer(folded)) {
                continue;
            }
            if (folded.contains(month)) {
                continue;
            }
            if (signals.filterAndList() && answerSatisfiesSpanishMonthInDates(folded, month)) {
                continue;
            }
            failures.add("month_constraint_missing:" + month);
        }

        if (signals.booleanVerify() && AFFIRMATIVE_START.matcher(answerText).find()) {
            for (String topic : signals.topicTokens()) {
                if (!folded.contains(topic)) {
                    failures.add("boolean_affirmation_without_topic:" + topic);
                }
            }
        }

        if (signals.filterAndList() && !isNegativeOrAbstentionAnswer(folded)) {
            if (!mentionsListEvidence(folded) && !hasConcreteActaReference(folded)) {
                failures.add("filter_list_missing_evidence");
            }
            if (hasCombinedTopicRoleConstraint(signals)) {
                if (!hasConcreteActaReference(folded)) {
                    failures.add("filter_list_missing_concrete_acta_reference");
                }
                if (!topicEntityCoBoundInSameEvidenceUnit(answerText, signals)) {
                    failures.add("filter_list_topic_entity_not_co_bound");
                }
                if (filterListTopicDescriptorMismatch(answerText, signals.topicTokens())) {
                    failures.add("filter_list_topic_descriptor_mismatch");
                }
            }
            if (toolKind.filter(k -> k == DeterministicToolKind.FILTER_AND_LIST_TOOL).isPresent()
                    && mentionsConflictingMonth(folded, signals.monthNames())) {
                failures.add("month_constraint_conflict");
            }
        }

        if (qt.filter(t -> t == QueryType.COUNT_DOCUMENTS || t == QueryType.COUNT_AND_EXPLAIN).isPresent()) {
            if (!hasCountableAnswer(folded) && !isNegativeOrAbstentionAnswer(folded)) {
                failures.add("count_answer_missing");
            }
        }

        if (!failures.isEmpty()) {
            if (signals.filterAndList()) {
                addCanonicalFilterListRejection(failures);
            }
            return RouteCandidateValidationResult.rejected(failures);
        }
        double confidence = confidenceScore(signals, folded);
        return RouteCandidateValidationResult.accepted(confidence, coverageLabel(signals, folded));
    }

    public RouteCandidateValidationResult validateRetrievalAnswer(QueryPlan plan, String answerText, boolean abstained) {
        QueryConstraintSignals signals = QueryConstraintSignals.fromPlan(plan);
        boolean enforceConstraints =
                signals.filterAndList() || signals.booleanVerify() || signals.absenceLikely();
        if (abstained && !enforceConstraints) {
            return RouteCandidateValidationResult.accepted(0.85, "ABSTENTION");
        }
        if (answerText != null && !answerText.isBlank()) {
            RouteCandidateValidationResult constrained =
                    validateToolOrFunctionAnswer(plan, answerText, Optional.empty());
            if (!constrained.safe()) {
                return constrained;
            }
            if (abstained) {
                return RouteCandidateValidationResult.accepted(0.85, "ABSTENTION");
            }
            return constrained;
        }
        if (abstained) {
            return RouteCandidateValidationResult.accepted(0.85, "ABSTENTION");
        }
        return validateToolOrFunctionAnswer(plan, answerText, Optional.empty());
    }

    private static double confidenceScore(QueryConstraintSignals signals, String folded) {
        double score = 0.65;
        if (!signals.topicTokens().isEmpty()) {
            score += 0.1;
        }
        if (!signals.years().isEmpty() && signals.years().stream().anyMatch(y -> folded.contains(String.valueOf(y)))) {
            score += 0.1;
        }
        if (!signals.monthNames().isEmpty()
                && signals.monthNames().stream().anyMatch(folded::contains)) {
            score += 0.1;
        }
        return Math.min(score, 0.95);
    }

    private static String coverageLabel(QueryConstraintSignals signals, String folded) {
        if (isNegativeOrAbstentionAnswer(folded)) {
            return "NEGATIVE_OR_ABSTENTION";
        }
        if (!signals.topicTokens().isEmpty()) {
            return "TOPIC_COVERED";
        }
        return "PARTIAL";
    }

    private static boolean relaxedGetFieldStructuredAnswer(
            Optional<QueryType> queryType, String folded, QueryPlan plan) {
        if (queryType.filter(t -> t == QueryType.GET_FIELD).isEmpty()) {
            return false;
        }
        if (relaxedGetFieldParticipantList(queryType, folded)) {
            return true;
        }
        String query =
                ((plan.rewrittenQueryText() == null ? "" : plan.rewrittenQueryText())
                                + " "
                                + (plan.normalizedQueryText() == null ? "" : plan.normalizedQueryText()))
                        .toLowerCase(Locale.ROOT);
        boolean agendaQuery =
                query.contains("orden del d")
                        || query.contains("puntos del orden")
                        || query.contains("agenda");
        if (!agendaQuery) {
            return false;
        }
        return folded.contains("lectura")
                || folded.contains("presupuesto")
                || folded.contains("reparacion")
                || folded.contains("normas")
                || folded.contains("ruegos")
                || folded.contains("mantenimiento");
    }

    private static boolean relaxedGetFieldParticipantList(Optional<QueryType> queryType, String folded) {
        return queryType.filter(t -> t == QueryType.GET_FIELD).isPresent()
                && (folded.contains("participantes") || folded.contains("asistentes"))
                && (folded.contains(" en total") || folded.contains(","));
    }

    /** GET_DURATION tool answers with slash date, clock times, and explicit duration tokens. */
    private static boolean relaxedGetDurationStructuredAnswer(Optional<QueryType> queryType, String folded) {
        if (queryType.filter(t -> t == QueryType.GET_DURATION).isEmpty()) {
            return false;
        }
        if (isNegativeOrAbstentionAnswer(folded)) {
            return false;
        }
        boolean hasDate = SLASH_DATE.matcher(folded).find() || SPANISH_MONTH.matcher(folded).find();
        boolean hasTimes = folded.matches(".*\\d{1,2}:\\d{2}.*");
        boolean hasDuration = folded.contains("minut") || folded.contains("hora");
        return hasDate && hasTimes && hasDuration;
    }

    private static boolean relaxedStructuredMetadataAnswer(
            Optional<QueryType> queryType, String folded, String entity, QueryConstraintSignals signals) {
        if (queryType.filter(
                        t ->
                                t == QueryType.SUMMARIZE_MEETING
                                        || t == QueryType.GET_DURATION
                                        || t == QueryType.GET_FIELD)
                .isEmpty()) {
            return false;
        }
        if (!isDateLikeEntity(entity)) {
            return false;
        }
        return answerSatisfiesDateEntity(folded, entity, signals);
    }

    private static boolean isDateLikeEntity(String entity) {
        if (entity == null || entity.isBlank()) {
            return false;
        }
        return SLASH_DATE.matcher(entity).find()
                || SPANISH_MONTH.matcher(entity).find()
                || YEAR_IN_ANSWER.matcher(entity).find();
    }

    private static boolean answerSatisfiesDateEntity(
            String folded, String entity, QueryConstraintSignals signals) {
        Matcher yearInEntity = YEAR_IN_ANSWER.matcher(entity);
        if (yearInEntity.find() && folded.contains(yearInEntity.group())) {
            return true;
        }
        for (String month : signals.monthNames()) {
            if (folded.contains(month)) {
                return true;
            }
        }
        return SLASH_DATE.matcher(folded).find() || CONCRETE_ACTA_DATE.matcher(folded).find();
    }

    private static boolean findParagraphHedgedAnswer(String folded) {
        if (isNegativeOrAbstentionAnswer(folded)) {
            return false;
        }
        return FIND_PARAGRAPH_HEDGE.matcher(folded).find();
    }

    private static boolean filterListTopicDisclaimed(String folded, String topic) {
        if (!answerContainsTopicToken(folded, topic)) {
            return false;
        }
        return folded.contains("no se detalla")
                || folded.contains("aunque no")
                || folded.contains("sin mencionar")
                || folded.contains("no se proporciona informacion adicional");
    }

    private static boolean answerContainsTopicToken(String folded, String topic) {
        if (topic == null || topic.isBlank()) {
            return false;
        }
        if (folded.contains(topic)) {
            return true;
        }
        if (RetrievalEntityMatchingSupport.containsEntityToken(folded, topic)) {
            return true;
        }
        if (topic.endsWith("n")) {
            return folded.contains(topic + "es");
        }
        return folded.contains(topic + "s") || folded.contains(topic + "es");
    }

    private static boolean concreteAffirmationWithoutAbstention(String folded) {
        if (isNegativeOrAbstentionAnswer(folded)) {
            return false;
        }
        return CONCRETE_COUNT.matcher(folded).find() || hasConcreteActaReference(folded);
    }

    private static boolean summarizeMeetingAbsenceYearRequired(
            Optional<QueryType> queryType, QueryConstraintSignals signals) {
        return signals.absenceLikely()
                && queryType.filter(t -> t == QueryType.SUMMARIZE_MEETING).isPresent();
    }

    private static boolean topicOccurrenceAcrossActasQuery(QueryPlan plan) {
        if (plan == null) {
            return false;
        }
        String query =
                ((plan.rewrittenQueryText() == null ? "" : plan.rewrittenQueryText())
                                + " "
                                + (plan.normalizedQueryText() == null ? "" : plan.normalizedQueryText()))
                        .toLowerCase(Locale.ROOT);
        return (query.contains("cuántas veces aparece") || query.contains("cuantas veces aparece"))
                && !query.contains("en qué reuniones")
                && !query.contains("en que reuniones");
    }

    private static boolean lexicalTopicFrequencyAnswer(String folded) {
        return folded.contains("aparece")
                && (folded.matches(".*\\b\\d+\\s+veces.*")
                        || folded.contains(" dos veces")
                        || folded.contains(" tres veces"));
    }

    private static boolean isNegativeOrAbstentionAnswer(String folded) {
        return ABSTENTION.matcher(folded).find()
                || functionSentinelAbstention(folded)
                || filterListVagueAbsence(folded)
                || folded.startsWith("no ")
                || folded.contains("ninguna")
                || folded.contains("ningun");
    }

    private static boolean functionSentinelAbstention(String folded) {
        return FUNCTION_SENTINEL_ABSTENTION.matcher(folded).find();
    }

    private static boolean filterListVagueAbsence(String folded) {
        return FILTER_LIST_VAGUE_ABSENCE.matcher(folded).find();
    }

    private static boolean answerContainsYear(String folded, int year) {
        Matcher m = YEAR_IN_ANSWER.matcher(folded);
        while (m.find()) {
            if (Integer.parseInt(m.group()) == year) {
                return true;
            }
        }
        return false;
    }

    /**
     * FILTER_AND_LIST tool answers often use slash/ISO dates (e.g. 25/08/2026) without repeating the Spanish
     * month name from the query (e.g. agosto). Accept encoded month numbers in date literals.
     */
    private static boolean answerSatisfiesSpanishMonthInDates(String folded, String monthName) {
        if (monthName == null || monthName.isBlank()) {
            return false;
        }
        Integer expected = SPANISH_MONTH_TO_NUMBER.get(monthName.toLowerCase(Locale.ROOT));
        if (expected == null) {
            return false;
        }
        Matcher slash = SLASH_DATE.matcher(folded);
        while (slash.find()) {
            if (slashDateMonthMatches(slash.group(), expected)) {
                return true;
            }
        }
        Matcher iso = ISO_DATE.matcher(folded);
        while (iso.find()) {
            try {
                if (Integer.parseInt(iso.group(2)) == expected) {
                    return true;
                }
            } catch (NumberFormatException ignored) {
                // continue
            }
        }
        Matcher prose = CONCRETE_ACTA_DATE.matcher(folded);
        while (prose.find()) {
            String proseMonth = prose.group(1).toLowerCase(Locale.ROOT);
            if (monthName.equalsIgnoreCase(proseMonth)) {
                return true;
            }
        }
        return false;
    }

    private static boolean slashDateMonthMatches(String slashDate, int expectedMonth) {
        String[] parts = slashDate.split("/");
        if (parts.length != 3) {
            return false;
        }
        try {
            return Integer.parseInt(parts[1]) == expectedMonth;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean mentionsConflictingMonth(String folded, Set<String> requiredMonths) {
        if (requiredMonths == null || requiredMonths.isEmpty() || isNegativeOrAbstentionAnswer(folded)) {
            return false;
        }
        Matcher m = SPANISH_MONTH.matcher(folded);
        while (m.find()) {
            String found = m.group(1).toLowerCase(Locale.ROOT);
            if (!requiredMonths.contains(found)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasConcreteActaReference(String folded) {
        if (YEAR_IN_ANSWER.matcher(folded).find()) {
            return true;
        }
        if (CONCRETE_ACTA_DATE.matcher(folded).find()) {
            return true;
        }
        if (SLASH_DATE.matcher(folded).find()) {
            return true;
        }
        return folded.contains(".pdf") || folded.matches(".*\\bacta\\s+\\d+\\b.*");
    }

    private static boolean hasCombinedTopicRoleConstraint(QueryConstraintSignals signals) {
        return signals.presidedByConstraint() && !signals.topicTokens().isEmpty();
    }

    private static Optional<String> primaryEntityToken(QueryConstraintSignals signals) {
        return signals.entityTokens().stream()
                .filter(entity -> entity != null && entity.contains(" "))
                .max(Comparator.comparingInt(String::length));
    }

    private static boolean topicEntityCoBoundInSameEvidenceUnit(String answerText, QueryConstraintSignals signals) {
        Optional<String> primaryEntity = primaryEntityToken(signals);
        if (signals.topicTokens().isEmpty() || primaryEntity.isEmpty()) {
            return true;
        }
        String entity = primaryEntity.get();
        for (String sentence : answerText.split("[.!?]")) {
            String unit = ExpectedAnswerNormalizer.normalizedFold(sentence);
            if (unit.isBlank()) {
                continue;
            }
            boolean hasTopic =
                    signals.topicTokens().stream().anyMatch(topic -> answerContainsTopicToken(unit, topic));
            boolean hasEntity = unit.contains(entity);
            if (hasTopic && hasEntity) {
                return true;
            }
        }
        return false;
    }

    private static boolean filterListTopicDescriptorMismatch(String answerText, Set<String> topicTokens) {
        if (topicTokens.isEmpty()) {
            return false;
        }
        String folded = ExpectedAnswerNormalizer.normalizedFold(answerText);
        boolean answerClaimsTopic =
                topicTokens.stream().anyMatch(topic -> answerContainsTopicToken(folded, topic));
        if (!answerClaimsTopic) {
            return false;
        }
        Matcher matcher = FILTER_LIST_FUERON_DESCRIPTOR.matcher(folded);
        while (matcher.find()) {
            String descriptor = ExpectedAnswerNormalizer.normalizedFold(matcher.group(1));
            if (descriptor.isBlank()) {
                continue;
            }
            boolean descriptorHasTopic =
                    topicTokens.stream().anyMatch(topic -> answerContainsTopicToken(descriptor, topic));
            if (!descriptorHasTopic) {
                return true;
            }
        }
        return false;
    }

    private static boolean mentionsListEvidence(String folded) {
        return folded.contains("acta") || folded.contains("reunion") || folded.contains(".pdf");
    }

    private static boolean hasCountableAnswer(String folded) {
        return CONCRETE_COUNT.matcher(folded).find()
                || folded.matches(".*\\b\\d+\\b.*")
                || folded.contains("dos")
                || folded.contains("tres")
                || folded.contains("cuatro")
                || folded.contains("una")
                || folded.contains("uno");
    }

    private static void addCanonicalFilterListRejection(List<String> failures) {
        boolean incomplete =
                failures.stream()
                        .anyMatch(
                                reason ->
                                        reason.startsWith("filter_list_missing_")
                                                || reason.startsWith("filter_list_topic_")
                                                || reason.equals("filter_list_unsupported_abstention")
                                                || reason.equals("function_sentinel_abstention")
                                                || reason.equals("filter_list_vague_absence")
                                                || reason.startsWith("topic_missing:")
                                                || reason.startsWith("entity_missing:")
                                                || reason.startsWith("year_constraint_missing:")
                                                || reason.startsWith("month_constraint_missing:"));
        if (incomplete && !failures.contains("function_filter_list_incomplete")) {
            failures.add("function_filter_list_incomplete");
        }
    }
}
