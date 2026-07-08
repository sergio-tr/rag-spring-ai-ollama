package com.uniovi.rag.domain.embedding;

import com.fasterxml.jackson.annotation.JsonInclude;

/** LiteLLM / OpenAI-compatible embedding request options (body fields when supported). */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EmbeddingRequestOptions(
        String encodingFormat,
        Integer dimensions,
        String user,
        Integer timeoutSeconds) {

    public EmbeddingRequestOptions {
        if (encodingFormat != null && encodingFormat.isBlank()) {
            encodingFormat = null;
        }
        if (user != null && user.isBlank()) {
            user = null;
        }
        if (timeoutSeconds != null && timeoutSeconds <= 0) {
            timeoutSeconds = null;
        }
        if (dimensions != null && dimensions <= 0) {
            dimensions = null;
        }
    }
}
