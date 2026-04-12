package com.uniovi.rag.tool.metadata;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MetadataLlmResponseCacheServiceTest {

    @Test
    void getCachedResponse_returnsEmpty_whenPromptBlank() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        MetadataLlmResponseCacheService svc = new MetadataLlmResponseCacheService(chatClient);

        assertThat(svc.getCachedResponse(null)).isEqualTo("");
        assertThat(svc.getCachedResponse("   ")).isEqualTo("");
        verifyNoInteractions(chatClient);
    }

    @Test
    void getCachedResponse_stripsNonEmptyResponse() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn("  OK  ");
        MetadataLlmResponseCacheService svc = new MetadataLlmResponseCacheService(chatClient);

        assertThat(svc.getCachedResponse("p")).isEqualTo("OK");
    }

    @Test
    void getCachedResponse_returnsEmpty_onIllegalArgumentException() {
        ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
        when(chatClient.prompt().user(anyString()).call().content()).thenThrow(new IllegalArgumentException("bad"));
        MetadataLlmResponseCacheService svc = new MetadataLlmResponseCacheService(chatClient);

        assertThat(svc.getCachedResponse("p")).isEqualTo("");
    }
}

