package com.uniovi.rag.application.service.llm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatRequest;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.application.port.llm.LlmClientRegistryPort;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.llm.ollama.OllamaApiClient;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LlmManualHealthCheckServiceTest {

    @Mock
    private ResolvedLlmConfigResolver configResolver;

    @Mock
    private LlmClientResolver llmClientResolver;

    @Mock
    private LlmClientRegistryPort clientRegistry;

    @Mock
    private OllamaApiClient ollamaApiClient;

    @Mock
    private LlmChatClient openAiChatClient;

    @Test
    void checkResolved_ollamaPingSuccess_returnsUp() throws Exception {
        ResolvedLlmConfig config = ollamaConfig();
        when(llmClientResolver.resolveChatClient(config)).thenReturn(openAiChatClient);
        when(ollamaApiClient.ping()).thenReturn(true);

        LlmManualHealthCheckService service =
                new LlmManualHealthCheckService(
                        configResolver, llmClientResolver, clientRegistry, ollamaApiClient);

        LlmManualHealthCheckService.LlmHealthCheckResult result = service.checkResolved(config);

        assertTrue(result.healthy());
        assertEquals("UP", result.status());
        assertEquals(LlmProvider.OLLAMA_NATIVE, result.provider());
    }

    @Test
    void checkResolved_ollamaPingFailure_returnsDownWithoutThrowing() throws Exception {
        ResolvedLlmConfig config = ollamaConfig();
        when(llmClientResolver.resolveChatClient(config)).thenReturn(openAiChatClient);
        when(ollamaApiClient.ping()).thenReturn(false);

        LlmManualHealthCheckService service =
                new LlmManualHealthCheckService(
                        configResolver, llmClientResolver, clientRegistry, ollamaApiClient);

        LlmManualHealthCheckService.LlmHealthCheckResult result = service.checkResolved(config);

        assertFalse(result.healthy());
        assertEquals("DOWN", result.status());
        assertTrue(result.message().toLowerCase().contains("ollama"));
    }

    @Test
    void checkResolved_openAiProbeSuccess_returnsUp() {
        ResolvedLlmConfig config = openAiConfig();
        when(llmClientResolver.resolveChatClient(config)).thenReturn(openAiChatClient);
        when(clientRegistry.createOpenAiCompatibleChatClient(config)).thenReturn(openAiChatClient);
        when(openAiChatClient.chat(any(LlmChatRequest.class)))
                .thenReturn(LlmChatResponse.ofContent("OK"));

        LlmManualHealthCheckService service =
                new LlmManualHealthCheckService(
                        configResolver, llmClientResolver, clientRegistry, ollamaApiClient);

        LlmManualHealthCheckService.LlmHealthCheckResult result = service.checkResolved(config);

        assertTrue(result.healthy());
        assertEquals("UP", result.status());
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

    private static ResolvedLlmConfig openAiConfig() {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OPENAI_COMPATIBLE,
                "http://litellm:4000",
                "gpt-4o",
                "embed-model",
                "TEST_OPENAI_KEY",
                null,
                0.2,
                30_000,
                null,
                Map.of());
    }
}
