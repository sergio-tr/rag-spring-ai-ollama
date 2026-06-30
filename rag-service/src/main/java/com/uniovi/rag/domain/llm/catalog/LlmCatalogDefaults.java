package com.uniovi.rag.domain.llm.catalog;

import com.uniovi.rag.domain.llm.LlmProvider;

/** System default models for a provider as declared in properties. */
public record LlmCatalogDefaults(
        LlmProvider provider, String defaultChatModel, String defaultEmbeddingModel) {}
