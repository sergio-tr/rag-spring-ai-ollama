package com.uniovi.rag.application.service.llm;

import com.uniovi.rag.application.exception.llm.LlmConfigurationException;
import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmClientRegistryPort;
import com.uniovi.rag.application.port.llm.LlmEmbeddingClient;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LlmClientResolverTest {

    @Mock
    private LlmClientRegistryPort clientRegistry;

    @Mock
    private LlmChatClient ollamaChatClient;

    @Mock
    private LlmEmbeddingClient ollamaEmbeddingClient;

    @Mock
    private LlmChatClient openAiChatClient;

    private LlmClientResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new LlmClientResolver(clientRegistry);
    }

    @Test
    void resolveChatClient_ollamaNative_returnsSingletonOllamaClient() {
        ResolvedLlmConfig config = ollamaConfig("http://localhost:11434", "gemma3:4b");
        when(clientRegistry.ollamaNativeChatClient()).thenReturn(ollamaChatClient);

        LlmChatClient client = resolver.resolveChatClient(config);

        assertSame(ollamaChatClient, client);
        verify(clientRegistry).ollamaNativeChatClient();
    }

    @Test
    void resolveChatClient_openAiCompatible_createsConfigBoundClient() {
        ResolvedLlmConfig config =
                ResolvedLlmConfig.uniform(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "http://litellm:4000",
                        "gpt-4o",
                        "embed-model",
                        "PATH",
                        null,
                        0.2,
                        30_000,
                        null,
                        Map.of());
        when(clientRegistry.createOpenAiCompatibleChatClient(same(config))).thenReturn(openAiChatClient);

        LlmChatClient client = resolver.resolveChatClient(config);

        assertSame(openAiChatClient, client);
        verify(clientRegistry).createOpenAiCompatibleChatClient(config);
    }

    @Test
    void resolveChatClient_openAiCompatibleMissingEnvVar_throwsClearError() {
        ResolvedLlmConfig config =
                ResolvedLlmConfig.uniform(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "http://litellm:4000",
                        "gpt-4o",
                        "embed-model",
                        "UNSET_ENV_VAR_XYZ",
                        null,
                        0.2,
                        30_000,
                        null,
                        Map.of());

        LlmConfigurationException ex =
                assertThrows(LlmConfigurationException.class, () -> resolver.resolveChatClient(config));
        assertTrue(ex.publicMessage().contains("UNSET_ENV_VAR_XYZ"));
        assertTrue(ex.publicMessage().contains("never store keys"));
    }

    @Test
    void resolveChatClient_ollamaMissingBaseUrl_throwsClearError() {
        ResolvedLlmConfig config =
                ResolvedLlmConfig.uniform(
                        LlmProvider.OLLAMA_NATIVE,
                        "  ",
                        "gemma3:4b",
                        "mxbai-embed-large:latest",
                        null,
                        null,
                        0.1,
                        60_000,
                        null,
                        Map.of());

        LlmConfigurationException ex =
                assertThrows(LlmConfigurationException.class, () -> resolver.resolveChatClient(config));
        assertTrue(ex.publicMessage().toLowerCase().contains("baseurl"));
    }

    @Test
    void resolveChatClient_nullConfig_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () -> resolver.resolveChatClient(null));
    }

    @Test
    void resolveEmbeddingClient_ollamaNative_returnsSingletonEmbeddingClient() {
        ResolvedLlmConfig config = ollamaConfig("http://localhost:11434", "gemma3:4b");
        when(clientRegistry.ollamaNativeEmbeddingClient()).thenReturn(ollamaEmbeddingClient);

        LlmEmbeddingClient client = resolver.resolveEmbeddingClient(config);

        assertSame(ollamaEmbeddingClient, client);
    }

    @Test
    void resolveEmbeddingClient_openAiCompatible_createsConfigBoundClient() {
        ResolvedLlmConfig config =
                ResolvedLlmConfig.uniform(
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
        LlmEmbeddingClient openAiEmbeddingClient = org.mockito.Mockito.mock(LlmEmbeddingClient.class);
        when(clientRegistry.createOpenAiCompatibleEmbeddingClient(same(config))).thenReturn(openAiEmbeddingClient);

        LlmEmbeddingClient client = resolver.resolveEmbeddingClient(config);

        assertSame(openAiEmbeddingClient, client);
        verify(clientRegistry, never()).ollamaNativeEmbeddingClient();
    }

    @Test
    void resolveChatClient_defaultsToOllamaShape() {
        ResolvedLlmConfig config = ollamaConfig("http://localhost:11434", "gemma3:4b");
        when(clientRegistry.ollamaNativeChatClient()).thenReturn(ollamaChatClient);

        LlmChatClient client = resolver.resolveChatClient(config);

        assertEquals(LlmProvider.OLLAMA_NATIVE, config.provider());
        assertSame(ollamaChatClient, client);
    }

    private static ResolvedLlmConfig ollamaConfig(String baseUrl, String chatModel) {
        return ResolvedLlmConfig.uniform(
                LlmProvider.OLLAMA_NATIVE,
                baseUrl,
                chatModel,
                "mxbai-embed-large:latest",
                null,
                null,
                0.1,
                60_000,
                null,
                Map.of());
    }
}
