package com.uniovi.rag.interfaces.rest.dto.tracereplaybatch;

import java.time.Instant;

/**
 * P28 route 2 body: optional P16 list filters only (strict schema — extra JSON keys fail deserialization).
 */
public record RuntimeTraceReplayBatchByConversationRequestDto(
        Instant createdAtFrom, Instant createdAtTo, String workflowName) {}
