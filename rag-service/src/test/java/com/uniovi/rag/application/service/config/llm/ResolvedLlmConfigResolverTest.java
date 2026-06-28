package com.uniovi.rag.application.service.config.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.domain.llm.LlmConfigurationKeys;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ResolvedLlmConfigResolverTest {

    @Mock
    private ConfigurationSourcePort configurationSource;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LlmProperties llmProperties = new LlmProperties();
    private LlmModelCatalogService modelCatalog;
    private final UUID userId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    private ResolvedLlmConfigResolver resolver;

    @BeforeEach
    void setUp() {
        modelCatalog = new LlmModelCatalogService(llmProperties);
        resolver = new ResolvedLlmConfigResolver(configurationSource, llmProperties, objectMapper, modelCatalog);
    }

    @Test
    void resolve_withoutUser_usesApplicationOllamaDefaults() {
        ResolvedLlmConfig config = resolver.resolve(null, null, null);

        assertEquals(LlmProvider.OLLAMA_NATIVE, config.chatProvider());
        assertEquals(LlmProvider.OLLAMA_NATIVE, config.embeddingProvider());
        assertEquals(LlmProvider.OLLAMA_NATIVE, config.provider());
        assertEquals("http://localhost:11434", config.baseUrl());
        assertEquals("gemma3:4b", config.chatModel());
        verify(configurationSource, never()).loadUserDefault(any());
    }

    @Test
    void resolve_userLayerOverridesChatModel() {
        llmProperties.getOllama().setAvailableChatModels(List.of("gemma3:4b", "custom-model"));
        modelCatalog = new LlmModelCatalogService(llmProperties);
        resolver = new ResolvedLlmConfigResolver(configurationSource, llmProperties, objectMapper, modelCatalog);
        when(configurationSource.loadUserDefault(eq(userId)))
                .thenReturn(Optional.of(Map.of(LlmConfigurationKeys.CHAT_MODEL, "custom-model")));

        ResolvedLlmConfig config = resolver.resolve(userId, null, null);

        assertEquals("custom-model", config.chatModel());
        assertEquals(LlmProvider.OLLAMA_NATIVE, config.chatProvider());
        assertEquals(LlmProvider.OLLAMA_NATIVE, config.embeddingProvider());
        assertEquals(LlmProvider.OLLAMA_NATIVE, config.provider());
    }

    @Test
    void resolve_userLayerOllamaModelNotInCatalog_fallsBackToOllamaDefault() {
        when(configurationSource.loadUserDefault(eq(userId)))
                .thenReturn(Optional.of(Map.of(LlmConfigurationKeys.CHAT_MODEL, "custom-model")));

        ResolvedLlmConfig config = resolver.resolve(userId, null, null);

        assertEquals("gemma3:4b", config.chatModel());
    }

    @Test
    void resolve_openAiCompatibleFromUser_requiresApiKeyEnvReference() {
        configureOpenAiCatalog("gpt-oss:20b");
        when(configurationSource.loadUserDefault(eq(userId)))
                .thenReturn(
                        Optional.of(
                                Map.of(
                                        LlmConfigurationKeys.PROVIDER,
                                        "OPENAI_COMPATIBLE",
                                        LlmConfigurationKeys.BASE_URL,
                                        "http://litellm:4000",
                                        LlmConfigurationKeys.CHAT_MODEL,
                                        "gpt-oss:20b",
                                        LlmConfigurationKeys.API_KEY_ENV,
                                        "MY_LLM_KEY")));

        ResolvedLlmConfig config = resolver.resolve(userId, null, null);

        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.chatProvider());
        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.embeddingProvider());
        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.provider());
        assertEquals("http://litellm:4000", config.baseUrl());
        assertEquals("gpt-oss:20b", config.chatModel());
        assertEquals("MY_LLM_KEY", config.apiKeyEnv());
    }

    @Test
    void resolve_openAiCompatibleFromUser_fallsBackToDefaultApiKeyEnv() {
        configureOpenAiCatalog("gpt-oss:20b");
        when(configurationSource.loadUserDefault(eq(userId)))
                .thenReturn(
                        Optional.of(
                                Map.of(
                                        LlmConfigurationKeys.PROVIDER,
                                        "OPENAI_COMPATIBLE",
                                        LlmConfigurationKeys.BASE_URL,
                                        "http://litellm:4000",
                                        LlmConfigurationKeys.CHAT_MODEL,
                                        "gpt-oss:20b")));

        ResolvedLlmConfig config = resolver.resolve(userId, null, null);

        assertEquals("OPENAI_COMPATIBLE_API_KEY", config.apiKeyEnv());
    }

    @Test
    void resolve_openAiCompatibleFromUser_unlistedChatModelUsesOpenAiDefaultNotOllama() {
        configureOpenAiCatalog("gpt-oss:20b");
        llmProperties.getOllama().setDefaultChatModel("gemma3:4b");
        when(configurationSource.loadUserDefault(eq(userId)))
                .thenReturn(
                        Optional.of(
                                Map.of(
                                        LlmConfigurationKeys.PROVIDER,
                                        "OPENAI_COMPATIBLE",
                                        LlmConfigurationKeys.BASE_URL,
                                        "http://litellm:4000",
                                        LlmConfigurationKeys.CHAT_MODEL,
                                        "gemma3:4b")));

        ResolvedLlmConfig config = resolver.resolve(userId, null, null);

        assertEquals("gpt-oss:20b", config.chatModel());
        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.chatProvider());
    }

    @Test
    void resolve_openAiCompatibleWithoutAnyKeyReference_failsValidation() {
        configureOpenAiCatalog("gpt-oss:20b");
        llmProperties.getOpenAiCompatible().setDefaultApiKeyEnv("");
        when(configurationSource.loadUserDefault(eq(userId)))
                .thenReturn(
                        Optional.of(
                                Map.of(
                                        LlmConfigurationKeys.PROVIDER,
                                        "OPENAI_COMPATIBLE",
                                        LlmConfigurationKeys.BASE_URL,
                                        "http://litellm:4000",
                                        LlmConfigurationKeys.CHAT_MODEL,
                                        "gpt-oss:20b")));

        assertThrows(IllegalStateException.class, () -> resolver.resolve(userId, null, null));
    }

    @Test
    void requireApiKeyEnvResolvable_missingEnvVar_throwsClearMessage() {
        ResolvedLlmConfig config =
                ResolvedLlmConfig.uniform(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "http://litellm:4000",
                        "gpt-4o",
                        "embed-model",
                        "MISSING_ENV_FOR_TEST",
                        null,
                        0.1,
                        60_000,
                        null,
                        Map.of());

        IllegalStateException ex =
                assertThrows(IllegalStateException.class, config::requireApiKeyEnvResolvable);
        assertEquals(
                "API key environment variable is not set or empty: MISSING_ENV_FOR_TEST"
                        + " (configure the secret externally; never store keys in the database)",
                ex.getMessage());
    }

    @Test
    void resolve_applicationDefaultsOpenAi_fillsFromProperties() {
        llmProperties.setDefaultProvider(LlmProvider.OPENAI_COMPATIBLE);
        LlmOpenAiCompatibleDefaults openAi = llmProperties.getOpenAiCompatible();
        openAi.setDefaultBaseUrl("http://default-litellm:4000");
        openAi.setDefaultChatModel("gpt-oss:20b");
        openAi.setAvailableChatModels(List.of("gpt-oss:20b"));
        openAi.setDefaultEmbeddingModel("qwen3-embedding:8b");
        openAi.setAvailableEmbeddingModels(List.of("qwen3-embedding:8b"));
        openAi.setDefaultApiKeyEnv("DEFAULT_KEY_ENV");
        modelCatalog = new LlmModelCatalogService(llmProperties);
        resolver = new ResolvedLlmConfigResolver(configurationSource, llmProperties, objectMapper, modelCatalog);

        ResolvedLlmConfig config = resolver.resolve(null, null, null);

        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.chatProvider());
        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.embeddingProvider());
        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.provider());
        assertEquals("http://default-litellm:4000", config.baseUrl());
        assertEquals("gpt-oss:20b", config.chatModel());
        assertEquals("DEFAULT_KEY_ENV", config.apiKeyEnv());
    }

    @Test
    void resolve_ollamaUserDoesNotRequireApiKeyEnv() {
        llmProperties.getOllama().setAvailableChatModels(List.of("gemma3:4b", "ollama-user-model"));
        modelCatalog = new LlmModelCatalogService(llmProperties);
        resolver = new ResolvedLlmConfigResolver(configurationSource, llmProperties, objectMapper, modelCatalog);
        when(configurationSource.loadUserDefault(eq(userId)))
                .thenReturn(Optional.of(Map.of(LlmConfigurationKeys.CHAT_MODEL, "ollama-user-model")));

        ResolvedLlmConfig config = resolver.resolve(userId, null, null);

        assertEquals(LlmProvider.OLLAMA_NATIVE, config.chatProvider());
        assertEquals(LlmProvider.OLLAMA_NATIVE, config.embeddingProvider());
        assertEquals(LlmProvider.OLLAMA_NATIVE, config.provider());
        assertEquals("ollama-user-model", config.chatModel());
        assertNull(config.apiKeyEnv());
        config.requireApiKeyEnvResolvable();
    }

    private void configureOpenAiCatalog(String chatModel) {
        LlmOpenAiCompatibleDefaults openAi = llmProperties.getOpenAiCompatible();
        openAi.setDefaultBaseUrl("http://litellm:4000");
        openAi.setDefaultChatModel(chatModel);
        openAi.setAvailableChatModels(List.of(chatModel));
        openAi.setDefaultEmbeddingModel("qwen3-embedding:8b");
        openAi.setAvailableEmbeddingModels(List.of("qwen3-embedding:8b"));
        openAi.setDefaultApiKeyEnv("OPENAI_COMPATIBLE_API_KEY");
        modelCatalog = new LlmModelCatalogService(llmProperties);
        resolver = new ResolvedLlmConfigResolver(configurationSource, llmProperties, objectMapper, modelCatalog);
    }
}
