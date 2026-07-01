package com.uniovi.rag.application.service.evaluation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LabBenchmarkDefaultModelResolverTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-4000-8000-000000000099");

    @Mock private ResolvedLlmConfigResolver configResolver;

    private LabBenchmarkDefaultModelResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new LabBenchmarkDefaultModelResolver(configResolver);
        lenient().when(configResolver.resolve(any(), isNull(), isNull()))
                .thenReturn(
                        ResolvedLlmConfig.uniform(
                                LlmProvider.OLLAMA_NATIVE,
                                "http://localhost:11434",
                                "catalog-chat-default",
                                "catalog-embed-default",
                                null,
                                null,
                                null,
                                null,
                                null,
                                Map.of()));
    }

    @Test
    void resolveLlmModelId_usesCatalogDefaultWhenRequestOmitsId() {
        assertThat(resolver.resolveLlmModelId(USER_ID, null)).isEqualTo("catalog-chat-default");
        assertThat(resolver.resolveLlmModelId(USER_ID, "")).isEqualTo("catalog-chat-default");
        assertThat(resolver.resolveLlmModelId(USER_ID, "   ")).isEqualTo("catalog-chat-default");
    }

    @Test
    void resolveLlmModelId_honorsRequestOverride() {
        assertThat(resolver.resolveLlmModelId(USER_ID, "llama3.2:3b")).isEqualTo("llama3.2:3b");
        assertThat(resolver.resolveLlmModelId(USER_ID, "  llama3.2:3b  ")).isEqualTo("llama3.2:3b");
    }

    @Test
    void resolveEmbeddingModelId_usesCatalogDefaultWhenRequestOmitsId() {
        assertThat(resolver.resolveEmbeddingModelId(USER_ID, null)).isEqualTo("catalog-embed-default");
        assertThat(resolver.resolveEmbeddingModelId(USER_ID, "")).isEqualTo("catalog-embed-default");
    }

    @Test
    void resolveEmbeddingModelId_honorsRequestOverride() {
        assertThat(resolver.resolveEmbeddingModelId(USER_ID, "nomic-embed-text")).isEqualTo("nomic-embed-text");
    }
}
