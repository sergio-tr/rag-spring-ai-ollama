package com.uniovi.rag.domain.evaluation.snapshot;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Immutable LLM generation parameters captured at benchmark start (Ollama-oriented).
 *
 * <p>Fields not supported by the bound Spring AI {@code OllamaOptions} builder are recorded only under
 * {@link #unsupportedFields()}.
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
        List<String> unsupportedFields) {

    public LlmExperimentalSnapshot {
        streaming = streaming != null ? streaming : Boolean.FALSE;
        unsupportedFields = unsupportedFields == null ? List.of() : List.copyOf(unsupportedFields);
        stopSequences = stopSequences == null ? List.of() : List.copyOf(stopSequences);
    }
}
