package com.uniovi.rag.infrastructure.llm;

import com.uniovi.rag.domain.llm.LlmProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmPropertiesTest {

    @Test
    void javaDefaultsMatchLegacyOllamaBaseline() {
        LlmProperties properties = new LlmProperties();
        assertEquals(LlmProvider.OLLAMA_NATIVE, properties.getDefaultProvider());
        assertEquals("http://localhost:11434", properties.getOllama().getDefaultBaseUrl());
        assertEquals("gemma3:4b", properties.getOllama().getDefaultChatModel());
        assertEquals("mxbai-embed-large:latest", properties.getOllama().getDefaultEmbeddingModel());
        assertEquals(60_000L, properties.getOllama().getDefaultTimeoutMs());
        assertEquals(0.1, properties.getOllama().getDefaultTemperature());
        assertEquals("OPENAI_COMPATIBLE_API_KEY", properties.getOpenAiCompatible().getDefaultApiKeyEnv());
    }

    @Test
    void uniformStackProviderWhenNoExplicitSplit() {
        LlmProperties properties = new LlmProperties();
        properties.setDefaultProvider(LlmProvider.OPENAI_COMPATIBLE);

        assertEquals(false, properties.hasExplicitProviderSplit());
        assertEquals(LlmProvider.OPENAI_COMPATIBLE, properties.getUniformStackProvider());
        assertEquals(LlmProvider.OPENAI_COMPATIBLE, properties.getEffectiveDefaultChatProvider());
        assertEquals(LlmProvider.OPENAI_COMPATIBLE, properties.getEffectiveDefaultEmbeddingProvider());
    }

    @Test
    void explicitSplitDetectedWhenChatProviderOverrideSet() {
        LlmProperties properties = new LlmProperties();
        properties.setDefaultProvider(LlmProvider.OLLAMA_NATIVE);
        properties.setDefaultChatProvider(LlmProvider.OPENAI_COMPATIBLE);

        assertEquals(true, properties.hasExplicitProviderSplit());
        assertEquals(LlmProvider.OPENAI_COMPATIBLE, properties.getEffectiveDefaultChatProvider());
        assertEquals(LlmProvider.OLLAMA_NATIVE, properties.getEffectiveDefaultEmbeddingProvider());
    }

    @Test
    void validatePassesForOllamaNativeDefaults() {
        LlmProperties properties = new LlmProperties();
        assertDoesNotThrow(properties::validate);
    }

    @Test
    void validateRequiresProvider() {
        LlmProperties properties = new LlmProperties();
        properties.setDefaultProvider(null);
        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void validateRequiresOllamaBaseUrl() {
        LlmProperties properties = new LlmProperties();
        properties.getOllama().setDefaultBaseUrl("  ");
        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void validateRequiresOpenAiApiKeyEnvNameEvenWhenOllamaActive() {
        LlmProperties properties = new LlmProperties();
        properties.getOpenAiCompatible().setDefaultApiKeyEnv("");
        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void validateRequiresOpenAiEndpointWhenProviderActive() {
        LlmProperties properties = new LlmProperties();
        properties.setDefaultProvider(LlmProvider.OPENAI_COMPATIBLE);
        properties.getOpenAiCompatible().setDefaultBaseUrl("");
        properties.getOpenAiCompatible().setDefaultChatModel("gpt-oss:20b");
        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void validateRequiresPositiveTimeout() {
        LlmProperties properties = new LlmProperties();
        properties.getOllama().setDefaultTimeoutMs(0);
        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void validateRejectsOutOfRangeTemperature() {
        LlmProperties properties = new LlmProperties();
        properties.getOllama().setDefaultTemperature(3.0);
        assertThrows(IllegalStateException.class, properties::validate);
    }

    @Test
    void validatePassesForOpenAiCompatibleWhenConfigured() {
        LlmProperties properties = new LlmProperties();
        properties.setDefaultProvider(LlmProvider.OPENAI_COMPATIBLE);
        properties.getOpenAiCompatible().setDefaultBaseUrl("http://156.35.160.78:4000");
        properties.getOpenAiCompatible().setDefaultChatModel("gpt-oss:20b");
        properties.getOpenAiCompatible().setDefaultEmbeddingModel("qwen3-embedding:8b");
        assertDoesNotThrow(properties::validate);
    }
}
