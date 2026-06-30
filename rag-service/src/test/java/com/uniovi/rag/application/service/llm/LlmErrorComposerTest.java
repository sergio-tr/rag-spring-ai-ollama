package com.uniovi.rag.application.service.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.port.llm.LlmChatClient;
import com.uniovi.rag.application.port.llm.LlmChatResponse;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.application.service.runtime.llm.OrchestrationLlmConfigScope;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.testsupport.llm.LlmModelCatalogTestSupport;
import java.net.ConnectException;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LlmErrorComposerTest {

    @Mock private ProviderAwareSecondaryLlmExecutor secondaryLlmExecutor;

    private LlmErrorComposer composer;
    private ResolvedLlmConfig openAiConfig;

    @BeforeEach
    void setUp() {
        composer = new LlmErrorComposer(secondaryLlmExecutor);
        LlmProperties properties = LlmModelCatalogTestSupport.openAiLiteLlmProperties();
        openAiConfig =
                ResolvedLlmConfig.uniform(
                        LlmProvider.OPENAI_COMPATIBLE,
                        properties.getOpenAiCompatible().getDefaultBaseUrl(),
                        properties.getOpenAiCompatible().getDefaultChatModel(),
                        properties.getOpenAiCompatible().getDefaultEmbeddingModel(),
                        properties.getOpenAiCompatible().getDefaultApiKeyEnv(),
                        null,
                        null,
                        null,
                        null,
                        Map.of());
        when(secondaryLlmExecutor.effectiveConfig()).thenReturn(openAiConfig);
    }

    @Test
    void errorComposerUsesEffectiveProviderModel() {
        when(secondaryLlmExecutor.complete(eq("error-composer"), eq(null), any()))
                .thenReturn("Lo siento, inténtalo de nuevo.");

        String message = composer.composeApologyForQueryFailure("¿Quién presidió?", new IllegalStateException("boom"));

        assertThat(message).contains("inténtalo");
        verify(secondaryLlmExecutor).complete(eq("error-composer"), eq(null), any());
        verify(secondaryLlmExecutor).effectiveConfig();
    }

    @Test
    void userFacingProviderErrorDoesNotMentionOllamaWhenProviderIsOpenAiCompatible() {
        String message =
                composer.composeApologyForQueryFailure("test", new ConnectException("connection refused"));

        assertThat(message).doesNotContainIgnoringCase("ollama");
        assertThat(message).containsIgnoringCase("configured LLM API");
    }
}
