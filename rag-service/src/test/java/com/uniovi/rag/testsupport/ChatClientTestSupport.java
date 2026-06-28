package com.uniovi.rag.testsupport;

import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Mockito deep stubs for Spring AI {@link ChatClient} fluent chains (types like CallResponseSpec are internal).
 */
public final class ChatClientTestSupport {

    private ChatClientTestSupport() {
    }

    public static ChatClient mockForUserPromptChain() {
        return mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
    }

    public static void stubUserPromptReturns(ChatClient client, String content) {
        when(client.prompt().user(anyString()).call().content()).thenReturn(content);
        when(client.prompt().user(anyString()).options(any()).call().content()).thenReturn(content);
    }

    /** Makes the common {@code prompt().user(any).call().content()} chain throw (e.g. Ollama down). */
    public static void stubUserPromptThrows(ChatClient client, Throwable t) {
        when(client.prompt().user(anyString()).call().content()).thenThrow(t);
        when(client.prompt().user(anyString()).options(any()).call().content()).thenThrow(t);
    }

    public static void stubSystemUserPromptReturns(ChatClient client, String content) {
        when(client.prompt().system(anyString()).user(anyString()).call().content()).thenReturn(content);
        when(client.prompt().system(anyString()).user(anyString()).options(any()).call().content()).thenReturn(content);
    }

    public static ChatClient clientWithUserPromptReturning(String content) {
        ChatClient c = mockForUserPromptChain();
        stubUserPromptReturns(c, content);
        return c;
    }
}
