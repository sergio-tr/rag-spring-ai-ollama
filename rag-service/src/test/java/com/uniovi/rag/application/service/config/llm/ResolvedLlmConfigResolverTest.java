package com.uniovi.rag.application.service.config.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.domain.llm.LlmConfigurationKeys;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
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
    private final UUID userId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    private ResolvedLlmConfigResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ResolvedLlmConfigResolver(configurationSource, llmProperties, objectMapper);
    }

    @Test
    void resolve_withoutUser_usesApplicationOllamaDefaults() {
        ResolvedLlmConfig config = resolver.resolve(null, null, null);

        assertEquals(LlmProvider.OLLAMA_NATIVE, config.provider());
        assertEquals("http://localhost:11434", config.baseUrl());
        assertEquals("gemma3:4b", config.chatModel());
        verify(configurationSource, never()).loadUserDefault(any());
    }

    @Test
    void resolve_userLayerOverridesChatModel() {
        when(configurationSource.loadUserDefault(eq(userId)))
                .thenReturn(Optional.of(Map.of(LlmConfigurationKeys.CHAT_MODEL, "custom-model")));

        ResolvedLlmConfig config = resolver.resolve(userId, null, null);

        assertEquals("custom-model", config.chatModel());
        assertEquals(LlmProvider.OLLAMA_NATIVE, config.provider());
    }

    @Test
    void resolve_openAiCompatibleFromUser_requiresApiKeyEnvReference() {
        when(configurationSource.loadUserDefault(eq(userId)))
                .thenReturn(
                        Optional.of(
                                Map.of(
                                        LlmConfigurationKeys.PROVIDER,
                                        "OPENAI_COMPATIBLE",
                                        LlmConfigurationKeys.BASE_URL,
                                        "http://litellm:4000",
                                        LlmConfigurationKeys.CHAT_MODEL,
                                        "gpt-4o",
                                        LlmConfigurationKeys.API_KEY_ENV,
                                        "MY_LLM_KEY")));

        ResolvedLlmConfig config = resolver.resolve(userId, null, null);

        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.provider());
        assertEquals("http://litellm:4000", config.baseUrl());
        assertEquals("MY_LLM_KEY", config.apiKeyEnv());
    }

    @Test
    void resolve_openAiCompatibleFromUser_fallsBackToDefaultApiKeyEnv() {
        when(configurationSource.loadUserDefault(eq(userId)))
                .thenReturn(
                        Optional.of(
                                Map.of(
                                        LlmConfigurationKeys.PROVIDER,
                                        "OPENAI_COMPATIBLE",
                                        LlmConfigurationKeys.BASE_URL,
                                        "http://litellm:4000",
                                        LlmConfigurationKeys.CHAT_MODEL,
                                        "gpt-4o")));

        ResolvedLlmConfig config = resolver.resolve(userId, null, null);

        assertEquals("OPENAI_COMPATIBLE_API_KEY", config.apiKeyEnv());
    }

    @Test
    void resolve_openAiCompatibleWithoutAnyKeyReference_failsValidation() {
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
                                        "gpt-4o")));

        assertThrows(IllegalStateException.class, () -> resolver.resolve(userId, null, null));
    }

    @Test
    void requireApiKeyEnvResolvable_missingEnvVar_throwsClearMessage() {
        ResolvedLlmConfig config =
                new ResolvedLlmConfig(
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
        openAi.setDefaultChatModel("default-chat");
        openAi.setDefaultApiKeyEnv("DEFAULT_KEY_ENV");

        ResolvedLlmConfig config = resolver.resolve(null, null, null);

        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.provider());
        assertEquals("http://default-litellm:4000", config.baseUrl());
        assertEquals("DEFAULT_KEY_ENV", config.apiKeyEnv());
    }

    @Test
    void resolve_ollamaUserDoesNotRequireApiKeyEnv() {
        when(configurationSource.loadUserDefault(eq(userId)))
                .thenReturn(Optional.of(Map.of(LlmConfigurationKeys.CHAT_MODEL, "ollama-user-model")));

        ResolvedLlmConfig config = resolver.resolve(userId, null, null);

        assertEquals(LlmProvider.OLLAMA_NATIVE, config.provider());
        assertNull(config.apiKeyEnv());
        config.requireApiKeyEnvResolvable();
    }
}
