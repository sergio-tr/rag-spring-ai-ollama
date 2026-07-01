package com.uniovi.rag.interfaces.rest.dto.me.llm;

import com.uniovi.rag.domain.llm.LlmProvider;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import java.util.List;

/** GET {@code {product}/me/llm/selectable-models} response. */
public record MeSelectableLlmModelsResponseDto(
        LlmProvider effectiveProvider,
        LlmModelCapability capability,
        List<MeSelectableLlmModelDto> models) {}
