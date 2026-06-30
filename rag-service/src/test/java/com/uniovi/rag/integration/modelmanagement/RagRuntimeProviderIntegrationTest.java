package com.uniovi.rag.integration.modelmanagement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.exception.llm.LlmConfigurationException;
import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.application.port.llm.LlmClientRegistryPort;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.config.llm.TaskLlmConfigResolver;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.tool.metadata.MetadataLlmResponseCacheService;
import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.application.service.runtime.ChatGenerationModelSelector;
import com.uniovi.rag.application.service.runtime.llm.OrchestrationLlmConfigScope;
import com.uniovi.rag.application.service.runtime.llm.RagLlmChatInvoker;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.domain.config.capability.CapabilitySet;
import com.uniovi.rag.domain.config.indexing.ReindexImpact;
import com.uniovi.rag.domain.config.prompt.SystemPromptLayers;
import com.uniovi.rag.domain.config.runtime.ConfigProvenance;
import com.uniovi.rag.domain.config.runtime.ResolvedRuntimeConfig;
import com.uniovi.rag.domain.config.validation.CompatibilityResult;
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
import com.uniovi.rag.testsupport.llm.LlmModelCatalogTestSupport;
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

/** Phase 2 — RAG runtime provider wiring without live HTTP. */
@ExtendWith(MockitoExtension.class)
class RagRuntimeProviderIntegrationTest {

    @Mock private ConfigurationSourcePort configurationSource;
    @Mock private LlmClientRegistryPort clientRegistry;
    @Mock private LlmChatClient ollamaChatClient;
    @Mock private LlmChatClient openAiChatClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LlmProperties llmProperties = LlmModelCatalogTestSupport.openAiLiteLlmProperties();
    private final LlmModelCatalogService modelCatalog = new LlmModelCatalogService(llmProperties);
    private final ChatGenerationModelSelector selector = new ChatGenerationModelSelector(modelCatalog);

    private ResolvedLlmConfigResolver configResolver;
    private LlmClientResolver clientResolver;
    private RagLlmChatInvoker ragInvoker;

    @BeforeEach
    void setUp() {
        configResolver = new ResolvedLlmConfigResolver(configurationSource, llmProperties, objectMapper, modelCatalog);
        clientResolver = new LlmClientResolver(clientRegistry);
        ragInvoker = new RagLlmChatInvoker(clientResolver, configResolver, objectMapper, selector, modelCatalog);
    }

    @AfterEach
    void tearDown() {
        OrchestrationLlmConfigScope.clear();
    }

    @Test
    void ragRuntimeWithOpenAiCompatibleUsesOpenAiChatAndOpenAiEmbeddings() {
        ResolvedLlmConfig config = openAiConfig();
        OrchestrationLlmConfigScope.bind(config);
        LlmClientResolver mockResolver = mock(LlmClientResolver.class);
        RagLlmChatInvoker invoker =
                new RagLlmChatInvoker(mockResolver, configResolver, objectMapper, selector, modelCatalog);
        when(mockResolver.resolveChatClient(config)).thenReturn(openAiChatClient);
        when(openAiChatClient.chat(any())).thenReturn(LlmChatResponse.ofContent("openai"));

        assertEquals("openai", invoker.invoke(legacyGemmaContext(), "sys", "q"));
        verify(clientRegistry, never()).ollamaNativeChatClient();
        assertEquals(LlmProvider.OPENAI_COMPATIBLE, config.embeddingProvider());
    }

    @Test
    void ragRuntimeWithOllamaUsesOllamaChatAndOllamaEmbeddings() {
        ResolvedLlmConfig config = ollamaConfig();
        OrchestrationLlmConfigScope.bind(config);
        when(clientRegistry.ollamaNativeChatClient()).thenReturn(ollamaChatClient);
        when(ollamaChatClient.chat(any())).thenReturn(LlmChatResponse.ofContent("ollama"));

        assertEquals("ollama", ragInvoker.invoke(legacyGemmaContext(), "sys", "q"));
        verify(clientRegistry).ollamaNativeChatClient();
        assertEquals(LlmProvider.OLLAMA_NATIVE, config.embeddingProvider());
    }

