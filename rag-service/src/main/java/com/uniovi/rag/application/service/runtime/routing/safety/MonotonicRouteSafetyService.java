package com.uniovi.rag.application.service.runtime.routing.safety;

import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.functioncalling.FunctionCallingExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolExecutionResult;
import com.uniovi.rag.domain.runtime.tool.DeterministicToolOutcome;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

import com.uniovi.rag.domain.runtime.query.QueryPlan;

@Service
public class MonotonicRouteSafetyService {

    private final RouteCandidateConstraintValidator validator;

    public MonotonicRouteSafetyService(RouteCandidateConstraintValidator validator) {
        this.validator = validator;
    }

    public RouteCandidateValidationResult validateToolResult(
            QueryPlan plan, DeterministicToolExecutionResult toolResult) {
        if (toolResult == null
                || toolResult.outcome() != DeterministicToolOutcome.EXECUTED_SUCCESS
                || !toolResult.success()) {
            return RouteCandidateValidationResult.rejected("tool_not_successful");
        }
        return validator.validateToolOrFunctionAnswer(plan, toolResult.answerText(), toolResult.toolKind());
    }

    public RouteCandidateValidationResult validateFunctionResult(
            QueryPlan plan, FunctionCallingExecutionResult fcResult) {
        if (fcResult == null || fcResult.answerText() == null || fcResult.answerText().isBlank()) {
            return RouteCandidateValidationResult.rejected("function_empty");
        }
        return validator.validateToolOrFunctionAnswer(plan, fcResult.answerText(), fcResult.selectedToolKind());
    }

    public RouteCandidateValidationResult validateRetrievalAnswer(
            QueryPlan plan, String answer, boolean abstained) {
        return validator.validateRetrievalAnswer(plan, answer, abstained);
    }

    public record CandidateScore(
            String source,
            RouteCandidateValidationResult validation,
            String answerPreview) {}

    public Optional<CandidateScore> selectSafest(
            QueryPlan plan,
            Optional<CandidateScore> tool,
            Optional<CandidateScore> function,
            CandidateScore retrieval) {
        return selectSafest(plan, tool, function, retrieval, false);
    }

    public Optional<CandidateScore> selectSafest(
            QueryPlan plan,
            Optional<CandidateScore> tool,
            Optional<CandidateScore> function,
            CandidateScore retrieval,
            boolean advancedCandidateRejected) {
        QueryConstraintSignals signals = QueryConstraintSignals.fromPlan(plan);
        String queryText = (plan.rewrittenQueryText() == null ? "" : plan.rewrittenQueryText())
                + " "
                + (plan.normalizedQueryText() == null ? "" : plan.normalizedQueryText());
        queryText = queryText.toLowerCase(Locale.ROOT);
        boolean personActaVerify =
                signals.booleanVerify()
                        && (queryText.contains("aparece") || queryText.contains("figura"))
                        && (queryText.contains("acta") || queryText.contains("reunion") || queryText.contains("reunión"));
        boolean summarizeMeeting =
                plan.classifierQueryType().filter(qt -> qt == QueryType.SUMMARIZE_MEETING).isPresent()
                        || queryText.contains("resume la reunion")
                        || queryText.contains("resume la reunión");
        boolean preferRetrieval =
                (signals.booleanVerify() && !personActaVerify)
                        || signals.filterAndList()
                        || signals.absenceLikely();
        boolean functionSafe = function.filter(c -> c.validation().safe()).isPresent();
        if (advancedCandidateRejected
                && preferRetrieval
                && !functionSafe
                && retrieval.validation().safe()) {
            return Optional.of(retrieval);
        }

        List<CandidateScore> safe = new ArrayList<>();
        tool.filter(c -> c.validation().safe()).ifPresent(safe::add);
        function.filter(c -> c.validation().safe()).ifPresent(safe::add);
        if (retrieval.validation().safe()) {
            safe.add(retrieval);
        }
        if (safe.isEmpty()) {
            return Optional.empty();
        }
        if (summarizeMeeting) {
            for (CandidateScore c : safe) {
                if ("TOOL".equals(c.source())) {
                    return Optional.of(c);
                }
            }
        }
        if (preferRetrieval) {
            for (CandidateScore c : safe) {
                if ("RETRIEVAL".equals(c.source())) {
                    return Optional.of(c);
                }
            }
        }
        return safe.stream().max((a, b) -> Double.compare(a.validation().confidence(), b.validation().confidence()));
    }
}
