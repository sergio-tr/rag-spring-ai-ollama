package com.uniovi.rag.application.service.runtime.memory;

import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryDecision;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryMode;
import com.uniovi.rag.domain.runtime.memory.ConversationMemorySlice;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationMemorySelectorTest {

    private final ConversationMemorySelector selector = new ConversationMemorySelector();

    @Test
    void selectSlice_picksLast6_andPreservesOrderOldestToNewestWithinSlice() {
        List<ConversationMemoryTurn> history = new ArrayList<>();
        for (int i = 1; i <= 10; i++) {
            history.add(new ConversationMemoryTurn(UUID.randomUUID(), i, i % 2 == 0 ? MessageRole.ASSISTANT : MessageRole.USER, "m" + i));
        }

        ConversationMemoryDecision decision =
                new ConversationMemoryDecision(
                        ConversationMemoryMode.ENABLED_CONDENSE_FOR_PLANNING,
                        true,
                        true,
                        ConversationMemoryDecision.FIXED_MAX_HISTORY_TURNS_P12,
                        List.of("enabled"));

        ConversationMemorySlice slice = selector.selectSlice(history, decision);
        assertThat(slice.totalTurnCount()).isEqualTo(6);
        assertThat(slice.turns()).extracting(ConversationMemoryTurn::seq).containsExactly(5, 6, 7, 8, 9, 10);
    }
}

