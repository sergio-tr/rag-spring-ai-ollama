package com.uniovi.rag.application.service.runtime.advisor;

import com.uniovi.rag.application.service.evaluation.metrics.matching.ExpectedAnswerNormalizer;
import com.uniovi.rag.application.service.runtime.factual.FactualAnswerVerifier;
import com.uniovi.rag.application.service.runtime.factual.FactualConstraintExtractor;
import com.uniovi.rag.application.service.runtime.factual.FactualQuestionConstraints;
import com.uniovi.rag.application.service.runtime.factual.FactualVerifierFailureReason;
import com.uniovi.rag.application.service.runtime.factual.FactualVerifierResult;
import com.uniovi.rag.application.service.runtime.routing.safety.QueryConstraintSignals;
import com.uniovi.rag.application.service.runtime.routing.safety.RouteCandidateConstraintValidator;
import com.uniovi.rag.application.service.runtime.routing.safety.RouteCandidateValidationResult;
import com.uniovi.rag.domain.runtime.advisor.PackedContextSet;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.judge.JudgeCandidateSource;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/**
 * Deterministic post-answer quality checks for advisor/judge stages: incomplete lists, false
 * abstentions, unsupported positives, and preservation of validated tool answers.
 */
@Service
public class AnswerQualityAdvisor {

