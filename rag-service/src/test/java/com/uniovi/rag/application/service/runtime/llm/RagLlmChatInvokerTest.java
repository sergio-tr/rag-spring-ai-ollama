package com.uniovi.rag.application.service.runtime.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.exception.llm.LlmFailureKind;
import com.uniovi.rag.application.exception.llm.LlmProviderException;
import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.llm.LlmClientResolver;
import com.uniovi.rag.application.service.runtime.ChatGenerationModelSelector;
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
import com.uniovi.rag.infrastructure.llm.openaicompat.OpenAiCompatibleLlmException;
import com.uniovi.rag.infrastructure.llm.openaicompat.OpenAiCompatibleLlmFailureKind;
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

@ExtendWith(MockitoExtension.class)
class RagLlmChatInvokerTest {

    @Mock
    private LlmClientResolver llmClientResolver;

    @Mock
    private ResolvedLlmConfigResolver resolvedLlmConfigResolver;

    @Mock
    private LlmChatClient chatClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @AfterEach
    void tearDown() {
        OrchestrationLlmConfigScope.clear();
    }

    @Test
    void invoke_usesBoundConfigAndMergesUserSystemPrompt() {
        ResolvedLlmConfig config =
                new ResolvedLlmConfig(
                        LlmProvider.OLLAMA_NATIVE,
                        "http://localhost:11434",
                        "gemma3:4b",
                        "mxbai-embed-large:latest",
                        null,
                        null,
                        0.3,
                        45_000,
                        "Responde en español.",
                        Map.of("top_p", 0.9));
        OrchestrationLlmConfigScope.bind(config);
        when(llmClientResolver.resolveChatClient(config)).thenReturn(chatClient);
        when(chatClient.chat(any())).thenReturn(LlmChatResponse.ofContent("hola"));

        RagLlmChatInvoker invoker = new RagLlmChatInvoker(llmClientResolver, resolvedLlmConfigResolver, objectMapper);
        ExecutionContext ctx = minimalContext();
        String answer = invoker.invoke(ctx, "RAG base prompt", "user question");

        assertEquals("hola", answer);
        ArgumentCaptor<LlmChatRequest> captor = ArgumentCaptor.forClass(LlmChatRequest.class);
        verify(chatClient).chat(captor.capture());
        LlmChatRequest request = captor.getValue();
        assertEquals("gemma3:4b", request.model());
        assertTrue(request.messages().getFirst().content().contains("RAG base prompt"));
        assertTrue(request.messages().getFirst().content().contains("Responde en español."));
        assertEquals(0.3, request.temperature());
        assertEquals(45_000, request.timeoutMs());
    }

    @Test
    void invoke_openAiUnauthorized_translatesToLlmProviderException() {
        ResolvedLlmConfig config =
                new ResolvedLlmConfig(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "http://litellm:4000",
                        "gpt-oss:20b",
                        "embed-model",
                        "OPENAI_COMPATIBLE_API_KEY",
                        null,
                        0.2,
                        30_000,
                        null,
                        Map.of());
        OrchestrationLlmConfigScope.bind(config);
        when(llmClientResolver.resolveChatClient(config)).thenReturn(chatClient);
        when(chatClient.chat(any()))
                .thenThrow(OpenAiCompatibleLlmException.unauthorized(401));

        RagLlmChatInvoker invoker = new RagLlmChatInvoker(llmClientResolver, resolvedLlmConfigResolver, objectMapper);

        LlmProviderException ex =
                assertThrows(LlmProviderException.class, () -> invoker.invoke(minimalContext(), "sys", "hola"));
        assertEquals(LlmFailureKind.UNAUTHORIZED, ex.failureKind());
    }

    @Test
    void mergeSystemPrompts_appendsUserLayer() {
        String merged = RagLlmChatInvoker.mergeSystemPrompts("base", "user-layer");
        assertEquals("base\n\nuser-layer", merged);
    }

    private static ExecutionContext minimalContext() {
        RagConfig rag = RagConfig.fromFeatureConfiguration(new RagFeatureConfiguration(), 5, 0.2, "gemma3:4b", "emb", "cls", "SIMPLE");
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
