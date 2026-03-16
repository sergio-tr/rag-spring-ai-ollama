package com.uniovi.rag.service.expand;

import com.uniovi.rag.model.ExpansionStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MinuteDocumentStructureExpanderTest {

    private ChatClient client;
    private MinuteDocumentStructureExpander expander;

    @BeforeEach
    void setUp() {
        client = mock(ChatClient.class);
        expander = new MinuteDocumentStructureExpander(client);
    }

    @Test
    void expand_nullReturnsEmpty() {
        assertEquals("", expander.expand(null));
    }

    @Test
    void expand_blankReturnsAsIs() {
        assertEquals("", expander.expand(""));
        assertEquals("   ", expander.expand("   "));
    }

    @Test
    void expand_withMockedLlmReturnsOriginalPlusExpansion() {
        var callSpec = mock(org.springframework.ai.chat.client.CallResponseSpec.class);
        var promptSpec = mock(org.springframework.ai.chat.client.PromptSpec.class);
        when(client.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenReturn(callSpec);
        when(callSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("reunión acta fecha asistentes");

        String result = expander.expand("¿Quién presidió?");

        assertNotNull(result);
        assertTrue(result.contains("¿Quién presidió?"));
    }

    @Test
    void expand_llmThrowsReturnsOriginalOnly() {
        var promptSpec = mock(org.springframework.ai.chat.client.PromptSpec.class);
        when(client.prompt()).thenReturn(promptSpec);
        when(promptSpec.user(anyString())).thenThrow(new RuntimeException("LLM error"));

        String result = expander.expand("query");

        assertEquals("query", result);
    }
}
