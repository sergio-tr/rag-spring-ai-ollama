package com.uniovi.rag.application.service.embedding;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.uniovi.rag.application.port.RagConfigurationResolver;
import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.configuration.RagIndexingEmbeddingProperties;
import com.uniovi.rag.domain.embedding.EmbeddingConfigurationKeys;
import com.uniovi.rag.domain.embedding.EmbeddingRequestOptions;
import com.uniovi.rag.domain.embedding.IndexingRequestOptions;
import com.uniovi.rag.domain.embedding.RetrievalRequestOptions;
import com.uniovi.rag.domain.runtime.RagConfig;
import com.uniovi.rag.infrastructure.llm.LlmProperties;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Resolves effective embedding defaults from application properties and user/project configuration. */
@Component
public class EmbeddingDefaultsResolver {

    private final RagConfigurationResolver ragConfigurationResolver;
    private final ResolvedLlmConfigResolver llmConfigResolver;
    private final ObjectMapper objectMapper;
    private final RagIndexingEmbeddingProperties indexingProperties;
    private final LlmProperties llmProperties;
    private final int defaultTopK;
    private final double defaultSimilarityThreshold;

    public EmbeddingDefaultsResolver(
            RagConfigurationResolver ragConfigurationResolver,
            ResolvedLlmConfigResolver llmConfigResolver,
            ObjectMapper objectMapper,
            RagIndexingEmbeddingProperties indexingProperties,
            LlmProperties llmProperties,
            @Value("${spring.ai.ollama.top-k:8}") int defaultTopK,
            @Value("${spring.ai.ollama.similarity-threshold:0.35}") double defaultSimilarityThreshold) {
        this.ragConfigurationResolver = ragConfigurationResolver;
        this.llmConfigResolver = llmConfigResolver;
        this.objectMapper = objectMapper;
        this.indexingProperties = indexingProperties;
        this.llmProperties = llmProperties;
        this.defaultTopK = Math.max(1, defaultTopK);
        this.defaultSimilarityThreshold = defaultSimilarityThreshold;
    }

    public record EffectiveEmbeddingDefaults(
            String embeddingModel,
            EmbeddingRequestOptions embeddingOptions,
            RetrievalRequestOptions retrievalOptions,
            IndexingRequestOptions indexingOptions) {}

    public EffectiveEmbeddingDefaults resolve(UUID userId, UUID projectId, Map<String, Object> configOverrides) {
        Objects.requireNonNull(configOverrides, "configOverrides");
        JsonNode runtimeOverride =
                configOverrides.isEmpty() ? null : objectMapper.valueToTree(configOverrides);
        RagConfig rag = ragConfigurationResolver.resolve(userId, projectId, runtimeOverride);
        Map<String, Object> merged = new LinkedHashMap<>(configOverrides);
        String embeddingModel = rag.embeddingModel();
        if (embeddingModel == null || embeddingModel.isBlank()) {
            embeddingModel = llmConfigResolver.resolve(userId, projectId, null).embeddingModel();
        }
        return new EffectiveEmbeddingDefaults(
                embeddingModel,
                resolveEmbeddingOptions(merged),
                resolveRetrievalOptions(rag, merged),
                resolveIndexingOptions(merged));
    }

    public EffectiveEmbeddingDefaults applicationDefaults() {
        return resolve(null, null, Map.of());
    }

    private EmbeddingRequestOptions resolveEmbeddingOptions(Map<String, Object> config) {
        String encodingFormat = readString(config, EmbeddingConfigurationKeys.EMBEDDING_ENCODING_FORMAT);
        Integer dimensions = readPositiveInt(config, EmbeddingConfigurationKeys.EMBEDDING_DIMENSIONS);
        Integer timeoutSeconds = readPositiveInt(config, EmbeddingConfigurationKeys.EMBEDDING_TIMEOUT_SECONDS);
        if (timeoutSeconds == null) {
            long timeoutMs = llmProperties.getOpenAiCompatible().getDefaultTimeoutMs();
            if (timeoutMs > 0) {
                timeoutSeconds = (int) Math.min(Integer.MAX_VALUE, timeoutMs / 1000L);
            }
        }
        return new EmbeddingRequestOptions(encodingFormat, dimensions, null, timeoutSeconds);
    }

    private RetrievalRequestOptions resolveRetrievalOptions(RagConfig rag, Map<String, Object> config) {
        Integer topK = readPositiveInt(config, "topK");
        Double threshold = readDouble(config, "similarityThreshold");
        String materialization = readString(config, "materializationStrategy");
        if (materialization == null && rag.materializationStrategy() != null) {
            materialization = rag.materializationStrategy().name();
        }
        return new RetrievalRequestOptions(
                topK != null ? topK : rag.topK() > 0 ? rag.topK() : defaultTopK,
                threshold != null ? threshold : rag.similarityThreshold(),
                materialization);
    }

    private IndexingRequestOptions resolveIndexingOptions(Map<String, Object> config) {
        Integer batchSize = readPositiveInt(config, EmbeddingConfigurationKeys.EMBEDDING_BATCH_SIZE);
        Integer maxInputChars = readPositiveInt(config, EmbeddingConfigurationKeys.EMBEDDING_MAX_INPUT_CHARS);
        if (maxInputChars == null) {
            maxInputChars = indexingProperties.maxInputChars();
        }
        Boolean normalize = readBoolean(config, EmbeddingConfigurationKeys.EMBEDDING_NORMALIZE);
        String truncate = readString(config, EmbeddingConfigurationKeys.EMBEDDING_TRUNCATE);
        return new IndexingRequestOptions(batchSize, maxInputChars, normalize, truncate);
    }

    private static String readString(Map<String, Object> config, String key) {
        Object raw = config.get(key);
        if (raw instanceof String text && !text.isBlank()) {
            return text.trim();
        }
        return null;
    }

    private static Integer readPositiveInt(Map<String, Object> config, String key) {
        Object raw = config.get(key);
        if (raw instanceof Number number && number.intValue() > 0) {
            return number.intValue();
        }
        return null;
    }

    private static Double readDouble(Map<String, Object> config, String key) {
        Object raw = config.get(key);
        if (raw instanceof Number number) {
            return number.doubleValue();
        }
        return null;
    }

    private static Boolean readBoolean(Map<String, Object> config, String key) {
        Object raw = config.get(key);
        if (raw instanceof Boolean bool) {
            return bool;
        }
        return null;
    }
}
