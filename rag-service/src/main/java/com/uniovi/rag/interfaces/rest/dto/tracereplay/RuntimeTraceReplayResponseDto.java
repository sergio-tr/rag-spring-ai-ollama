package com.uniovi.rag.interfaces.rest.dto.tracereplay;

import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayMode;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayRequest;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayResult;

import java.util.Optional;
import java.util.UUID;

/**
 * Bounded projection of {@link RuntimeTraceReplayResult} for P22 GET responses.
 */
public record RuntimeTraceReplayResponseDto(
        String selectorMode,
        String replayOutcome,
        UUID originalTraceId,
        UUID conversationId,
        UUID messageId,
        String routingRouteKind,
        String workflowName,
        String answerText,
        String failureDetail,
        RuntimeTraceReplayTransientTraceSummaryDto transientTraceSummary) {

    public static final int MAX_ANSWER_TEXT_CHARS = 1024;
    public static final int MAX_FAILURE_DETAIL_CHARS = 512;
    public static final int MAX_ROUTE_OR_WORKFLOW_CHARS = 128;

    public static RuntimeTraceReplayResponseDto fromReplayHttp(
            RuntimeTraceReplayResult result,
            RuntimeTraceReplayRequest replayRequest,
            UUID pathTraceId,
            UUID pathConversationId,
            UUID pathMessageId) {
        RuntimeTraceReplayMode mode = replayRequest.mode();
        UUID originalTraceId = mode == RuntimeTraceReplayMode.BY_TRACE_ID ? pathTraceId : null;
        UUID conversationId = mode == RuntimeTraceReplayMode.BY_MESSAGE_ID ? pathConversationId : null;
        UUID messageId = mode == RuntimeTraceReplayMode.BY_MESSAGE_ID ? pathMessageId : null;

        Optional<ExecutionTrace> traceOpt = result.transientReplayTrace();
        String routing = traceOpt.map(ExecutionTrace::routingRouteKind).orElse("");
        String workflow = traceOpt.map(ExecutionTrace::workflowName).orElse("");
        RuntimeTraceReplayTransientTraceSummaryDto summary =
                traceOpt.map(et -> new RuntimeTraceReplayTransientTraceSummaryDto(et.stages().size()))
                        .orElse(null);

        return new RuntimeTraceReplayResponseDto(
                mode.name(),
                result.outcome().name(),
                originalTraceId,
                conversationId,
                messageId,
                cap(routing, MAX_ROUTE_OR_WORKFLOW_CHARS),
                cap(workflow, MAX_ROUTE_OR_WORKFLOW_CHARS),
                cap(result.answerText().orElse(""), MAX_ANSWER_TEXT_CHARS),
                cap(result.failureDetail().orElse(""), MAX_FAILURE_DETAIL_CHARS),
                summary);
    }

    private static String cap(String s, int max) {
        if (s == null) {
            return "";
        }
        if (s.length() <= max) {
            return s;
        }
        return s.substring(0, max);
    }
}
