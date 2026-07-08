package com.uniovi.rag.application.service.embedding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.ConfigurationSourcePort;
import com.uniovi.rag.application.port.RagConfigurationResolver;
import com.uniovi.rag.application.service.config.ConfigResolver;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.configuration.RagFeatureConfiguration;
import com.uniovi.rag.configuration.RagIndexingEmbeddingProperties;
import com.uniovi.rag.configuration.RagReasoningProperties;
import com.uniovi.rag.domain.embedding.EmbeddingApplicationDefaults;
import com.uniovi.rag.domain.embedding.EmbeddingConfigurationKeys;
import com.uniovi.rag.domain.knowledge.MaterializationStrategy;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.llm.LlmOpenAiCompatibleDefaults;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** Lightweight EMB-2 runtime resolution guard (properties + resolver, no campaign). */
@ExtendWith(MockitoExtension.class)
class EmbeddingApplicationDefaultsTest {

    @Mock private RagConfigurationResolver ragConfigurationResolver;
    @Mock private ResolvedLlmConfigResolver llmConfigResolver;

    private ConfigResolver configResolver;
    private EmbeddingDefaultsResolver embeddingDefaultsResolver;

    @BeforeEach
    void setUp() {
        LlmProperties properties = new LlmProperties();
        properties.setDefaultProvider(LlmProvider.OPENAI_COMPATIBLE);
        LlmOpenAiCompatibleDefaults openAi = properties.getOpenAiCompatible();
        openAi.setDefaultEmbeddingModel(EmbeddingApplicationDefaults.EMBEDDING_MODEL);
        openAi.setDefaultTimeoutMs(60_000L);

        configResolver =
                new ConfigResolver(
                        new RagFeatureConfiguration(),
                        new RagReasoningProperties(),
                        mock(ConfigurationSourcePort.class),
                        new ObjectMapper(),
                        properties,
                        EmbeddingApplicationDefaults.RETRIEVAL_TOP_K,
                        EmbeddingApplicationDefaults.SIMILARITY_THRESHOLD);

        embeddingDefaultsResolver =
                new EmbeddingDefaultsResolver(
                        ragConfigurationResolver,
                        llmConfigResolver,
                        new ObjectMapper(),
                        new RagIndexingEmbeddingProperties(2048, 400, true, 0.85),
                        properties,
                        EmbeddingApplicationDefaults.RETRIEVAL_TOP_K,
                        EmbeddingApplicationDefaults.SIMILARITY_THRESHOLD);
    }

    @Test
    void applicationPropertiesLayer_matchesEmb1Winner() {
        RagConfig rag = configResolver.resolve(null, null, null);

        assertThat(rag.topK()).isEqualTo(EmbeddingApplicationDefaults.RETRIEVAL_TOP_K);
        assertThat(rag.similarityThreshold()).isEqualTo(EmbeddingApplicationDefaults.SIMILARITY_THRESHOLD);
        assertThat(rag.embeddingModel()).isEqualTo(EmbeddingApplicationDefaults.EMBEDDING_MODEL);
        assertThat(rag.materializationStrategy()).isEqualTo(MaterializationStrategy.CHUNK_LEVEL);
    }

    @Test
    void embeddingDefaultsResolver_matchesWideChunkConfig() {
        RagConfig rag =
                new RagConfig(
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
                        EmbeddingApplicationDefaults.RETRIEVAL_TOP_K,
                        EmbeddingApplicationDefaults.SIMILARITY_THRESHOLD,
                        "qwen3.5:9b",
                        EmbeddingApplicationDefaults.EMBEDDING_MODEL,
                        "default",
                        "SIMPLE",
                        false,
                        24_000,
                        24_000,
                        false,
                        false,
                        false,
                        MaterializationStrategy.CHUNK_LEVEL);
        when(ragConfigurationResolver.resolve(any(), any(), any())).thenReturn(rag);

        var defaults =
                embeddingDefaultsResolver.resolve(
                        null,
                        null,
                        Map.of(
                                "embeddingDimensions",
                                EmbeddingApplicationDefaults.EMBEDDING_DIMENSIONS,
                                "embeddingNormalize",
                                EmbeddingApplicationDefaults.EMBEDDING_NORMALIZE,
                                "embeddingBatchSize",
                                EmbeddingApplicationDefaults.EMBEDDING_BATCH_SIZE));

        assertThat(defaults.embeddingModel()).isEqualTo(EmbeddingApplicationDefaults.EMBEDDING_MODEL);
        assertThat(defaults.retrievalOptions().topK()).isEqualTo(EmbeddingApplicationDefaults.RETRIEVAL_TOP_K);
        assertThat(defaults.retrievalOptions().similarityThreshold())
                .isEqualTo(EmbeddingApplicationDefaults.SIMILARITY_THRESHOLD);
        assertThat(defaults.retrievalOptions().materializationStrategy())
                .isEqualTo(EmbeddingApplicationDefaults.MATERIALIZATION_STRATEGY);
        assertThat(defaults.embeddingOptions().dimensions()).isEqualTo(EmbeddingApplicationDefaults.EMBEDDING_DIMENSIONS);
        assertThat(defaults.indexingOptions().normalize()).isEqualTo(EmbeddingApplicationDefaults.EMBEDDING_NORMALIZE);
        assertThat(defaults.indexingOptions().batchSize()).isEqualTo(EmbeddingApplicationDefaults.EMBEDDING_BATCH_SIZE);
        assertThat(defaults.embeddingOptions().timeoutSeconds())
                .isEqualTo(EmbeddingApplicationDefaults.EMBEDDING_TIMEOUT_SECONDS);
    }

    @Test
    void systemConfigurationValues_exportsEmb2WinnerKeys() {
        var values = EmbeddingApplicationDefaults.systemConfigurationValues();

        assertThat(values)
                .containsEntry("topK", EmbeddingApplicationDefaults.RETRIEVAL_TOP_K)
                .containsEntry("similarityThreshold", EmbeddingApplicationDefaults.SIMILARITY_THRESHOLD)
                .containsEntry("materializationStrategy", EmbeddingApplicationDefaults.MATERIALIZATION_STRATEGY)
                .containsEntry("embeddingModel", EmbeddingApplicationDefaults.EMBEDDING_MODEL)
                .containsEntry(EmbeddingConfigurationKeys.EMBEDDING_DIMENSIONS, EmbeddingApplicationDefaults.EMBEDDING_DIMENSIONS)
                .containsEntry(EmbeddingConfigurationKeys.EMBEDDING_NORMALIZE, EmbeddingApplicationDefaults.EMBEDDING_NORMALIZE)
                .containsEntry(EmbeddingConfigurationKeys.EMBEDDING_BATCH_SIZE, EmbeddingApplicationDefaults.EMBEDDING_BATCH_SIZE)
                .containsEntry(EmbeddingConfigurationKeys.EMBEDDING_TIMEOUT_SECONDS, EmbeddingApplicationDefaults.EMBEDDING_TIMEOUT_SECONDS);
        assertThat(values).hasSize(8);
    }
}
