package com.uniovi.rag.application.service.embedding;

import com.uniovi.rag.application.service.config.llm.ResolvedLlmConfigResolver;
import com.uniovi.rag.domain.embedding.EmbeddingConfigurationKeys;
import com.uniovi.rag.domain.embedding.EmbeddingModelCapabilities;
import com.uniovi.rag.domain.embedding.EmbeddingRequestOptions;
import com.uniovi.rag.domain.embedding.IndexingRequestOptions;
import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.ResolvedLlmConfig;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Validates embedding configuration against provider/model capabilities. */
@Component
public class EmbeddingOptionsValidator {

    private final ResolvedLlmConfigResolver configResolver;
    private final EmbeddingCapabilityResolver capabilityResolver;

    public EmbeddingOptionsValidator(
            ResolvedLlmConfigResolver configResolver, EmbeddingCapabilityResolver capabilityResolver) {
        this.configResolver = configResolver;
        this.capabilityResolver = capabilityResolver;
    }

    public void validateForModel(UUID userId, String modelName, EmbeddingRequestOptions options) {
        validateForModel(userId, modelName, options, null);
    }

    public void validateForModel(
            UUID userId,
            String modelName,
            EmbeddingRequestOptions options,
            IndexingRequestOptions indexingOptions) {
        if (options == null && indexingOptions == null) {
            return;
        }
        ResolvedLlmConfig config = configResolver.resolve(userId, null, null);
        if (config.embeddingProvider() != LlmProvider.OPENAI_COMPATIBLE && options != null) {
            if (options.dimensions() != null) {
                fail(EmbeddingConfigurationKeys.ERROR_DIMENSIONS_UNSUPPORTED);
            }
            if (options.encodingFormat() != null) {
                fail(EmbeddingConfigurationKeys.ERROR_ENCODING_FORMAT_UNSUPPORTED);
            }
        }
        EmbeddingModelCapabilities caps = capabilityResolver.resolve(config.embeddingProvider(), modelName);
        if (options != null) {
            if (options.encodingFormat() != null
                    && (!caps.supportsEncodingFormat()
                            || !caps.supportedEncodingFormats().contains(options.encodingFormat().toLowerCase(Locale.ROOT)))) {
                fail(EmbeddingConfigurationKeys.ERROR_ENCODING_FORMAT_UNSUPPORTED);
            }
            if (options.dimensions() != null && !caps.supportsDimensions()) {
                fail(EmbeddingConfigurationKeys.ERROR_DIMENSIONS_UNSUPPORTED);
            }
        }
        if (indexingOptions != null) {
            if (Boolean.TRUE.equals(indexingOptions.normalize()) && !caps.supportsNormalize()) {
                fail(EmbeddingConfigurationKeys.ERROR_NORMALIZE_UNSUPPORTED);
            }
            if (indexingOptions.truncate() != null && !caps.supportsTruncate()) {
                fail(EmbeddingConfigurationKeys.ERROR_TRUNCATE_UNSUPPORTED);
            }
        }
    }

    public void validateRuntimeParameters(UUID userId, String modelName, Map<String, Object> runtimeParameters) {
        if (runtimeParameters == null || runtimeParameters.isEmpty()) {
            return;
        }
        EmbeddingRequestOptions embedding = EmbeddingBenchmarkRuntimeParameters.readEmbeddingOptions(runtimeParameters);
        IndexingRequestOptions indexing = EmbeddingBenchmarkRuntimeParameters.readIndexingOptions(runtimeParameters);
        validateForModel(userId, modelName, embedding, indexing);
    }

    private static void fail(String code) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, code);
    }
}
