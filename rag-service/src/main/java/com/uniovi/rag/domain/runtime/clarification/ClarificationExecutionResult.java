package com.uniovi.rag.domain.runtime.clarification;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;

import java.util.List;
import java.util.Objects;

/**
 * Result of {@code ClarificationStrategy.execute}: outcome, assistant text for ASKED_* only, stage traces for orchestrator merge.
 */
public record ClarificationExecutionResult(
        ClarificationOutcome outcome,
        String answerText,
        List<ExecutionStageTrace> stageTraces) {

    public ClarificationExecutionResult {
        outcome = Objects.requireNonNull(outcome, "outcome");
        answerText = answerText != null ? answerText : "";
        stageTraces = List.copyOf(Objects.requireNonNull(stageTraces, "stageTraces"));
    }
}
