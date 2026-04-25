package com.uniovi.rag.domain.runtime.tracereplaybatch;

import java.util.Optional;
import java.util.UUID;

/**
 * One P27 batch row: bounded scalars only (no full {@code ExecutionTrace} or JSON blobs).
 */
public record RuntimeTraceReplayBatchItemResult(
        UUID requestedTraceId,
        Optional<UUID> resolvedOriginalTraceId,
        int itemOrder,
        RuntimeTraceReplayBatchItemOutcome itemOutcome,
        Optional<String> replayOutcome,
        String answerText,
        String failureDetail,
        String routingRouteKind,
        String workflowName,
        int stageCount,
        boolean originalTraceLoaded,
        boolean replaySucceeded,
        boolean unsupported,
        boolean failedSafe) {

    public RuntimeTraceReplayBatchItemResult {
        resolvedOriginalTraceId = resolvedOriginalTraceId == null ? Optional.empty() : resolvedOriginalTraceId;
        replayOutcome = replayOutcome == null ? Optional.empty() : replayOutcome;
        answerText = answerText == null ? "" : answerText;
        failureDetail = failureDetail == null ? "" : failureDetail;
        routingRouteKind = routingRouteKind == null ? "" : routingRouteKind;
        workflowName = workflowName == null ? "" : workflowName;
    }
}
