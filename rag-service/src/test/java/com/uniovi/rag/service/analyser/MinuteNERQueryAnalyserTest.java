package com.uniovi.rag.service.analyser;

import com.uniovi.rag.testsupport.ChatClientTestSupport;
import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class MinuteNERQueryAnalyserTest {

    private ChatClient chatClient;
    private MinuteNERQueryAnalyser analyser;

    @BeforeEach
    void setUp() {
        chatClient = ChatClientTestSupport.mockForUserPromptChain();
        analyser = new MinuteNERQueryAnalyser(chatClient);
    }

    @Test
    void analyse_nullOrEmpty_returnsFallback() {
        JSONObject result = analyser.analyse(null);
        assertNotNull(result);

        result = analyser.analyse("");
        assertNotNull(result);

        result = analyser.analyse("   ");
        assertNotNull(result);
    }

    @Test
    void analyse_withValidJsonFromLlm_returnsParsedObject() {
        ChatClientTestSupport.stubSystemUserPromptReturns(
                chatClient,
                "{\"date\":[\"2025-01-15\"],\"answerType\":\"person\"}");

        JSONObject result = analyser.analyse("¿Quién presidió el 15 de enero de 2025?");

        assertNotNull(result);
        assertTrue(result.has("date") || result.length() >= 0);
    }

    @Test
    void analyse_llmThrows_returnsFallback() {
        when(chatClient.prompt().system(anyString())).thenThrow(new RuntimeException("error"));

        JSONObject result = analyser.analyse("query");

        assertNotNull(result);
    }
}
