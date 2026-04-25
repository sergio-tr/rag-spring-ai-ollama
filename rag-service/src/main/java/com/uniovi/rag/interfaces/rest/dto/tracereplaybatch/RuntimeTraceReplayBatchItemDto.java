package com.uniovi.rag.interfaces.rest.dto.tracereplaybatch;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchItemResult;

import java.util.UUID;

/**
 * P28 item row: bounded scalars only (no stages, no comparison mismatches, no trace JSON).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record RuntimeTraceReplayBatchItemDto(
        UUID requestedTraceId,
        UUID resolvedOriginalTraceId,
        int itemOrder,
        String itemOutcome,
        String replayOutcome,
        String answerText,
        String failureDetail,
        String routingRouteKind,
        String workflowName,
        int stageCount,
        boolean originalTraceLoaded,
        boolean replaySucceeded,
        boolean unsupported,
        boolean failedSafe) {

    private static final int MAX_ANSWER_TEXT_CHARS = 1024;
    private static final int MAX_FAILURE_DETAIL_CHARS = 512;
    private static final int MAX_ROUTE_OR_WORKFLOW_CHARS = 128;

    public static RuntimeTraceReplayBatchItemDto fromItemResult(RuntimeTraceReplayBatchItemResult item) {
        UUID resolved = item.resolvedOriginalTraceId().orElse(null);
        return new RuntimeTraceReplayBatchItemDto(
                item.requestedTraceId(),
                resolved,
                item.itemOrder(),
                item.itemOutcome().name(),
                item.replayOutcome().orElse(null),
                cap(item.answerText(), MAX_ANSWER_TEXT_CHARS),
                cap(item.failureDetail(), MAX_FAILURE_DETAIL_CHARS),
                cap(item.routingRouteKind(), MAX_ROUTE_OR_WORKFLOW_CHARS),
                cap(item.workflowName(), MAX_ROUTE_OR_WORKFLOW_CHARS),
                item.stageCount(),
                item.originalTraceLoaded(),
                item.replaySucceeded(),
                item.unsupported(),
                item.failedSafe());
    }

    private static String cap(String s, int maxCodePoints) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.codePoints()
                .limit(maxCodePoints)
                .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }
}
