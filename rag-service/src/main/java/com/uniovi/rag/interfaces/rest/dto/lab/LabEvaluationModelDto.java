package com.uniovi.rag.interfaces.rest.dto.lab;

import com.uniovi.rag.domain.llm.catalog.LlmCatalogRuntimeStatus;
import java.util.List;

/** Evaluation-selectable model row from the properties catalog. */
public record LabEvaluationModelDto(
        String modelName,
        boolean evalSelectable,
        String blockedReason,
        String blockedReasonCode,
        LlmCatalogRuntimeStatus runtimeStatus,
        Integer embeddingDimensions,
        Boolean compatibleWithCurrentVectorStore,
        boolean usableAsDefault,
        boolean supportsEncodingFormat,
        List<String> supportedEncodingFormats,
        boolean supportsDimensions,
        Integer defaultDimensions,
        Integer maxInputTokens,
        boolean supportsNormalize,
        boolean supportsTruncate) {

    public LabEvaluationModelDto {
        supportedEncodingFormats =
                supportedEncodingFormats == null ? List.of() : List.copyOf(supportedEncodingFormats);
    }

    /** Backward-compatible constructor for tests and call sites without capability metadata. */
    public LabEvaluationModelDto(
            String modelName,
            boolean evalSelectable,
            String blockedReason,
            String blockedReasonCode,
            LlmCatalogRuntimeStatus runtimeStatus,
            Integer embeddingDimensions,
            Boolean compatibleWithCurrentVectorStore,
            boolean usableAsDefault) {
        this(
                modelName,
                evalSelectable,
                blockedReason,
                blockedReasonCode,
                runtimeStatus,
                embeddingDimensions,
                compatibleWithCurrentVectorStore,
                usableAsDefault,
                false,
                List.of(),
                false,
                embeddingDimensions,
                null,
                false,
                false);
    }
}
