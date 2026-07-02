package com.uniovi.rag.domain.llm;

import java.util.List;

/** Canonical generation parameter identifiers for provider-aware filtering. */
public enum LlmGenerationParameterId {
    TEMPERATURE(List.of("temperature")),
    TOP_P(List.of("topP", "top_p")),
    SEED(List.of("seed")),
    MAX_TOKENS(List.of("maxTokens", "max_tokens")),
    PRESENCE_PENALTY(List.of("presencePenalty", "presence_penalty")),
    FREQUENCY_PENALTY(List.of("frequencyPenalty", "frequency_penalty")),
    RESPONSE_FORMAT(List.of("responseFormat", "response_format")),
    STOP(List.of("stop", "stopSequences")),
    THINK(List.of("think"));

    private final List<String> configKeys;

    LlmGenerationParameterId(List<String> configKeys) {
        this.configKeys = List.copyOf(configKeys);
    }

    public List<String> configKeys() {
        return configKeys;
    }
}
