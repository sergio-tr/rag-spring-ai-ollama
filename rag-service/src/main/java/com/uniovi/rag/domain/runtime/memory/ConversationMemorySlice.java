package com.uniovi.rag.domain.runtime.memory;

import java.util.List;
import java.util.Objects;

/**
 * Bounded slice of conversation history used by the memory stage for the current turn (P12).
 */
public record ConversationMemorySlice(
        List<ConversationMemoryTurn> turns,
        int totalTurnCount,
        int totalCharCount) {

    public ConversationMemorySlice {
        turns = List.copyOf(Objects.requireNonNull(turns, "turns"));
        if (totalTurnCount < 0) {
            throw new IllegalArgumentException("totalTurnCount must be >= 0");
        }
        if (totalCharCount < 0) {
            throw new IllegalArgumentException("totalCharCount must be >= 0");
        }
        if (totalTurnCount != turns.size()) {
            throw new IllegalArgumentException("totalTurnCount must equal turns.size()");
        }
    }

    public static ConversationMemorySlice of(List<ConversationMemoryTurn> turns) {
        int chars = 0;
        for (ConversationMemoryTurn t : turns) {
            chars += t.content() == null ? 0 : t.content().length();
        }
        return new ConversationMemorySlice(List.copyOf(turns), turns.size(), chars);
    }
}

