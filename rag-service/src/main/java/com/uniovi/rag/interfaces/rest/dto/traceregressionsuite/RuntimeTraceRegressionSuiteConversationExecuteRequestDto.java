package com.uniovi.rag.interfaces.rest.dto.traceregressionsuite;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** Route 2 body - only batch specs; {@code conversationId} comes from the path (P31). */
public record RuntimeTraceRegressionSuiteConversationExecuteRequestDto(
        @JsonProperty(value = "entries", required = true) List<RuntimeTraceRegressionSuiteConversationBatchSpecDto> entries) {}
