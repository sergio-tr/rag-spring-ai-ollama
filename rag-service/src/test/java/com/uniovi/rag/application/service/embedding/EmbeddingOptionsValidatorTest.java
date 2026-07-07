package com.uniovi.rag.application.service.embedding;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.domain.embedding.EmbeddingConfigurationKeys;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class EmbeddingOptionsValidatorTest {

    @Mock
    private ResolvedLlmConfigResolver configResolver;

    private EmbeddingOptionsValidator validator;

    @BeforeEach
    void setUp() {
        validator = new EmbeddingOptionsValidator(configResolver, new EmbeddingCapabilityResolver());
        when(configResolver.resolve(any(), any(), any()))
                .thenReturn(
                        new ResolvedLlmConfig(
                                LlmProvider.OPENAI_COMPATIBLE,
                                LlmProvider.OPENAI_COMPATIBLE,
                                "http://litellm",
                                "gemma4:12b",
                                "bge-m3",
                                "LITELLM_API_KEY",
                                null,
                                0.2,
                                45_000,
                                null,
                                Map.of()));
    }

    @Test
    void rejectsDimensionsForUnsupportedModel() {
        Map<String, Object> runtime =
                Map.of(
                        EmbeddingConfigurationKeys.RUNTIME_EMBEDDING_OPTIONS,
                        Map.of("dimensions", 256));

        assertThatThrownBy(() -> validator.validateRuntimeParameters(UUID.randomUUID(), "mxbai-embed-large", runtime))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining(EmbeddingConfigurationKeys.ERROR_DIMENSIONS_UNSUPPORTED);
    }
}
