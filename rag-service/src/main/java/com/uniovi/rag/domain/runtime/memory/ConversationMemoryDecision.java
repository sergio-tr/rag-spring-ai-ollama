package com.uniovi.rag.domain.runtime.memory;

import java.util.List;
import java.util.Objects;

/**
 * Pure policy output determining whether memory/condensation is attempted for the current turn (P12).
 */
public record ConversationMemoryDecision(
        ConversationMemoryMode mode,
        boolean attemptMemory,
        boolean attemptCondensation,
        int maxHistoryTurns,
        List<String> reasons) {

    public static final int FIXED_MAX_HISTORY_TURNS_P12 = 6;

    public ConversationMemoryDecision {
        mode = Objects.requireNonNull(mode, "mode");
        reasons = List.copyOf(Objects.requireNonNull(reasons, "reasons"));
        if (maxHistoryTurns != FIXED_MAX_HISTORY_TURNS_P12) {
            throw new IllegalArgumentException("maxHistoryTurns must be fixed to " + FIXED_MAX_HISTORY_TURNS_P12);
        }
        if (!attemptMemory && attemptCondensation) {
            throw new IllegalArgumentException("attemptCondensation requires attemptMemory=true");
        }
    }
}

