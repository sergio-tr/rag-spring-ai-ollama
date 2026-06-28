package com.uniovi.rag.application.service.config.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.exception.llm.LlmConfigurationException;
import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.application.port.llm.LlmClientRegistryPort;
import com.uniovi.rag.application.port.llm.LlmEmbeddingClient;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaApiClient;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaModelProvisioningService;
import com.uniovi.rag.infrastructure.health.RagHealthProperties;
import com.uniovi.rag.infrastructure.llm.ollama.RagOllamaProperties;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LlmProviderSeparationTest {

    @Mock
    private ConfigurationSourcePort configurationSource;

    @Mock
    private OllamaApiClient ollamaApiClient;

    @Mock
    private LlmClientRegistryPort clientRegistry;

    @Mock
    private LlmEmbeddingClient openAiEmbeddingClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LlmProperties llmProperties = new LlmProperties();
    private LlmModelCatalogService modelCatalog;
    private ResolvedLlmConfigResolver configResolver;
    private LlmClientResolver clientResolver;

    @BeforeEach
    void setUp() {
        modelCatalog = new LlmModelCatalogService(llmProperties);
        configResolver = new ResolvedLlmConfigResolver(configurationSource, llmProperties, objectMapper, modelCatalog);
        clientResolver = new LlmClientResolver(clientRegistry);
    }

    @Test
    void openAiCompatibleDefaultProviderAppliesToChatAndEmbeddings() {
        llmProperties.setDefaultProvider(LlmProvider.OPENAI_COMPATIBLE);
        LlmOpenAiCompatibleDefaults openAi = llmProperties.getOpenAiCompatible();
        openAi.setDefaultBaseUrl("http://litellm:4000");
        openAi.setDefaultChatModel("gpt-oss:20b");
        openAi.setDefaultEmbeddingModel("qwen3-embedding:8b");
        openAi.setAvailableChatModels(java.util.List.of("gpt-oss:20b"));
        openAi.setAvailableEmbeddingModels(java.util.List.of("qwen3-embedding:8b"));
        openAi.setDefaultApiKeyEnv("OPENAI_COMPATIBLE_API_KEY");
        modelCatalog = new LlmModelCatalogService(llmProperties);
        configResolver = new ResolvedLlmConfigResolver(configurationSource, llmProperties, objectMapper, modelCatalog);

        ResolvedLlmConfig config = configResolver.resolve(null, null, null);

        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.chatProvider());
        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.embeddingProvider());
        assertEquals("gpt-oss:20b", config.chatModel());
        assertEquals("qwen3-embedding:8b", config.embeddingModel());
        assertEquals("http://litellm:4000", config.baseUrl());
    }

    @Test
    void openAiCompatibleChatDoesNotTriggerOllamaChatProvisioning() throws Exception {
        llmProperties.setDefaultProvider(LlmProvider.OPENAI_COMPATIBLE);
        OllamaModelProvisioningService svc = provisioningService();
        svc.ensureConfiguredModelsAtStartup();

        verify(ollamaApiClient, never()).pullModel(org.mockito.ArgumentMatchers.eq("chat-m"), org.mockito.ArgumentMatchers.anyLong());
        verify(ollamaApiClient, never()).pullModel(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
        assertEquals(OllamaModelProvisioningService.State.READY, svc.getState());
    }

    @Test
    void openAiCompatibleEmbeddingsDoNotFallbackToOllama() {
        llmProperties.setDefaultProvider(LlmProvider.OPENAI_COMPATIBLE);
        LlmOpenAiCompatibleDefaults openAi = llmProperties.getOpenAiCompatible();
        openAi.setDefaultBaseUrl("http://litellm:4000");
        openAi.setDefaultChatModel("gpt-oss:20b");
        openAi.setAvailableChatModels(java.util.List.of("gpt-oss:20b"));
        openAi.setDefaultEmbeddingModel("qwen3-embedding:8b");
        openAi.setAvailableEmbeddingModels(java.util.List.of("qwen3-embedding:8b"));
        openAi.setDefaultApiKeyEnv("OPENAI_COMPATIBLE_API_KEY");
        modelCatalog = new LlmModelCatalogService(llmProperties);
        configResolver = new ResolvedLlmConfigResolver(configurationSource, llmProperties, objectMapper, modelCatalog);

        ResolvedLlmConfig config = configResolver.resolve(null, null, null);

        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.embeddingProvider());
        assertEquals("qwen3-embedding:8b", config.embeddingModel());
    }

    @Test
    void hybridModeRequiresExplicitEmbeddingProviderOllama() {
        llmProperties.setDefaultChatProvider(LlmProvider.OPENAI_COMPATIBLE);
        llmProperties.setDefaultEmbeddingProvider(LlmProvider.OLLAMA_NATIVE);
        llmProperties.getOpenAiCompatible().setDefaultBaseUrl("http://litellm:4000");
        llmProperties.getOpenAiCompatible().setDefaultChatModel("gpt-oss:20b");
        llmProperties.getOpenAiCompatible().setAvailableChatModels(java.util.List.of("gpt-oss:20b"));
        llmProperties.getOpenAiCompatible().setDefaultApiKeyEnv("OPENAI_COMPATIBLE_API_KEY");
        modelCatalog = new LlmModelCatalogService(llmProperties);
        configResolver = new ResolvedLlmConfigResolver(configurationSource, llmProperties, objectMapper, modelCatalog);

        ResolvedLlmConfig config = configResolver.resolve(null, null, null);

        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.chatProvider());
        assertEquals(LlmProvider.OLLAMA_NATIVE, config.embeddingProvider());
    }

    @Test
    void ollamaProvisioningRunsOnlyWhenAnyEffectiveProviderIsOllamaNative() throws Exception {
        llmProperties.setDefaultProvider(LlmProvider.OPENAI_COMPATIBLE);
        OllamaModelProvisioningService openAiOnly = provisioningService();
        openAiOnly.ensureConfiguredModelsAtStartup();
        verify(ollamaApiClient, never()).pullModel(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());

        llmProperties.setDefaultEmbeddingProvider(LlmProvider.OLLAMA_NATIVE);
        OllamaModelProvisioningService hybrid = provisioningService();
        when(ollamaApiClient.listModelNames()).thenReturn(new HashSet<>());
        org.mockito.Mockito.lenient()
                .doAnswer(inv -> null)
                .when(ollamaApiClient)
                .pullModel(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyLong());
        hybrid.ensureConfiguredModelsAtStartup();
        verify(ollamaApiClient).pullModel(org.mockito.ArgumentMatchers.eq("embed-m"), org.mockito.ArgumentMatchers.anyLong());
        verify(ollamaApiClient, never()).pullModel(org.mockito.ArgumentMatchers.eq("chat-m"), org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void openAiCompatibleEmbeddingWithoutClientFailsWithClearError() {
        ResolvedLlmConfig config =
                ResolvedLlmConfig.uniform(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "http://litellm:4000",
                        "gpt-oss:20b",
                        "qwen3-embedding:8b",
                        "OPENAI_COMPATIBLE_API_KEY",
                        null,
                        0.1,
                        30_000,
                        null,
                        Map.of());
        when(clientRegistry.createOpenAiCompatibleEmbeddingClient(same(config))).thenReturn(openAiEmbeddingClient);

        assertEquals(openAiEmbeddingClient, clientResolver.resolveEmbeddingClient(config));
        verify(clientRegistry, never()).ollamaNativeEmbeddingClient();
    }

    @Test
    void openAiCompatibleEmbeddingWithoutApiKeyFailsWithClearError() {
        ResolvedLlmConfig config =
                ResolvedLlmConfig.uniform(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "http://litellm:4000",
                        "gpt-oss:20b",
                        "qwen3-embedding:8b",
                        "MISSING_EMBED_KEY_ENV",
                        null,
                        0.1,
                        30_000,
                        null,
                        Map.of());

        LlmConfigurationException ex =
                assertThrows(LlmConfigurationException.class, () -> clientResolver.resolveEmbeddingClient(config));
        org.junit.jupiter.api.Assertions.assertTrue(ex.publicMessage().contains("MISSING_EMBED_KEY_ENV"));
        verify(clientRegistry, never()).ollamaNativeEmbeddingClient();
        verify(clientRegistry, never()).createOpenAiCompatibleEmbeddingClient(org.mockito.ArgumentMatchers.any());
    }

    private OllamaModelProvisioningService provisioningService() {
        RagHealthProperties health = new RagHealthProperties();
        RagOllamaProperties ollama = new RagOllamaProperties();
        return new OllamaModelProvisioningService(health, ollama, llmProperties, ollamaApiClient, "chat-m", "embed-m");
    }
}
