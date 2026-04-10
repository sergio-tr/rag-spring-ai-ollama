package com.uniovi.rag.interfaces.rest.dto;

import java.time.Instant;
import java.util.UUID;

public record RuntimeExecutionTraceSummaryDto(
        UUID id,
        Instant createdAt,
        UUID userId,
        UUID projectId,
        UUID conversationId,
        UUID messageId,
        String correlationId,
        UUID resolvedConfigSnapshotId,
        String configHash,
        String workflowName,
        boolean memoryAttempted,
        String memoryOutcome,
        boolean routingAttempted,
        String routingOutcome,
        String routingRouteKind,
        boolean routingFallbackApplied,
        boolean routingWorkflowSelectorInvoked,
        String deterministicToolOutcome,
        String functionCallingOutcome,
        String advisorOutcome,
        boolean judgeAttempted,
        String judgeCandidateSource,
        String judgeFinalOutcome,
        boolean judgeFinalAnswerFromRetry,
        String clarificationOutcome
) {}

