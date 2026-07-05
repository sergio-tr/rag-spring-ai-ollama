package com.uniovi.rag.interfaces.rest.dto.me.llm;

import java.util.List;
import java.util.Map;

/** GET {@code {product}/me/llm/effective-runtime} - chat-scoped effective configuration summary. */
public record MeEffectiveRuntimeResponseDto(
        String projectId,
        String conversationId,
        Map<String, Object> effectiveConfig,
        List<Map<String, Object>> taskRoles,
        String classifierModelId,
        String snapshotEmbeddingModelId,
        String presetId,
        String effectivePresetId,
        String presetName,
        String presetSource,
        Integer retrievalTopK,
        Double retrievalSimilarityThreshold,
        String materializationStrategy) {}
