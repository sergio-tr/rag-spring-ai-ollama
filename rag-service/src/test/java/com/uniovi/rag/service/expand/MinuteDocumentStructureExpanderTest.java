package com.uniovi.rag.service.expand;

import com.uniovi.rag.testsupport.ChatClientTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class MinuteDocumentStructureExpanderTest {

    private ChatClient client;
    private MinuteDocumentStructureExpander expander;

    @BeforeEach
    void setUp() {
        client = ChatClientTestSupport.mockForUserPromptChain();
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
        ChatClientTestSupport.stubUserPromptReturns(client, "reunión acta fecha asistentes");

        String result = expander.expand("¿Quién presidió?");

        assertNotNull(result);
        assertTrue(result.contains("¿Quién presidió?"));
    }

    @Test
    void expand_llmThrowsReturnsOriginalOnly() {
        when(client.prompt().user(anyString())).thenThrow(new RuntimeException("LLM error"));

        String result = expander.expand("query");

        assertEquals("query", result);
    }
}
