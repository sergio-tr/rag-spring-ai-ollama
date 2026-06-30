package com.uniovi.rag.interfaces.rest.dto.lab;

import com.uniovi.rag.domain.llm.catalog.LlmCatalogRuntimeStatus;

/** Evaluation-selectable model row from the properties catalog. */
public record LabEvaluationModelDto(
        String modelName,
        boolean evalSelectable,
        String blockedReason,
        String blockedReasonCode,
        LlmCatalogRuntimeStatus runtimeStatus,
        Integer embeddingDimensions,
        Boolean compatibleWithCurrentVectorStore,
        boolean usableAsDefault) {}
