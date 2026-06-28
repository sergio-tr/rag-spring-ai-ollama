package com.uniovi.rag.application.service.runtime.memory;

import com.uniovi.rag.testsupport.llm.ChatGenerationModelSelectorTestSupport;
import com.uniovi.rag.domain.MessageRole;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.memory.ConversationMemorySlice;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryTurn;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.ArgumentMatchers;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationQuestionCondensorTest {

    @Test
    void condense_trimsOutput_andCallsChatClientOnce() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(ArgumentMatchers.anyString())
                .user(ArgumentMatchers.anyString())
                .options(ArgumentMatchers.any())
                .call()
                .content())
                .thenReturn("  condensed query  ");

        ConversationQuestionCondensor c =
                new ConversationQuestionCondensor(chatClient, ChatGenerationModelSelectorTestSupport.permissiveMock());
        ExecutionContext ctx = mock(ExecutionContext.class);
        when(ctx.chatModelOverride()).thenReturn(Optional.empty());

        ConversationMemorySlice slice =
                ConversationMemorySlice.of(
                        List.of(new ConversationMemoryTurn(UUID.randomUUID(), 1, MessageRole.USER, "hi")));

        String out = c.condense(ctx, slice, "latest", "pre");
        assertThat(out).isEqualTo("condensed query");
    }
}

