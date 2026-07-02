package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.application.service.runtime.llm.OrchestrationLlmConfigScope;
import com.uniovi.rag.application.service.runtime.llm.RagChatModelRoutingService;
import com.uniovi.rag.application.service.runtime.llm.RagLlmChatInvoker;
import com.uniovi.rag.application.service.runtime.ChatGenerationModelSelector;
import com.uniovi.rag.application.service.llm.catalog.LlmModelCatalogService;
import com.uniovi.rag.testsupport.llm.LlmModelCatalogTestSupport;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPromptRuntimeUsageTest {

    private static final String USER_PROMPT_MARKER_12345 = "USER_PROMPT_MARKER_12345";

    @Mock
    private LlmClientResolver llmClientResolver;
    @Mock
    private ResolvedLlmConfigResolver resolvedLlmConfigResolver;
    @Mock
    private LlmChatClient chatClient;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final LlmModelCatalogService modelCatalog =
            LlmModelCatalogTestSupport.catalogFrom(LlmModelCatalogTestSupport.openAiLiteLlmProperties());
    private final RagChatModelRoutingService chatModelRoutingService =
            new RagChatModelRoutingService(modelCatalog);
    private final ChatGenerationModelSelector chatGenerationModelSelector =
            new ChatGenerationModelSelector(modelCatalog);

    @AfterEach
    void tearDown() {
        OrchestrationLlmConfigScope.clear();
    }

    @Test
    void invoke_mergesUserLlmSystemPromptIntoFinalCall() {
        ResolvedLlmConfig config =
                ResolvedLlmConfig.uniform(
                        LlmProvider.OLLAMA_NATIVE,
                        "http://localhost:11434",
                        "gemma3:4b",
                        "mxbai-embed-large:latest",
                        null,
                        null,
                        0.2,
                        60000,
                        USER_PROMPT_MARKER_12345,
                        Map.of());
        OrchestrationLlmConfigScope.bind(config);
        when(llmClientResolver.resolveChatClient(config)).thenReturn(chatClient);
        when(chatClient.chat(any())).thenReturn(LlmChatResponse.ofContent("ok"));

        RagLlmChatInvoker invoker =
                new RagLlmChatInvoker(
                        llmClientResolver,
                        resolvedLlmConfigResolver,
                        objectMapper,
                        chatGenerationModelSelector,
                        modelCatalog,
                        chatModelRoutingService);

        invoker.invoke(minimalContext(), "workflow-system", "user turn");

        ArgumentCaptor<LlmChatRequest> requestCaptor = ArgumentCaptor.forClass(LlmChatRequest.class);
        verify(chatClient).chat(requestCaptor.capture());
        String systemContent = requestCaptor.getValue().messages().getFirst().content();
        assertTrue(
                systemContent.contains(USER_PROMPT_MARKER_12345),
                "User llmSystemPrompt must reach the final answer LLM call");
        assertTrue(systemContent.contains("workflow-system"));
    }

    private static ExecutionContext minimalContext() {
        RagConfig rag =
                RagConfig.fromFeatureConfiguration(
                        new RagFeatureConfiguration(), 8, 0.25, "gemma3:4b", "emb", "cls", "SIMPLE");
        ResolvedRuntimeConfig resolved =
                new ResolvedRuntimeConfig(
                        rag,
                        CapabilitySet.fromRagConfig(rag),
                        CompatibilityResult.ok(),
                        ReindexImpact.none(),
                        new SystemPromptLayers("", "", "", ""),
                        "workflow-system",
                        new ConfigProvenance(null, null, null, List.of(), null, null),
                        rag);
        return new ExecutionContext(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                "q",
                RuntimeOperationKind.CHAT_MESSAGE,
                resolved,
                "workflow-system",
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
