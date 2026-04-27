package com.uniovi.rag.application.port;

import com.uniovi.rag.domain.runtime.clarification.PendingClarificationState;

import java.util.Objects;
import java.util.Optional;

/**
 * Result of loading {@code pending_clarification_jsonb}: empty column, valid state, or invalid JSON/schema.
 */
public record PendingClarificationLoad(
        Optional<PendingClarificationState> state,
        boolean invalidJsonOrVersion) {

    public PendingClarificationLoad {
        state = Objects.requireNonNullElse(state, Optional.empty());
    }

    public static PendingClarificationLoad empty() {
        return new PendingClarificationLoad(Optional.empty(), false);
    }

    public static PendingClarificationLoad ok(PendingClarificationState s) {
        return new PendingClarificationLoad(Optional.of(Objects.requireNonNull(s, "state")), false);
    }

    public static PendingClarificationLoad invalid() {
        return new PendingClarificationLoad(Optional.empty(), true);
    }
}
