package com.uniovi.rag.domain.embedding;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

/** Provider/model capability metadata for embedding configuration UI and validation. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmbeddingModelCapabilities(
        boolean supportsEncodingFormat,
        List<String> supportedEncodingFormats,
        boolean supportsDimensions,
        Integer defaultDimensions,
        Integer maxInputTokens,
        boolean supportsNormalize,
        boolean supportsTruncate) {

    public EmbeddingModelCapabilities {
        supportedEncodingFormats =
                supportedEncodingFormats == null ? List.of() : List.copyOf(supportedEncodingFormats);
    }
}
