package com.uniovi.rag.domain.runtime.clarification;

import java.util.Objects;

/**
 * Policy output: whether to ask, selected question when asking, terminal outcome for this turn.
 */
public record ClarificationDecision(
        boolean ask,
        ClarificationOutcome terminalOutcome,
        ClarificationQuestion questionIfAsking,
        String policyTraceNote) {

    public ClarificationDecision {
        terminalOutcome = Objects.requireNonNull(terminalOutcome, "terminalOutcome");
        policyTraceNote = policyTraceNote != null ? policyTraceNote : "";
        if (ask) {
            Objects.requireNonNull(questionIfAsking, "questionIfAsking when ask=true");
        }
    }
}
