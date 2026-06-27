package com.uniovi.rag.application.port.llm;

/**
 * Optional token accounting returned by providers that expose usage metadata.
 */
public record LlmTokenUsage(Integer promptTokens, Integer completionTokens, Integer totalTokens) {}
