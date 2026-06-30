package com.uniovi.rag.interfaces.rest.dto.llm.catalog;

import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogRuntimeStatus;
import com.uniovi.rag.domain.llm.catalog.LlmCatalogSource;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;

/** Single model row in {@link LlmCatalogResponseDto}. */
public record LlmCatalogModelDto(
        LlmProvider provider,
        String modelName,
        LlmModelCapability capability,
        boolean available,
        boolean selectableByUser,
        boolean usableAsDefault,
        LlmCatalogRuntimeStatus runtimeStatus,
        String runtimeDetail,
        Integer embeddingDimensions,
        Boolean compatibleWithCurrentVectorStore,
        LlmCatalogSource source) {}
