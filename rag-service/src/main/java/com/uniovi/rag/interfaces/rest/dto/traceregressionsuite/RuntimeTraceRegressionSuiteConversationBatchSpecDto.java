package com.uniovi.rag.interfaces.rest.dto.traceregressionsuite;

import java.time.Instant;

/** One conversation-batch filter row for route 2 (P31). */
public record RuntimeTraceRegressionSuiteConversationBatchSpecDto(
        Instant createdAtFrom, Instant createdAtTo, String workflowName) {}
