package com.uniovi.rag.application.service.runtime.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.llm.LlmEmbeddingClient;
import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.application.port.llm.LlmClientRegistryPort;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.application.service.runtime.llm.OrchestrationLlmConfigScope;
import com.uniovi.rag.application.service.runtime.llm.RagLlmChatInvoker;
import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.application.service.runtime.ChatGenerationModelSelector;
import com.uniovi.rag.testsupport.llm.LlmModelCatalogTestSupport;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
import com.uniovi.rag.domain.llm.LlmConfigurationKeys;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.domain.runtime.engine.ExecutionContext;
import com.uniovi.rag.domain.runtime.engine.KnowledgeSnapshotSelection;
import com.uniovi.rag.domain.runtime.engine.RuntimeOperationKind;
import com.uniovi.rag.domain.runtime.memory.ConversationMemoryOutcome;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRouteKind;
import com.uniovi.rag.domain.runtime.routing.AdaptiveRoutingOutcome;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Wires config resolution, client resolver, and {@link RagLlmChatInvoker} without live LiteLLM/Ollama HTTP.
 */
@ExtendWith(MockitoExtension.class)
class RagLlmProviderIntegrationTest {

    @Mock
    private ConfigurationSourcePort configurationSource;

    @Mock
    private LlmClientRegistryPort clientRegistry;

    @Mock
    private LlmChatClient ollamaChatClient;

    @Mock
    private LlmChatClient openAiBoundClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LlmProperties llmProperties = LlmModelCatalogTestSupport.openAiLiteLlmProperties();
    private final LlmModelCatalogService modelCatalog = new LlmModelCatalogService(llmProperties);
    private final ChatGenerationModelSelector chatGenerationModelSelector = new ChatGenerationModelSelector(modelCatalog);
    private final UUID userId = UUID.fromString("aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee");

    private ResolvedLlmConfigResolver configResolver;
    private LlmClientResolver clientResolver;
    private RagLlmChatInvoker ragInvoker;

    @BeforeEach
    void setUp() {
        configResolver = new ResolvedLlmConfigResolver(configurationSource, llmProperties, objectMapper, modelCatalog);
        clientResolver = new LlmClientResolver(clientRegistry);
        ragInvoker =
                new RagLlmChatInvoker(
                        clientResolver,
                        configResolver,
                        objectMapper,
                        chatGenerationModelSelector,
                        modelCatalog);
    }

    @AfterEach
    void tearDown() {
        OrchestrationLlmConfigScope.clear();
    }

    @Test
    void userWithoutOverrides_defaultsToOllamaNativeAndUsesSingletonChatClient() {
        when(clientRegistry.ollamaNativeChatClient()).thenReturn(ollamaChatClient);
        when(ollamaChatClient.chat(any())).thenReturn(LlmChatResponse.ofContent("ollama"));

        ResolvedLlmConfig resolved = configResolver.resolve(null, null, null);
        assertEquals(LlmProvider.OLLAMA_NATIVE, resolved.provider());

        OrchestrationLlmConfigScope.bind(resolved);
        String answer = ragInvoker.invoke(minimalContext(), "RAG system", "pregunta");

        assertEquals("ollama", answer);
        verify(clientRegistry).ollamaNativeChatClient();
        verify(clientRegistry, never()).createOpenAiCompatibleChatClient(any());
    }

