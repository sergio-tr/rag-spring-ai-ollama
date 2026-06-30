package com.uniovi.rag.infrastructure.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.infrastructure.llm.openaicompat.OpenAiCompatibleEmbeddingHealthProbe;
import com.uniovi.rag.infrastructure.llm.openaicompat.OpenAiCompatibleLlmChatClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

class RagProviderHealthIndicatorTest {

    private RagHealthProperties healthProperties;
    private LlmProperties llmProperties;
    private OpenAiCompatibleLlmChatClient openAiChatClient;
    private OpenAiCompatibleEmbeddingHealthProbe embeddingHealthProbe;
    private OllamaHealthIndicator ollamaHealthIndicator;

    @BeforeEach
    void setUp() {
        healthProperties = new RagHealthProperties();
        healthProperties.setReadTimeoutMs(3000);
        llmProperties = new LlmProperties();
        openAiChatClient = mock(OpenAiCompatibleLlmChatClient.class);
        embeddingHealthProbe = mock(OpenAiCompatibleEmbeddingHealthProbe.class);
        ollamaHealthIndicator = mock(OllamaHealthIndicator.class);
    }

    @Test
    void healthChecksV1EmbeddingsWhenOpenAiCompatibleRagIsEnabled() {
        llmProperties.setDefaultProvider(LlmProvider.OPENAI_COMPATIBLE);
        when(openAiChatClient.healthCheckViaChatCompletion()).thenReturn(true);
        when(embeddingHealthProbe.healthCheckViaEmbeddings(3000)).thenReturn(true);

        RagProviderHealthIndicator indicator =
                new RagProviderHealthIndicator(
                        healthProperties, llmProperties, openAiChatClient, embeddingHealthProbe, ollamaHealthIndicator);

        Health health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        verify(openAiChatClient).healthCheckViaChatCompletion();
        verify(embeddingHealthProbe).healthCheckViaEmbeddings(3000);
        verify(ollamaHealthIndicator, never()).health();
        assertEquals("up", health.getDetails().get("openAiEmbeddings"));
    }

    @Test
    void ragHealthIsNotReadyWhenOpenAiEmbeddingsAreConfiguredButUnavailable() {
        llmProperties.setDefaultProvider(LlmProvider.OPENAI_COMPATIBLE);
        when(openAiChatClient.healthCheckViaChatCompletion()).thenReturn(true);
        when(embeddingHealthProbe.healthCheckViaEmbeddings(3000))
                .thenThrow(new RuntimeException("connection refused"));

        RagProviderHealthIndicator indicator =
                new RagProviderHealthIndicator(
                        healthProperties, llmProperties, openAiChatClient, embeddingHealthProbe, ollamaHealthIndicator);

        Health health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("down", health.getDetails().get("openAiEmbeddings"));
        verify(embeddingHealthProbe).healthCheckViaEmbeddings(3000);
    }
}
