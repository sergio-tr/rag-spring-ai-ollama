package com.uniovi.rag.application.service.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.RagConfigurationResolver;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.configuration.RagIndexingEmbeddingProperties;
import com.uniovi.rag.domain.embedding.EmbeddingConfigurationKeys;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmbeddingDefaultsResolverTest {

    @Mock
    private RagConfigurationResolver ragConfigurationResolver;

    @Mock
    private ResolvedLlmConfigResolver llmConfigResolver;

    private EmbeddingDefaultsResolver resolver;

    @BeforeEach
    void setUp() {
        LlmProperties properties = new LlmProperties();
        LlmOpenAiCompatibleDefaults openAi = properties.getOpenAiCompatible();
        openAi.setDefaultTimeoutMs(45_000L);
        resolver =
                new EmbeddingDefaultsResolver(
                        ragConfigurationResolver,
                        llmConfigResolver,
                        new ObjectMapper(),
                        new RagIndexingEmbeddingProperties(2048, 400, true, 0.85),
                        properties,
                        10,
                        0.35);
    }

    @Test
    void resolvesEffectiveDefaultsFromConfigOverrides() {
        RagConfig rag = sampleRagConfig();
        when(ragConfigurationResolver.resolve(eq(null), eq(null), any())).thenReturn(rag);

        EmbeddingDefaultsResolver.EffectiveEmbeddingDefaults defaults =
                resolver.resolve(
                        null,
                        null,
                        Map.of(
                                EmbeddingConfigurationKeys.EMBEDDING_ENCODING_FORMAT,
                                "float",
                                EmbeddingConfigurationKeys.EMBEDDING_DIMENSIONS,
                                768,
                                "topK",
                                12,
                                "similarityThreshold",
                                0.61,
                                "materializationStrategy",
                                "DOCUMENT_LEVEL"));

        assertThat(defaults.embeddingModel()).isEqualTo("bge-m3");
        assertThat(defaults.embeddingOptions().encodingFormat()).isEqualTo("float");
        assertThat(defaults.embeddingOptions().dimensions()).isEqualTo(768);
        assertThat(defaults.retrievalOptions().topK()).isEqualTo(12);
        assertThat(defaults.retrievalOptions().similarityThreshold()).isEqualTo(0.61);
        assertThat(defaults.retrievalOptions().materializationStrategy()).isEqualTo("DOCUMENT_LEVEL");
        assertThat(defaults.indexingOptions().maxInputChars()).isEqualTo(2048);
    }

    private static RagConfig sampleRagConfig() {
        return new RagConfig(
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                false,
                5,
                0.7,
                "gemma4:12b",
                "bge-m3",
                "default",
                "SIMPLE",
                false,
                24_000,
                24_000,
                false,
                false,
                false,
                MaterializationStrategy.CHUNK_LEVEL);
    }
}
