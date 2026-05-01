package com.uniovi.rag.interfaces.rest.dto.traceregressionsuitedefinition;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Polymorphic suite entry in an upsert JSON body (P35).
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "entryKind", include = JsonTypeInfo.As.EXISTING_PROPERTY, visible = true)
@JsonSubTypes({
    @JsonSubTypes.Type(value = RuntimeTraceRegressionSuiteDefinitionUpsertByTraceIdsEntryRequestDto.class, name = "BY_TRACE_IDS"),
    @JsonSubTypes.Type(value = RuntimeTraceRegressionSuiteDefinitionUpsertByConversationEntryRequestDto.class, name = "BY_CONVERSATION")
})
public abstract class RuntimeTraceRegressionSuiteDefinitionUpsertEntryRequestDto {}
