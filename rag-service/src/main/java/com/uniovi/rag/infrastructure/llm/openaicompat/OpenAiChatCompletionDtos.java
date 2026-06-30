package com.uniovi.rag.infrastructure.llm.openaicompat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Request body for {@code POST /v1/chat/completions}. */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
record OpenAiChatCompletionRequest(
        String model,
        List<OpenAiChatMessageDto> messages,
        Double temperature,
        @JsonProperty("top_p") Double topP,
        @JsonProperty("max_tokens") Integer maxTokens,
        Object stop,
        @JsonProperty("presence_penalty") Double presencePenalty,
        @JsonProperty("frequency_penalty") Double frequencyPenalty,
        Integer seed,
        @JsonProperty("response_format") Object responseFormat) {

    /** Backward-compatible 3-field constructor for tests and direct HTTP client usage. */
    OpenAiChatCompletionRequest(String model, List<OpenAiChatMessageDto> messages, Double temperature) {
        this(model, messages, temperature, null, null, null, null, null, null, null);
    }
}

@JsonIgnoreProperties(ignoreUnknown = true)
record OpenAiChatMessageDto(String role, String content) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record OpenAiChatCompletionResponse(
        List<OpenAiChatChoiceDto> choices,
        OpenAiUsageDto usage,
        String model) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record OpenAiChatChoiceDto(
        @JsonProperty("finish_reason") String finishReason, OpenAiChatChoiceMessageDto message) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record OpenAiChatChoiceMessageDto(String role, String content) {}

@JsonIgnoreProperties(ignoreUnknown = true)
record OpenAiUsageDto(
        @JsonProperty("prompt_tokens") Integer promptTokens,
        @JsonProperty("completion_tokens") Integer completionTokens,
        @JsonProperty("total_tokens") Integer totalTokens) {}
