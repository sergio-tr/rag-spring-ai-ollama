package com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Polymorphic suite definition entry (P34 JSON) — discriminated by {@code entryKind}.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "entryKind", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = RuntimeTraceRegressionSuiteDefinitionByTraceIdsEntryDto.class, name = "BY_TRACE_IDS"),
    @JsonSubTypes.Type(value = RuntimeTraceRegressionSuiteDefinitionByConversationEntryDto.class, name = "BY_CONVERSATION")
})
public abstract class RuntimeTraceRegressionSuiteDefinitionEntryDto {}