    @Test
    void userWithOpenAiLayer_resolvesOpenAiProvider() {
        when(configurationSource.loadUserDefault(userId))
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
                                        "OPENAI_COMPATIBLE_API_KEY")));

        ResolvedLlmConfig resolved = configResolver.resolve(userId, null, null);

        assertEquals(LlmProvider.OPENAI_COMPATIBLE, resolved.provider());
        assertEquals("http://litellm:4000", resolved.baseUrl());
        assertEquals("gpt-oss:20b", resolved.chatModel());
    }

    @Test
    void ragInvoker_openAiWithLegacyRagGemmaModel_usesGptOssNotGemma() {
        ResolvedLlmConfig openAiConfig =
                ResolvedLlmConfig.uniform(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "http://litellm:4000",
                        "gpt-oss:20b",
                        "embed-model",
                        "OPENAI_COMPATIBLE_API_KEY",
                        null,
                        0.15,
                        25_000,
                        null,
                        Map.of());
        OrchestrationLlmConfigScope.bind(openAiConfig);
        LlmClientResolver mockResolver = mock(LlmClientResolver.class);
        RagLlmChatInvoker invoker =
                new RagLlmChatInvoker(
                        mockResolver, configResolver, objectMapper, chatGenerationModelSelector, modelCatalog);
        when(mockResolver.resolveChatClient(openAiConfig)).thenReturn(openAiBoundClient);
        when(openAiBoundClient.chat(any())).thenReturn(LlmChatResponse.ofContent("lite"));

        ExecutionContext ctx = executionContextWithRag(
                RagConfig.fromFeatureConfiguration(
                        new RagFeatureConfiguration(), 5, 0.2, "gemma3:4b", "emb", "cls", "SIMPLE"));
        String answer = invoker.invoke(ctx, "Capa RAG", "hola");

        assertEquals("lite", answer);
        ArgumentCaptor<LlmChatRequest> captor = ArgumentCaptor.forClass(LlmChatRequest.class);
        verify(openAiBoundClient).chat(captor.capture());
        assertEquals("gpt-oss:20b", captor.getValue().model());
    }

    @Test
    void ragInvoker_openAiBoundConfig_appliesTemperatureTimeoutAndMergedSystemPrompt() {
        ResolvedLlmConfig openAiConfig =
                ResolvedLlmConfig.uniform(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "http://litellm:4000",
                        "gpt-oss:20b",
                        "embed-model",
                        "OPENAI_COMPATIBLE_API_KEY",
                        null,
                        0.15,
                        25_000,
                        "Capa usuario LLM",
                        Map.of());
        OrchestrationLlmConfigScope.bind(openAiConfig);
        LlmClientResolver mockResolver = mock(LlmClientResolver.class);
        RagLlmChatInvoker invoker =
                new RagLlmChatInvoker(
                        mockResolver, configResolver, objectMapper, chatGenerationModelSelector, modelCatalog);
        when(mockResolver.resolveChatClient(openAiConfig)).thenReturn(openAiBoundClient);
        when(openAiBoundClient.chat(any())).thenReturn(LlmChatResponse.ofContent("lite"));

        String answer = invoker.invoke(openAiContext(), "Capa RAG", "hola");

        assertEquals("lite", answer);
        ArgumentCaptor<LlmChatRequest> captor = ArgumentCaptor.forClass(LlmChatRequest.class);
        verify(openAiBoundClient).chat(captor.capture());
        LlmChatRequest request = captor.getValue();
        assertEquals("gpt-oss:20b", request.model());
        assertEquals(0.15, request.temperature());
        assertEquals(25_000, request.timeoutMs());
        assertTrue(request.messages().getFirst().content().contains("Capa RAG"));
        assertTrue(request.messages().getFirst().content().contains("Capa usuario LLM"));
    }

    @Test
    void openAiEmbedding_usesOpenAiCompatibleClient_noOllamaFallback() {
        ResolvedLlmConfig openAiConfig =
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
        LlmEmbeddingClient embeddingClient = mock(LlmEmbeddingClient.class);
        when(clientRegistry.createOpenAiCompatibleEmbeddingClient(openAiConfig)).thenReturn(embeddingClient);

        LlmEmbeddingClient resolved = clientResolver.resolveEmbeddingClient(openAiConfig);

        assertSame(embeddingClient, resolved);
        verify(clientRegistry, never()).ollamaNativeEmbeddingClient();
    }

    private static ExecutionContext openAiContext() {
        RagConfig rag =
                RagConfig.fromFeatureConfiguration(
                        new RagFeatureConfiguration(), 5, 0.2, "gpt-oss:20b", "emb", "cls", "SIMPLE");
        return executionContextWithRag(rag);
    }

    private static ExecutionContext minimalContext() {
        RagConfig rag =
                RagConfig.fromFeatureConfiguration(new RagFeatureConfiguration(), 5, 0.2, "gemma3:4b", "emb", "cls", "SIMPLE");
        return executionContextWithRag(rag);
    }

    private static ExecutionContext executionContextWithRag(RagConfig rag) {
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        rag,
                        CapabilitySet.fromRagConfig(rag),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        new SystemPromptLayers("", "", "", ""),
                        "sys",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        rag);
        return new ExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "q",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "sys",
                KnowledgeSnapshotSelection.empty(),
                Optional.empty(),
                Optional.empty(),
                "corr",
                List.of("all"),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                "q",
                "q",
                Optional.empty(),
                ConversationMemoryOutcome.DISABLED_BY_CONFIG,
                List.of(),
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                Optional.empty(),
                Optional.empty(),
                false,
                AdaptiveRoutingOutcome.DISABLED_BY_CONFIG,
                AdaptiveRouteKind.DIRECT_WORKFLOW_ROUTE,
                false,
                Optional.empty(),
                false,
                List.of());
    }
}
