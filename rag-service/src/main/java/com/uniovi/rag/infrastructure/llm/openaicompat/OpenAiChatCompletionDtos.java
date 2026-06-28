package com.uniovi.rag.infrastructure.llm.openaicompat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Request body for {@code POST /v1/chat/completions}. */
@JsonIgnoreProperties(ignoreUnknown = true)
record OpenAiChatCompletionRequest(String model, List<OpenAiChatMessageDto> messages, Double temperature) {}

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
