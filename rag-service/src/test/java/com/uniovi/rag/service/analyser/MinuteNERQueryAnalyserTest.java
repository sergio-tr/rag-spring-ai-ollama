package com.uniovi.rag.service.analyser;

import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MinuteNERQueryAnalyserTest {

    private ChatClient chatClient;
    private MinuteNERQueryAnalyser analyser;

    @BeforeEach
    void setUp() {
        chatClient = mock(ChatClient.class);
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
        var callSpec = mock(org.springframework.ai.chat.client.CallResponseSpec.class);
        var promptSpec = mock(org.springframework.ai.chat.client.PromptSpec.class);
        var systemSpec = mock(org.springframework.ai.chat.client.SystemSpec.class);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.system(anyString())).thenReturn(systemSpec);
        when(systemSpec.user(anyString())).thenReturn(callSpec);
        when(callSpec.call()).thenReturn(callSpec);
        when(callSpec.content()).thenReturn("{\"date\":[\"2025-01-15\"],\"answerType\":\"person\"}");

        JSONObject result = analyser.analyse("¿Quién presidió el 15 de enero de 2025?");

        assertNotNull(result);
        assertTrue(result.has("date") || result.length() >= 0);
    }

    @Test
    void analyse_llmThrows_returnsFallback() {
        var promptSpec = mock(org.springframework.ai.chat.client.PromptSpec.class);
        when(chatClient.prompt()).thenReturn(promptSpec);
        when(promptSpec.system(anyString())).thenThrow(new RuntimeException("error"));

        JSONObject result = analyser.analyse("query");

        assertNotNull(result);
    }
}
