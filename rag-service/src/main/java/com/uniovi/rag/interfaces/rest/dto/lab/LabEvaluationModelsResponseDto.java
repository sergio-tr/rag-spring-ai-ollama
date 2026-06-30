package com.uniovi.rag.interfaces.rest.dto.lab;

import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import java.util.List;

public record LabEvaluationModelsResponseDto(
        LlmProvider effectiveProvider,
        LlmModelCapability capability,
        List<LabEvaluationModelDto> models,
        boolean hasCompatibleEmbeddingModels) {}
