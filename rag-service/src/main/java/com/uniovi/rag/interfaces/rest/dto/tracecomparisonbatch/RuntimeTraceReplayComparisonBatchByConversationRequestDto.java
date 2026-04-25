package com.uniovi.rag.interfaces.rest.dto.tracecomparisonbatch;

import java.time.Instant;

/**
 * P25 route 2 body: optional P16 list filters only (strict schema — extra JSON keys fail deserialization).
 */
public record RuntimeTraceReplayComparisonBatchByConversationRequestDto(
        Instant createdAtFrom, Instant createdAtTo, String workflowName) {}
