package com.uniovi.rag.application.service.runtime.clarification;

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
            case AMBIGUOUS, MISSING_INFORMATION, CONFLICTING_CUES -> true;
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
}
