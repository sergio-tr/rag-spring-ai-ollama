package com.uniovi.rag.interfaces.rest.dto.traceregressionsuite;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Polymorphic suite entry for route 1 - discriminated by {@code kind} (P31).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind", visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = RuntimeTraceRegressionSuiteByTraceIdsEntryRequestDto.class, name = "BY_TRACE_IDS"),
    @JsonSubTypes.Type(value = RuntimeTraceRegressionSuiteByConversationEntryRequestDto.class, name = "BY_CONVERSATION")
})
public sealed interface RuntimeTraceRegressionSuiteEntryRequestDto
        permits RuntimeTraceRegressionSuiteByTraceIdsEntryRequestDto,
                RuntimeTraceRegressionSuiteByConversationEntryRequestDto {}
