package com.uniovi.rag.interfaces.rest.dto.modelregistry;

import java.util.List;

public record ModelRegistryResponseDto(
        boolean ollamaReachable,
        String ollamaErrorMessage,
        List<ModelRegistryItemDto> llmModels,
        List<ModelRegistryItemDto> embeddingModels) {}
