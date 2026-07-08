package com.uniovi.rag.application.service.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.exception.llm.LlmConfigurationException;
import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.application.service.runtime.llm.OrchestrationLlmConfigScope;
import com.uniovi.rag.application.service.runtime.llm.RagChatModelRoutingService;
import com.uniovi.rag.application.service.runtime.llm.RagLlmChatInvoker;
import com.uniovi.rag.application.service.runtime.llm.RagLlmChatInvokerTestSupport;
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
import com.uniovi.rag.testsupport.llm.LlmModelCatalogTestSupport;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * RAG chat model selection: {@link ResolvedLlmConfig} wins over legacy {@link RagConfig#llmModel()} for
 * {@link LlmProvider#OPENAI_COMPATIBLE}; legacy RagConfig remains available for {@link LlmProvider#OLLAMA_NATIVE}.
 */
class RagModelSelectionTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LlmModelCatalogService modelCatalog =
            LlmModelCatalogTestSupport.catalogFrom(LlmModelCatalogTestSupport.openAiLiteLlmProperties());
    private final ChatGenerationModelSelector chatGenerationModelSelector =
            new ChatGenerationModelSelector(modelCatalog);
    private final RagChatModelRoutingService chatModelRoutingService =
            new RagChatModelRoutingService(modelCatalog);
    private final ResolvedLlmConfigResolver configResolver = mock(ResolvedLlmConfigResolver.class);
    private final LlmClientResolver clientResolver = mock(LlmClientResolver.class);
    private final LlmChatClient chatClient = mock(LlmChatClient.class);
    private RagLlmChatInvoker ragInvoker;

    @BeforeEach
    void setUp() {
        ragInvoker =
                new RagLlmChatInvoker(
                        clientResolver,
                        configResolver,
                        RagLlmChatInvokerTestSupport.passthroughFinalAnswerResolver(),
                        objectMapper,
                        chatGenerationModelSelector,
                        modelCatalog,
                        chatModelRoutingService);
    }

    @AfterEach
    void tearDown() {
        OrchestrationLlmConfigScope.clear();
    }

    @Test
    void ragWithOpenAiCompatibleIgnoresLegacyRagConfigOllamaChatModel() {
        ResolvedLlmConfig openAiConfig = openAiResolved("gpt-oss:20b");
        OrchestrationLlmConfigScope.bind(openAiConfig);
        when(clientResolver.resolveChatClient(openAiConfig)).thenReturn(chatClient);
        when(chatClient.chat(any())).thenReturn(LlmChatResponse.ofContent("ok"));

        ExecutionContext ctx =
                executionContextWithLegacyRagChatModel("gemma3:4b", "mxbai-embed-large:latest");

        ragInvoker.invoke(ctx, "RAG", "hola");

        assertEquals(
                "gpt-oss:20b",
                capturedChatRequest().model(),
                "OPENAI_COMPATIBLE must not use legacy Ollama RagConfig.llmModel()");
        assertNotEquals("gemma3:4b", capturedChatRequest().model());
    }

    @Test
    void ragWithOpenAiCompatibleUsesLiteLlmChatModel() {
        ResolvedLlmConfig openAiConfig = openAiResolved("gpt-oss:20b");
        OrchestrationLlmConfigScope.bind(openAiConfig);
        when(clientResolver.resolveChatClient(openAiConfig)).thenReturn(chatClient);
        when(chatClient.chat(any())).thenReturn(LlmChatResponse.ofContent("lite"));

        ExecutionContext ctx =
                executionContextWithLegacyRagChatModel("gemma3:4b", "mxbai-embed-large:latest");

        ragInvoker.invoke(ctx, "Capa RAG", "pregunta");

        verify(clientResolver).resolveChatClient(openAiConfig);
        assertEquals("gpt-oss:20b", capturedChatRequest().model());
    }

    @Test
    void ragWithOllamaCanUseLegacyRagConfigModel() {
        ResolvedLlmConfig ollamaConfig =
                ResolvedLlmConfig.uniform(
                        LlmProvider.OLLAMA_NATIVE,
                        "http://localhost:11434",
                        "not-in-ollama-catalog",
                        "mxbai-embed-large:latest",
                        null,
                        null,
                        0.1,
                        60_000,
                        null,
                        Map.of());
        OrchestrationLlmConfigScope.bind(ollamaConfig);
        when(clientResolver.resolveChatClient(ollamaConfig)).thenReturn(chatClient);
        when(chatClient.chat(any())).thenReturn(LlmChatResponse.ofContent("ollama"));

        ExecutionContext ctx = executionContextWithLegacyRagChatModel("llama3.1:8b", "mxbai-embed-large:latest");

        assertThrows(
                LlmConfigurationException.class,
                () -> ragInvoker.invoke(ctx, "RAG", "hola"),
                "OLLAMA_NATIVE catalog strict validation rejects models not registered for CHAT");
    }

    private LlmChatRequest capturedChatRequest() {
        ArgumentCaptor<LlmChatRequest> captor = ArgumentCaptor.forClass(LlmChatRequest.class);
        verify(chatClient).chat(captor.capture());
        return captor.getValue();
    }

    private static ResolvedLlmConfig openAiResolved(String chatModel) {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                "http://litellm:4000",
                chatModel,
                "qwen3-embedding:8b",
                "OPENAI_COMPATIBLE_API_KEY",
                null,
                0.1,
                60_000,
                null,
                Map.of());
    }

    private static ExecutionContext executionContextWithLegacyRagChatModel(
            String legacyChatModel, String legacyEmbeddingModel) {
        RagConfig rag =
                RagConfig.fromFeatureConfiguration(
                        new RagFeatureConfiguration(),
                        5,
                        0.2,
                        legacyChatModel,
                        legacyEmbeddingModel,
                        "cls",
                        "SIMPLE");
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
