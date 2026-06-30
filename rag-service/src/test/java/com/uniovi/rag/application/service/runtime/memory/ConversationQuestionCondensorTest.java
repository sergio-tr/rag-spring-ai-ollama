package com.uniovi.rag.application.service.runtime.memory;

import com.uniovi.rag.application.service.llm.ProviderAwareSecondaryLlmExecutor;
import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.memory.ConversationMemorySlice;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.uniovi.rag.testsupport.config.TestConfigurablePromptResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationQuestionCondensorTest {

    @Mock private ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor;

    @Test
    void condense_trimsOutput_andCallsSecondaryExecutorOnce() {
        when(secondaryLlmExecutor.complete(
                        any(ExecutionContext.class),
                        eq("conversation-condense"),
                        anyString(),
                        anyString(),
                        eq(ProviderAwareSecondaryLlmExecutor.SECONDARY_TASK_DEFAULT_TEMPERATURE)))
                .thenReturn("  condensed query  ");

        ConversationQuestionCondensor c = new ConversationQuestionCondensor(secondaryLlmExecutor, TestConfigurablePromptResolver.defaultsOnly());
        ExecutionContext ctx = mock(ExecutionContext.class);

        ConversationMemorySlice slice =
                ConversationMemorySlice.of(
                        List.of(new ConversationMemoryTurn(UUID.randomUUID(), 1, MessageRole.USER, "hi")));

        String out = c.condense(ctx, slice, "latest", "pre");
        assertThat(out).isEqualTo("condensed query");
        verify(secondaryLlmExecutor, times(1))
                .complete(
                        eq(ctx),
                        eq("conversation-condense"),
                        anyString(),
                        anyString(),
                        eq(ProviderAwareSecondaryLlmExecutor.SECONDARY_TASK_DEFAULT_TEMPERATURE));
    }

    @Test
    void condense_skipsLlmWhenFollowUpResolverExpandsDeterministically() {
        ConversationQuestionCondensor c = new ConversationQuestionCondensor(secondaryLlmExecutor, TestConfigurablePromptResolver.defaultsOnly());
        ExecutionContext ctx = mock(ExecutionContext.class);

        ConversationMemorySlice slice =
                ConversationMemorySlice.of(
                        List.of(
                                new ConversationMemoryTurn(
                                        UUID.randomUUID(),
                                        1,
                                        MessageRole.USER,
                                        "reunión del 15/01/2025"),
                                new ConversationMemoryTurn(
                                        UUID.randomUUID(),
                                        2,
                                        MessageRole.ASSISTANT,
                                        "ok")));

        String out = c.condense(ctx, slice, "¿qué se decidió en esa reunión?", "pre");
        assertThat(out).contains("15/01/2025");
        verifyNoInteractions(secondaryLlmExecutor);
    }
}