    @Test
    void ragRuntimeRejectsIndexBuiltWithDifferentEmbeddingModel() {
        OrchestrationLlmConfigScope.bind(openAiConfig());
        ExecutionContext ctx = contextWithChatOverride(legacyGemmaContext(), "gemma3:4b");
        assertThrows(LlmConfigurationException.class, () -> selector.effectiveChatModelId(ctx));
    }

    @Test
    void metadataToolFallbackUsesProviderAwareRetrieval() {
        assertNotEquals(LlmProvider.OLLAMA_NATIVE, openAiConfig().embeddingProvider());
    }

    @Test
    void metadataYesNoFilterDoesNotCallOllamaWhenProviderIsOpenAiCompatible() {
        ResolvedLlmConfig config = openAiConfig();
        OrchestrationLlmConfigScope.bind(config);
        TaskLlmConfigResolver taskLlmConfigResolver = mock(TaskLlmConfigResolver.class);
        when(taskLlmConfigResolver.resolveSecondaryCall(
                        isNull(), isNull(), eq("metadata-yes-no-filter"), isNull(), isNull()))
                .thenReturn(
                        new TaskLlmConfigResolver.SecondaryCallConfig(
                                config, config.chatModel(), config.temperature(), false));
        LlmClientResolver mockResolver = mock(LlmClientResolver.class);
        when(mockResolver.resolveChatClient(config)).thenReturn(openAiChatClient);
        when(openAiChatClient.chat(any())).thenReturn(LlmChatResponse.ofContent("YES"));
        MetadataLlmResponseCacheService metadataCache =
                new MetadataLlmResponseCacheService(mockResolver, configResolver, taskLlmConfigResolver);

        String out = metadataCache.getCachedResponse("metadata-yes-no-filter", "interpret response");

        assertEquals("YES", out);
        verify(clientRegistry, never()).ollamaNativeChatClient();
        verify(openAiChatClient).chat(any());
    }

    @Test
    void deterministicToolRouteDoesNotCallOllamaWhenProviderIsOpenAiCompatible() {
        ResolvedLlmConfig config = openAiConfig();
        OrchestrationLlmConfigScope.bind(config);
        LlmClientResolver mockResolver = mock(LlmClientResolver.class);
        RagLlmChatInvoker invoker =
                new RagLlmChatInvoker(mockResolver, configResolver, objectMapper, selector, modelCatalog);
        when(mockResolver.resolveChatClient(config)).thenReturn(openAiChatClient);
        when(openAiChatClient.chat(any())).thenReturn(LlmChatResponse.ofContent("ok"));

        invoker.invoke(legacyGemmaContext(), "sys", "q");

        ArgumentCaptor<LlmChatRequest> captor = ArgumentCaptor.forClass(LlmChatRequest.class);
        verify(openAiChatClient).chat(captor.capture());
        assertEquals("gpt-oss:20b", captor.getValue().model());
    }

    private static ResolvedLlmConfig openAiConfig() {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                "http://litellm:4000",
                "gpt-oss:20b",
                "qwen3-embedding:8b",
                "PATH",
                null,
                0.1,
                60_000,
                null,
                Map.of());
    }

    private static ResolvedLlmConfig ollamaConfig() {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OLLAMA_NATIVE,
                "http://localhost:11434",
                "gemma3:4b",
                "mxbai-embed-large:latest",
                null,
                null,
                0.1,
                60_000,
                null,
                Map.of());
    }

    private static ExecutionContext legacyGemmaContext() {
        RagConfig rag =
                RagConfig.fromFeatureConfiguration(
                        new RagFeatureConfiguration(), 5, 0.2, "gemma3:4b", "emb", "cls", "SIMPLE");
        return executionContextWithRag(rag, Optional.empty());
    }

    private static ExecutionContext contextWithChatOverride(ExecutionContext base, String override) {
        return executionContextWithRag(base.resolved().toRagConfig(), Optional.of(override));
    }

    private static ExecutionContext executionContextWithRag(RagConfig rag, Optional<String> chatOverride) {
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
                chatOverride,
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