    private static final Pattern ABSTENTION =
            Pattern.compile(
                    "\\bno consta\\b|\\bno hay (?:informacion|información|registro|datos)\\b|\\binsufficient evidence\\b|\\bno se mencion",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    private static final Pattern PARTIAL_ATTENDEE_LIST =
            Pattern.compile(
                    "\\b(?:participaron|asistieron|asistentes)\\b[^.]{0,120}\\b(?:y\\s+)?[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)?\\b",
                    Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);

    private final RouteCandidateConstraintValidator constraintValidator;

    public AnswerQualityAdvisor(RouteCandidateConstraintValidator constraintValidator) {
        this.constraintValidator = constraintValidator;
    }

    public AnswerQualityAssessment assess(
            ExecutionContext ctx,
            QueryPlan plan,
            String answerText,
            JudgeCandidateSource source,
            Optional<DeterministicToolKind> toolKind) {
        String answer = answerText != null ? answerText.trim() : "";
        if (answer.isBlank()) {
            return AnswerQualityAssessment.rejected(List.of("empty_answer"), false);
        }
        String context = resolveContextText(ctx);
        String folded = ExpectedAnswerNormalizer.normalizedFold(answer);
        QueryConstraintSignals signals = QueryConstraintSignals.fromPlan(plan);
        List<String> reasons = new ArrayList<>();

        RouteCandidateValidationResult constraintResult =
                source == JudgeCandidateSource.WORKFLOW
                        ? constraintValidator.validateRetrievalAnswer(plan, answer, isAbstention(folded))
                        : constraintValidator.validateToolOrFunctionAnswer(plan, answer, toolKind);
        if (!constraintResult.safe()) {
            reasons.addAll(constraintResult.rejectionReasons());
        }

        if (detectIncompleteParticipantList(plan, signals, answer, folded, reasons)) {
            reasons.add("incomplete_participant_list");
        }
        if (detectFalseAbstention(plan, signals, folded, reasons)) {
            reasons.add("false_abstention");
        }
        if (detectWrongDate(plan, folded, reasons)) {
            reasons.add("wrong_date_answer");
        }

        FactualQuestionConstraints factual =
                FactualConstraintExtractor.extract(plan.rewrittenQueryText(), plan, plan.classifierQueryType());
        if (!shouldSkipFactualVerification(source, toolKind, folded, answer)) {
            FactualVerifierResult factualResult = FactualAnswerVerifier.verify(factual, context, answer);
            if (!factualResult.passed()) {
                for (FactualVerifierFailureReason failure : factualResult.failures()) {
                    reasons.add("factual_" + failure.name().toLowerCase(Locale.ROOT));
                    if (failure == FactualVerifierFailureReason.DATE_MISMATCH) {
                        reasons.add("wrong_date_answer");
                    }
                    if (failure == FactualVerifierFailureReason.UNSUPPORTED_POSITIVE_CLAIM
                            || failure == FactualVerifierFailureReason.TOPIC_NOT_IN_CONTEXT
                            || failure == FactualVerifierFailureReason.ENTITY_NOT_IN_CONTEXT) {
                        reasons.add("unsupported_positive_answer");
                    }
                }
            }
        }

        boolean preserveToolAnswer =
                (source == JudgeCandidateSource.DETERMINISTIC_TOOL
                                || source == JudgeCandidateSource.FUNCTION_CALLING)
                        && constraintResult.safe()
                        && !reasons.contains("incomplete_participant_list");

        if (reasons.isEmpty()) {
            return AnswerQualityAssessment.accepted(preserveToolAnswer);
        }
        return AnswerQualityAssessment.rejected(reasons, preserveToolAnswer);
    }

    public static String resolveContextText(ExecutionContext ctx) {
        if (ctx == null) {
            return "";
        }
        Optional<PackedContextSet> packed = ctx.advisorPackedContextSet();
        if (packed.isPresent() && !packed.get().promptContextText().isBlank()) {
            return packed.get().promptContextText();
        }
        return "";
    }

    private static boolean shouldSkipFactualVerification(
            JudgeCandidateSource source,
            Optional<DeterministicToolKind> toolKind,
            String folded,
            String answer) {
        if (source != JudgeCandidateSource.DETERMINISTIC_TOOL
                || toolKind.filter(k -> k == DeterministicToolKind.GET_FIELD_TOOL).isEmpty()) {
            return false;
        }
        if (answer.contains("extracción de participantes está incompleta")
                || answer.contains("extraccion de participantes esta incompleta")) {
            return true;
        }
        boolean participantCount =
                folded.contains("participantes") && folded.contains("asistieron") && folded.matches(".*\\b\\d{1,3}\\b.*");
        boolean participantList =
                (folded.contains("participantes fueron") || folded.contains(" en total"))
                        && answer.contains(",");
        return participantCount || participantList;
    }

    private static boolean detectFalseAbstention(
            QueryPlan plan,
            QueryConstraintSignals signals,
            String folded,
            List<String> reasons) {
        if (signals.absenceLikely() || !isAbstention(folded)) {
            return false;
        }
        Optional<QueryType> qt = plan.classifierQueryType();
        boolean structuredQuery =
                signals.filterAndList()
                        || signals.booleanVerify()
                        || !signals.entityTokens().isEmpty()
                        || !signals.years().isEmpty()
                        || qt.filter(
                                        t ->
                                                t == QueryType.COUNT_DOCUMENTS
                                                        || t == QueryType.COUNT_AND_EXPLAIN
                                                        || t == QueryType.GET_FIELD
                                                        || t == QueryType.FILTER_AND_LIST)
                                .isPresent();
        return structuredQuery && !reasons.contains("false_abstention");
    }

    private static boolean detectWrongDate(QueryPlan plan, String folded, List<String> reasons) {
        if (plan.entityExtractionResult() == null || plan.entityExtractionResult().dates().isEmpty()) {
            return false;
        }
        for (String iso : plan.entityExtractionResult().dates()) {
            if (iso == null || iso.isBlank() || iso.length() < 4) {
                continue;
            }
            String year = iso.substring(0, 4);
            if (folded.contains(year)) {
                continue;
            }
            for (int y = 2018; y <= 2030; y++) {
                String candidate = String.valueOf(y);
                if (!candidate.equals(year) && folded.contains(candidate)) {
                    return !reasons.contains("wrong_date_answer");
                }
            }
        }
        return false;
    }

    private static boolean detectIncompleteParticipantList(
            QueryPlan plan,
            QueryConstraintSignals signals,
            String answer,
            String folded,
            List<String> reasons) {
        if (answer.contains("extracción de participantes está incompleta")
                || answer.contains("extraccion de participantes esta incompleta")) {
            return false;
        }
        String query =
                ((plan.rewrittenQueryText() != null ? plan.rewrittenQueryText() : "")
                                + " "
                                + (plan.normalizedQueryText() != null ? plan.normalizedQueryText() : ""))
                        .toLowerCase(Locale.ROOT);
        boolean attendeeEnumeration =
                (query.contains("asistieron")
                        || query.contains("participaron")
                        || query.contains("asistentes")
                        || query.contains("participantes")
                        || query.contains("enumera")
                        || query.contains("quienes"))
                        && !query.contains("cuantos")
                        && !query.contains("cuántos")
                        && !query.contains("cuantas")
                        && !query.contains("cuántas");
        if (!attendeeEnumeration) {
            return false;
        }
        if (reasons.stream().anyMatch(r -> r.contains("function_filter_list_incomplete"))) {
            return true;
        }
        if (!isAbstention(folded)) {
            boolean singleParticipant =
                    !answer.contains(",")
                            && PARTIAL_ATTENDEE_LIST.matcher(answer).find()
                            && !answer.toLowerCase(Locale.ROOT).contains(" y ");
            if (singleParticipant) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAbstention(String folded) {
        return folded != null && ABSTENTION.matcher(folded).find();
    }

    public record AnswerQualityAssessment(
            boolean acceptable,
            boolean preserveWithoutLlmJudge,
            boolean incompleteParticipantList,
            boolean falseAbstention,
            boolean unsupportedPositive,
            boolean wrongDate,
            List<String> reasons) {

        public static AnswerQualityAssessment accepted(boolean preserveWithoutLlmJudge) {
            return new AnswerQualityAssessment(
                    true, preserveWithoutLlmJudge, false, false, false, false, List.of());
        }

        public static AnswerQualityAssessment rejected(List<String> reasons, boolean preserveWithoutLlmJudge) {
            List<String> safe = List.copyOf(reasons != null ? reasons : List.of());
            return new AnswerQualityAssessment(
                    false,
                    preserveWithoutLlmJudge,
                    safe.contains("incomplete_participant_list")
                            || safe.stream().anyMatch(r -> r.contains("function_filter_list_incomplete")),
                    safe.contains("false_abstention"),
                    safe.contains("unsupported_positive_answer"),
                    safe.contains("wrong_date_answer"),
                    safe);
        }
    }
}
