package com.uniovi.rag.domain.evaluation.snapshot;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Immutable LLM generation parameters captured at benchmark start.
 *
 * <p>Scalar fields mirror {@link com.uniovi.rag.domain.llm.ResolvedLlmConfig} and its
 * {@code additionalParameters}. {@link #fieldSources()} records where each value originated.
 * Unsupported runtime knobs are listed under {@link #unsupportedFields()}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record LlmExperimentalSnapshot(
        String model,
        Double temperature,
        Double topP,
        Integer topK,
        Double minP,
        Double repeatPenalty,
        Integer numCtx,
        Integer maxTokens,
        Integer numPredict,
        Integer seed,
        List<String> stopSequences,
        Object outputFormat,
        Boolean streaming,
        String chatProvider,
        String embeddingProvider,
        Integer timeoutMs,
        Map<String, Object> additionalParameters,
        Map<String, String> fieldSources,
        List<String> unsupportedFields) {

    public LlmExperimentalSnapshot {
        streaming = streaming != null ? streaming : Boolean.FALSE;
        unsupportedFields = unsupportedFields == null ? List.of() : List.copyOf(unsupportedFields);
        stopSequences = stopSequences == null ? List.of() : List.copyOf(stopSequences);
        additionalParameters = additionalParameters == null ? Map.of() : Map.copyOf(additionalParameters);
        fieldSources = fieldSources == null ? Map.of() : Map.copyOf(fieldSources);
    }
}
