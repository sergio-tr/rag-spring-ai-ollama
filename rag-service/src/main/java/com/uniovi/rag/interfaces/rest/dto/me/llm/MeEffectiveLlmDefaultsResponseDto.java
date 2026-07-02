package com.uniovi.rag.interfaces.rest.dto.me.llm;

import com.uniovi.rag.domain.llm.LlmProvider;
import java.util.Map;

/** GET {@code {product}/me/llm/effective-defaults} response (resolved LLM defaults for Settings UI). */
public record MeEffectiveLlmDefaultsResponseDto(
        LlmProvider effectiveProvider,
        String chatModel,
        String classifierModelId,
        Double temperature,
        Map<String, Object> additionalParameters) {}

