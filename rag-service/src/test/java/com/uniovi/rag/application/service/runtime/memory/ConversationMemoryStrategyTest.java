package com.uniovi.rag.application.service.runtime.memory;

import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryDecision;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryExecutionResult;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryMode;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.memory.ConversationMemorySlice;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationMemoryStrategyTest {

    @Test
    void execute_fallsBackDeterministically_whenCondenseThrows() {
        ConversationMemoryPolicyResolver policy = mock(ConversationMemoryPolicyResolver.class);
        ConversationHistoryLoader loader = mock(ConversationHistoryLoader.class);
        ConversationMemorySelector selector = new ConversationMemorySelector();
        ConversationQuestionCondensor condensor = mock(ConversationQuestionCondensor.class);

        ConversationMemoryStrategy strategy = new ConversationMemoryStrategy(policy, loader, selector, condensor);

        ExecutionContext ctx = mock(ExecutionContext.class);
        when(ctx.userQuery()).thenReturn("latest");
        when(ctx.resolved()).thenThrow(new AssertionError("not needed"));

        when(policy.resolve(ctx)).thenReturn(
                new ConversationMemoryDecision(
                        ConversationMemoryMode.ENABLED_CONDENSE_FOR_PLANNING,
                        true,
                        true,
                        ConversationMemoryDecision.FIXED_MAX_HISTORY_TURNS_P12,
                        List.of("enabled")));

        List<ConversationMemoryTurn> history =
                List.of(
                        new ConversationMemoryTurn(UUID.randomUUID(), 1, MessageRole.USER, "a"),
                        new ConversationMemoryTurn(UUID.randomUUID(), 2, MessageRole.ASSISTANT, "b"));
        when(loader.loadEligibleHistory(ctx)).thenReturn(history);
        when(condensor.condense(ArgumentMatchers.eq(ctx), ArgumentMatchers.any(ConversationMemorySlice.class),
                ArgumentMatchers.anyString(), ArgumentMatchers.anyString()))
                .thenThrow(new RuntimeException("boom"));

        String pre = "pre-memory";
        ConversationMemoryExecutionResult out = strategy.execute(ctx, pre);

        assertThat(out.outcome()).isEqualTo(ConversationMemoryOutcome.CONDENSE_FAILED_FALLBACK);
        assertThat(out.finalPlanningInputText()).isEqualTo(pre);
        assertThat(out.fallbackApplied()).isTrue();
        assertThat(out.condensationAttempted()).isTrue();
        assertThat(out.condensationUsed()).isFalse();
        assertThat(out.stageTraces()).extracting(ExecutionStageTrace::stageName)
                .contains("memory_history_load", "memory_select_slice", "memory_condense", "memory_finalize_planning_input");
    }

    @Test
    void execute_noHistory_skipsCondense_andReturnsNoHistoryOutcome() {
        ConversationMemoryPolicyResolver policy = mock(ConversationMemoryPolicyResolver.class);
        ConversationHistoryLoader loader = mock(ConversationHistoryLoader.class);
        ConversationMemorySelector selector = mock(ConversationMemorySelector.class);
        ConversationQuestionCondensor condensor = mock(ConversationQuestionCondensor.class);

        ConversationMemoryStrategy strategy = new ConversationMemoryStrategy(policy, loader, selector, condensor);

        ExecutionContext ctx = mock(ExecutionContext.class);
        when(policy.resolve(ctx)).thenReturn(
                new ConversationMemoryDecision(
                        ConversationMemoryMode.ENABLED_CONDENSE_FOR_PLANNING,
                        true,
                        true,
                        ConversationMemoryDecision.FIXED_MAX_HISTORY_TURNS_P12,
                        List.of("enabled")));

        when(loader.loadEligibleHistory(ctx)).thenReturn(List.of());

        ConversationMemoryExecutionResult out = strategy.execute(ctx, "pre");
        assertThat(out.outcome()).isEqualTo(ConversationMemoryOutcome.NO_HISTORY_AVAILABLE);
        verify(selector, never()).selectSlice(ArgumentMatchers.anyList(), ArgumentMatchers.any());
        verify(condensor, never()).condense(ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any(), ArgumentMatchers.any());
    }
}

