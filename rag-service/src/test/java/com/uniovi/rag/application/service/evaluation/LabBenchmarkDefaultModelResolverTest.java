package com.uniovi.rag.application.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LabBenchmarkDefaultModelResolverTest {

    private final LabBenchmarkDefaultModelResolver resolver =
            new LabBenchmarkDefaultModelResolver("gemma3:4b", "mxbai-embed-large:latest");

    @Test
    void resolveLlmModelId_usesDefaultWhenRequestOmitsId() {
        assertThat(resolver.resolveLlmModelId(null)).isEqualTo("gemma3:4b");
        assertThat(resolver.resolveLlmModelId("")).isEqualTo("gemma3:4b");
        assertThat(resolver.resolveLlmModelId("   ")).isEqualTo("gemma3:4b");
    }

    @Test
    void resolveLlmModelId_honorsRequestOverride() {
        assertThat(resolver.resolveLlmModelId("llama3.2:3b")).isEqualTo("llama3.2:3b");
        assertThat(resolver.resolveLlmModelId("  llama3.2:3b  ")).isEqualTo("llama3.2:3b");
    }

    @Test
    void resolveEmbeddingModelId_usesDefaultWhenRequestOmitsId() {
        assertThat(resolver.resolveEmbeddingModelId(null)).isEqualTo("mxbai-embed-large:latest");
        assertThat(resolver.resolveEmbeddingModelId("")).isEqualTo("mxbai-embed-large:latest");
    }

    @Test
    void resolveEmbeddingModelId_honorsRequestOverride() {
        assertThat(resolver.resolveEmbeddingModelId("nomic-embed-text")).isEqualTo("nomic-embed-text");
    }
}
