package com.uniovi.rag.application.service.runtime.memory;

import com.uniovi.rag.domain.runtime.memory.ConversationMemoryDecision;
import com.uniovi.rag.domain.runtime.memory.ConversationMemorySlice;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Deterministically selects the fixed bounded memory slice for the current turn (P12).
 */
@Service
public class ConversationMemorySelector {

    public ConversationMemorySlice selectSlice(
            List<ConversationMemoryTurn> eligibleHistory,
            ConversationMemoryDecision decision) {
        Objects.requireNonNull(eligibleHistory, "eligibleHistory");
        Objects.requireNonNull(decision, "decision");

        int max = decision.maxHistoryTurns();
        int n = eligibleHistory.size();
        if (n <= max) {
            return ConversationMemorySlice.of(eligibleHistory);
        }
        List<ConversationMemoryTurn> tail = eligibleHistory.subList(n - max, n);
        return ConversationMemorySlice.of(tail);
    }
}

