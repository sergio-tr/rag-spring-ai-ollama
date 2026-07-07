package com.uniovi.rag.application.service.runtime.clarification;

import com.uniovi.rag.application.service.runtime.optimization.DeterministicQueryRewriteShortcuts;
import com.uniovi.rag.application.service.runtime.query.ActaFieldAnchorHeuristics;
import com.uniovi.rag.application.service.runtime.query.IncompleteQueryHeuristics;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.clarification.ClarificationDecision;
import com.uniovi.rag.domain.runtime.clarification.ClarificationOutcome;
import com.uniovi.rag.domain.runtime.clarification.ClarificationQuestion;
import com.uniovi.rag.domain.runtime.clarification.ClarificationQuestionKind;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.query.AmbiguityStatus;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Frozen ambiguity policy matrix, gates (via {@link ExecutionContext#clarificationDisableReason()}), and kind priority (P11).
 */
@Service
public class ClarificationPolicyResolver {

    private final ClarificationQuestionGenerator clarificationQuestionGenerator;

    public ClarificationPolicyResolver(ClarificationQuestionGenerator clarificationQuestionGenerator) {
        this.clarificationQuestionGenerator = clarificationQuestionGenerator;
    }

    public ClarificationDecision resolve(ExecutionContext ctx, QueryPlan plan) {
        if (ctx.invalidPendingRecoveredThisTurn()) {
            return new ClarificationDecision(
                    false,
                    ClarificationOutcome.INVALID_PENDING_STATE_RECOVERED,
                    null,
                    "invalid_pending_cleared_before_qu");
        }
        Optional<String> disableReason = ctx.clarificationDisableReason();
        if (disableReason.isPresent()) {
            String d = disableReason.get();
            return new ClarificationDecision(
                    false, ClarificationOutcome.DISABLED_BY_CONFIG, null, "disable_reason=" + d);
        }

        Optional<IncompleteQueryHeuristics.Signal> incomplete = IncompleteQueryHeuristics.detect(plan);
        if (incomplete.isPresent()) {
            ClarificationQuestionKind kind =
                    switch (incomplete.get().reason()) {
                        case INCOMPLETE_COUNT_FILTER, TRAILING_RELATIVE_CLAUSE ->
                                ClarificationQuestionKind.GENERIC_MISSING_INFORMATION;
                        case TRAILING_PREPOSITION -> ClarificationQuestionKind.MISSING_DATE;
                    };
            ClarificationQuestion q = clarificationQuestionGenerator.questionForKind(kind, plan);
            ClarificationOutcome askOutcome =
                    ctx.validPendingExistedAtLoad()
                            ? ClarificationOutcome.ASKED_CLARIFICATION_AGAIN
                            : ClarificationOutcome.ASKED_CLARIFICATION;
            return new ClarificationDecision(
                    true, askOutcome, q, incomplete.get().traceNote());
        }

        if (isCompoundMonthTopicAttendeeFilterQuery(plan)) {
            return new ClarificationDecision(
                    false, ClarificationOutcome.NOT_NEEDED, null, "compound_month_topic_attendee_filter");
        }

        if (isCorpusWideExactAttendeeCountListingQuery(plan)) {
            return new ClarificationDecision(
                    false, ClarificationOutcome.NOT_NEEDED, null, "corpus_wide_exact_attendee_count_listing");
        }

        if (isCorpusWideListingQuery(plan)) {
            return new ClarificationDecision(
                    false, ClarificationOutcome.NOT_NEEDED, null, "corpus_wide_listing_exempt");
        }

        AmbiguityStatus status = plan.ambiguityAssessment().status();
        if (!ambiguityRequiresClarification(status)) {
            if (ctx.pendingClarificationLoadedForTrace()) {
                return new ClarificationDecision(
                        false, ClarificationOutcome.RESOLVED_FROM_PENDING, null, "continuation_resolved");
            }
            return new ClarificationDecision(false, ClarificationOutcome.NOT_NEEDED, null, "");
        }

        Optional<ClarificationQuestionKind> kind = selectKind(plan);
        if (kind.isEmpty()) {
            return new ClarificationDecision(
                    false,
                    ClarificationOutcome.NOT_NEEDED,
                    null,
                    "clarification_skipped_no_deterministic_question");
        }

        ClarificationQuestion q = clarificationQuestionGenerator.questionForKind(kind.get(), plan);
        ClarificationOutcome askOutcome =
                ctx.validPendingExistedAtLoad()
                        ? ClarificationOutcome.ASKED_CLARIFICATION_AGAIN
                        : ClarificationOutcome.ASKED_CLARIFICATION;
        return new ClarificationDecision(true, askOutcome, q, "");
    }

    private static boolean ambiguityRequiresClarification(AmbiguityStatus status) {
        return switch (status) {
            case AMBIGUOUS, MISSING_INFORMATION -> true;
            case CONFLICTING_CUES -> false;
            case SUFFICIENT, UNKNOWN -> false;
        };
    }

    /**
     * Frozen kind selection priority; first match wins.
     */
    Optional<ClarificationQuestionKind> selectKind(QueryPlan plan) {
        List<String> missingLower =
                plan.ambiguityAssessment().missingFields().stream()
                        .map(s -> s.toLowerCase(Locale.ROOT))
                        .toList();
        AmbiguityStatus status = plan.ambiguityAssessment().status();

        for (String f : missingLower) {
            if (containsAnySubstring(f, "filter", "condition", "criteria", "predicate")) {
                return Optional.of(ClarificationQuestionKind.GENERIC_MISSING_INFORMATION);
            }
        }
        for (String f : missingLower) {
            if (containsAnySubstring(f, "date", "time", "deadline")) {
                return Optional.of(ClarificationQuestionKind.MISSING_DATE);
            }
        }
        for (String f : missingLower) {
            if (containsAnySubstring(f, "person", "user", "author", "speaker", "attendee", "owner")) {
                return Optional.of(ClarificationQuestionKind.MISSING_PERSON);
            }
        }
        for (String f : missingLower) {
            if (containsAnySubstring(f, "topic", "subject", "theme", "title")) {
                return Optional.of(ClarificationQuestionKind.MISSING_TOPIC);
            }
        }
        for (String f : missingLower) {
            if (containsAnySubstring(f, "location", "place", "venue", "room")) {
                return Optional.of(ClarificationQuestionKind.MISSING_LOCATION);
            }
        }
        if (status == AmbiguityStatus.CONFLICTING_CUES) {
            return Optional.of(ClarificationQuestionKind.CONFLICTING_CUES);
        }
        if (status == AmbiguityStatus.AMBIGUOUS) {
            return Optional.of(ClarificationQuestionKind.MULTIPLE_ENTITY_CANDIDATES);
        }
        if (status == AmbiguityStatus.MISSING_INFORMATION) {
            return Optional.of(ClarificationQuestionKind.GENERIC_MISSING_INFORMATION);
        }
        return Optional.empty();
    }

    private static boolean containsAnySubstring(String fieldLower, String... needles) {
        for (String n : needles) {
            if (fieldLower.contains(n)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCorpusWideListingQuery(QueryPlan plan) {
        if (plan == null) {
            return false;
        }
        for (String candidate :
                List.of(plan.normalizedQueryText(), plan.rewrittenQueryText(), plan.rawUserQuery())) {
            if (candidate == null || candidate.isBlank()) {
                continue;
            }
            String q = candidate.toLowerCase(Locale.ROOT);
            if (!ActaFieldAnchorHeuristics.isCorpusWideAggregate(q)
                    && DeterministicQueryRewriteShortcuts.matches(candidate).isEmpty()) {
                continue;
            }
            Optional<QueryType> classifier = plan.classifierQueryType();
            if (classifier.isPresent()) {
                QueryType type = classifier.get();
                if (type == QueryType.FILTER_AND_LIST
                        || type == QueryType.COUNT_DOCUMENTS
                        || type == QueryType.FIND_PARAGRAPH
                        || type == QueryType.SUMMARIZE_TOPIC
                        || type == QueryType.COUNT_AND_EXPLAIN
                        || type == QueryType.COMPARE) {
                    return true;
                }
            }
            if (DeterministicQueryRewriteShortcuts.matches(candidate).isPresent()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCorpusWideExactAttendeeCountListingQuery(QueryPlan plan) {
        if (plan == null) {
            return false;
        }
        for (String candidate :
                List.of(plan.normalizedQueryText(), plan.rewrittenQueryText(), plan.rawUserQuery())) {
            if (candidate != null
                    && ActaFieldAnchorHeuristics.isCorpusWideExactAttendeeCountListing(
                            candidate.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCompoundMonthTopicAttendeeFilterQuery(QueryPlan plan) {
        if (plan == null) {
            return false;
        }
        for (String candidate :
                List.of(plan.normalizedQueryText(), plan.rewrittenQueryText(), plan.rawUserQuery())) {
            if (candidate != null
                    && ActaFieldAnchorHeuristics.isCompoundMonthTopicAttendeeFilter(
                            candidate.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }
}
