package com.uniovi.rag.application.service.runtime.memory;

import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryDecision;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryMode;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MemoryFollowUpTest {

    @Test
    void deterministicFollowUp_expandsEsaReunionBeforeCondensor() {
        ConversationMemoryPolicyResolver policy = mock(ConversationMemoryPolicyResolver.class);
        ConversationHistoryLoader loader = mock(ConversationHistoryLoader.class);
        ConversationMemorySelector selector = new ConversationMemorySelector();
        ConversationQuestionCondensor condensor = mock(ConversationQuestionCondensor.class);

        ConversationMemoryStrategy strategy =
                new ConversationMemoryStrategy(policy, loader, selector, condensor);

        ExecutionContext ctx = mock(ExecutionContext.class);
        when(ctx.userQuery()).thenReturn("¿Cuántos participantes asistieron a esa reunión?");

        when(policy.resolve(ctx))
                .thenReturn(
                        new ConversationMemoryDecision(
                                ConversationMemoryMode.ENABLED_CONDENSE_FOR_PLANNING,
                                true,
                                true,
                                ConversationMemoryDecision.FIXED_MAX_HISTORY_TURNS_P12,
                                List.of("enabled")));

        when(loader.loadEligibleHistory(ctx))
                .thenReturn(
                        List.of(
                                new ConversationMemoryTurn(
                                        UUID.randomUUID(),
                                        1,
                                        MessageRole.USER,
                                        "¿Quién fue el presidente en el acta del 25/02/2026?"),
                                new ConversationMemoryTurn(
                                        UUID.randomUUID(),
                                        2,
                                        MessageRole.ASSISTANT,
                                        "Jorge Moreno Navarro fue el presidente.")));

        var result = strategy.execute(ctx, "pre");
        assertThat(result.outcome()).isEqualTo(ConversationMemoryOutcome.MEMORY_APPLIED);
        assertThat(result.finalPlanningInputText())
                .isEqualTo("¿Cuántos participantes asistieron a la reunión del 25/02/2026?");
        verify(condensor, never()).condense(any(), any(), any(), any());
    }

    @Test
    void deterministicFollowUp_expandsEsaReunionAfterCountBeforeCondensor() {
        ConversationMemoryPolicyResolver policy = mock(ConversationMemoryPolicyResolver.class);
        ConversationHistoryLoader loader = mock(ConversationHistoryLoader.class);
        ConversationMemorySelector selector = new ConversationMemorySelector();
        ConversationQuestionCondensor condensor = mock(ConversationQuestionCondensor.class);

        ConversationMemoryStrategy strategy =
                new ConversationMemoryStrategy(policy, loader, selector, condensor);

        ExecutionContext ctx = mock(ExecutionContext.class);
        when(ctx.userQuery()).thenReturn("¿Cuántos participantes asistieron a esa reunión?");

        when(policy.resolve(ctx))
                .thenReturn(
                        new ConversationMemoryDecision(
                                ConversationMemoryMode.ENABLED_CONDENSE_FOR_PLANNING,
                                true,
                                true,
                                ConversationMemoryDecision.FIXED_MAX_HISTORY_TURNS_P12,
                                List.of("enabled")));

        when(loader.loadEligibleHistory(ctx))
                .thenReturn(
                        List.of(
                                new ConversationMemoryTurn(
                                        UUID.randomUUID(),
                                        1,
                                        MessageRole.USER,
                                        "La reunión del 25/02/2026"),
                                new ConversationMemoryTurn(
                                        UUID.randomUUID(),
                                        2,
                                        MessageRole.ASSISTANT,
                                        "En el acta del 25/02/2026 asistieron 17 participantes.")));

        var result = strategy.execute(ctx, "pre");
        assertThat(result.outcome()).isEqualTo(ConversationMemoryOutcome.MEMORY_APPLIED);
        assertThat(result.finalPlanningInputText()).contains("25/02/2026");
        verify(condensor, never()).condense(any(), any(), any(), any());
    }
}
