package com.uniovi.rag.interfaces.rest.dto.traceregressionsuite;

import java.time.Instant;
import java.util.UUID;

/** {@code kind: BY_CONVERSATION} entry (P31). */
public record RuntimeTraceRegressionSuiteByConversationEntryRequestDto(
        String kind,
        UUID conversationId,
        Instant createdAtFrom,
        Instant createdAtTo,
        String workflowName)
        implements RuntimeTraceRegressionSuiteEntryRequestDto {}
