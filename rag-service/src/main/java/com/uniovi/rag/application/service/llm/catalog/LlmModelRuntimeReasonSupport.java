package com.uniovi.rag.application.service.llm.catalog;

import com.uniovi.rag.domain.llm.catalog.LlmCatalogRuntimeStatus;
import com.uniovi.rag.domain.llm.catalog.LlmModelCapability;
import com.uniovi.rag.domain.llm.catalog.LlmModelReasonCodes;
import com.uniovi.rag.interfaces.rest.dto.llm.catalog.LlmCatalogModelDto;

/** Maps catalog runtime rows to Phase 6 reason codes for API consumers. */
public final class LlmModelRuntimeReasonSupport {

    private LlmModelRuntimeReasonSupport() {}

    public static String disabledReasonCode(LlmCatalogRuntimeStatus runtimeStatus) {
        return switch (runtimeStatus) {
            case UNAVAILABLE, PROBE_FAILED -> LlmModelReasonCodes.LLM_MODEL_UNAVAILABLE;
            case AVAILABLE, UNKNOWN, CONFIGURED, NOT_PROBED -> null;
        };
    }

    public static String chatDisabledReasonCode(LlmCatalogModelDto entry) {
        if (entry == null) {
            return LlmModelReasonCodes.LLM_MODEL_NOT_CONFIGURED;
        }
        if (!entry.available()) {
            return LlmModelReasonCodes.LLM_MODEL_NOT_CONFIGURED;
        }
        String runtimeCode = disabledReasonCode(entry.runtimeStatus());
        if (runtimeCode != null) {
            return runtimeCode;
        }
        if (!entry.selectableByUser()) {
            return LlmModelReasonCodes.LLM_MODEL_NOT_CONFIGURED;
        }
        return null;
    }

    public static String evaluationBlockedReasonCode(LlmCatalogModelDto entry) {
        if (entry == null || !entry.available()) {
            return LlmModelReasonCodes.LLM_MODEL_NOT_CONFIGURED;
        }
        if (entry.capability() == LlmModelCapability.EMBEDDING
                && Boolean.FALSE.equals(entry.compatibleWithCurrentVectorStore())) {
            return LlmModelReasonCodes.EMBEDDING_MODEL_INCOMPATIBLE_WITH_VECTOR_STORE;
        }
        String runtimeCode = disabledReasonCode(entry.runtimeStatus());
        return runtimeCode != null ? runtimeCode : null;
    }
}
