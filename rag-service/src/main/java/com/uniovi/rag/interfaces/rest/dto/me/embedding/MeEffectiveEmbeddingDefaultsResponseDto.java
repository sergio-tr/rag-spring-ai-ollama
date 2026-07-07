package com.uniovi.rag.interfaces.rest.dto.me.embedding;

import com.uniovi.rag.domain.embedding.EmbeddingRequestOptions;
import com.uniovi.rag.domain.embedding.IndexingRequestOptions;
import com.uniovi.rag.domain.embedding.RetrievalRequestOptions;
import com.uniovi.rag.domain.llm.LlmProvider;

/** GET {@code {product}/me/embedding/effective-defaults} response for Settings UI. */
public record MeEffectiveEmbeddingDefaultsResponseDto(
        LlmProvider effectiveProvider,
        String embeddingModel,
        EmbeddingRequestOptions embeddingOptions,
        RetrievalRequestOptions retrievalOptions,
        IndexingRequestOptions indexingOptions) {}
