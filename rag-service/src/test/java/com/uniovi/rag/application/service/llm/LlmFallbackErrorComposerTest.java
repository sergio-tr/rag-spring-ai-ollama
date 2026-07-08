package com.uniovi.rag.application.service.llm;

import static org.assertj.core.api.Assertions.assertThat;

import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LlmFallbackErrorComposerTest {

    @Test
    void connectivityUnavailable_mentionsOpenAiWhenProviderConfigured() {
        ResolvedLlmConfig config =
                ResolvedLlmConfig.uniform(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "http://localhost",
                        "gpt-test",
                        "embed",
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of());

        assertThat(LlmFallbackErrorComposer.connectivityUnavailable(config))
                .contains("configured LLM API");
    }

    @Test
    void connectivityUnavailable_mentionsOllamaWhenProviderMissingOrOllama() {
        assertThat(LlmFallbackErrorComposer.connectivityUnavailable(null)).contains("Ollama");
    }

    @Test
    void modelNotInstalled_mentionsApiForOpenAiCompatible() {
        ResolvedLlmConfig config =
                ResolvedLlmConfig.uniform(
                        LlmProvider.OPENAI_COMPATIBLE,
                        "http://localhost",
                        "gpt-test",
                        "embed",
                        null,
                        null,
                        null,
                        null,
                        null,
                        Map.of());

        assertThat(LlmFallbackErrorComposer.modelNotInstalled(config)).contains("configured API");
    }

    @Test
    void modelNotInstalled_mentionsOllamaPullOtherwise() {
        assertThat(LlmFallbackErrorComposer.modelNotInstalled(null)).contains("ollama pull");
    }

    @Test
    void genericApology_returnsStableMessage() {
        assertThat(LlmFallbackErrorComposer.genericApology()).contains("error occurred");
    }
}
