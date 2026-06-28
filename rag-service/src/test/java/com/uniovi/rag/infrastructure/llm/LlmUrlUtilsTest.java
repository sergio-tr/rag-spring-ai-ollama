package com.uniovi.rag.infrastructure.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class LlmUrlUtilsTest {

    @Test
    void buildsChatCompletionsUrlWithoutDuplicateSlash() {
        assertEquals(
                "http://156.35.160.78:4000/v1/chat/completions",
                LlmUrlUtils.openAiChatCompletionsUrl("http://156.35.160.78:4000/"));
    }

    @Test
    void onlyExposesChatCompletionsPath() {
        assertEquals(
                "http://proxy.example/v1/chat/completions",
                LlmUrlUtils.openAiChatCompletionsUrl("http://proxy.example"));
        assertFalse(LlmUrlUtils.openAiChatCompletionsUrl("http://proxy.example").contains("/v1/models"));
    }
}
