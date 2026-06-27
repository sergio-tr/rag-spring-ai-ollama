package com.uniovi.rag.application.exception.llm;

import com.uniovi.rag.domain.llm.LlmProvider;

/** Embedding operation is not implemented for the resolved provider. */
public class UnsupportedEmbeddingProviderException extends LlmProviderException {

    public UnsupportedEmbeddingProviderException(LlmProvider provider, String model, String baseUrl) {
        super(
                LlmFailureKind.UNSUPPORTED_EMBEDDING,
                provider,
                "embedding",
                model,
                baseUrl,
                "Embedding is not supported for provider "
                        + provider
                        + "; use OLLAMA_NATIVE for vector retrieval or wait for a future OpenAI-compatible embedding adapter",
                null,
                null);
    }
}
