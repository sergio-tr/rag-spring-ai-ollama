package com.uniovi.rag.interfaces.rest.dto.me.llm;

import com.uniovi.rag.domain.llm.catalog.LlmCatalogRuntimeStatus;

/** One chat-selectable model row for the authenticated user. */
public record MeSelectableLlmModelDto(
        String modelName,
        String displayName,
        boolean selectable,
        String disabledReason,
        String disabledReasonCode,
        boolean usableAsDefault,
        LlmCatalogRuntimeStatus runtimeStatus) {}
