package com.uniovi.rag.application.service.runtime.tracecomparison;

import com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService;
import com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayAnswerComparisonStatus;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonOutcome;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonReplayEcho;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonRequest;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonResult;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayFieldMismatch;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayMode;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayOutcome;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayResult;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceDetailDto;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Single application-layer owner for P19 replay comparison (internal only; no REST in P19).
 */
@Service
public class RuntimeTraceReplayComparisonService {

    private static final int MAX_SUMMARY_CHARS = 512;
    private static final UUID NIL = new UUID(0L, 0L);

    private final RuntimeTraceQueryService traceQueryService;
    private final RuntimeTraceReplayService replayService;
    private final RuntimeTraceReplayComparator comparator;

    public RuntimeTraceReplayComparisonService(
            RuntimeTraceQueryService traceQueryService,
            RuntimeTraceReplayService replayService,
            RuntimeTraceReplayComparator comparator) {
        this.traceQueryService = traceQueryService;
        this.replayService = replayService;
        this.comparator = comparator;
    }

    /**
     * Loads the original trace via {@link RuntimeTraceQueryService} only; invokes replay via {@link RuntimeTraceReplayService} only.
     */
    public RuntimeTraceReplayComparisonResult compare(RuntimeTraceReplayComparisonRequest request) {
        if (request == null || request.userId() == null || !isSelectorValid(request)) {
            return emptyResult(
                    request != null ? request.userId() : NIL,
                    request != null ? request.mode() : RuntimeTraceReplayMode.BY_TRACE_ID,
                    echo(request),
                    RuntimeTraceReplayComparisonOutcome.INVALID_REQUEST,
                    RuntimeTraceReplayOutcome.NOT_ATTEMPTED,
                    "invalid comparison request");
        }

        final RuntimeExecutionTraceDetailDto original;
        try {
            original = loadOriginal(request);
        } catch (NotFoundException e) {
            return emptyResult(
                    request.userId(),
                    request.mode(),
                    echo(request),
                    RuntimeTraceReplayComparisonOutcome.ORIGINAL_TRACE_NOT_FOUND_OR_INACCESSIBLE,
                    RuntimeTraceReplayOutcome.NOT_ATTEMPTED,
                    "original trace not found or inaccessible");
        }

        RuntimeTraceReplayResult replayResult = replayService.replay(request.toReplayRequest());
        RuntimeTraceReplayOutcome replayOutcome = replayResult.outcome();

        if (isReplayUnsupported(replayOutcome)) {
            return buildTerminalWithoutComparator(
                    original,
                    request,
                    replayResult,
                    RuntimeTraceReplayComparisonOutcome.REPLAY_UNSUPPORTED,
                    summarizeReplay(replayOutcome));
        }

        if (replayOutcome == RuntimeTraceReplayOutcome.REPLAY_FAILED_SAFE) {
            return buildTerminalWithoutComparator(
                    original,
                    request,
                    replayResult,
                    RuntimeTraceReplayComparisonOutcome.REPLAY_FAILED_SAFE,
                    "replay failed safe");
        }

        if (replayOutcome != RuntimeTraceReplayOutcome.REPLAY_SUCCEEDED) {
            return buildTerminalWithoutComparator(
                    original,
                    request,
                    replayResult,
                    RuntimeTraceReplayComparisonOutcome.REPLAY_UNSUPPORTED,
                    "unexpected replay outcome");
        }

        Optional<ExecutionTrace> replayTrace = replayResult.transientReplayTrace();
        if (replayTrace.isEmpty()) {
            return buildComparisonFailedSafe(original, request, replayResult, "replay trace missing after success");
        }

        try {
            List<RuntimeTraceReplayFieldMismatch> mismatches =
                    comparator.compare(original, replayTrace.get(), replayResult.answerText());
            RuntimeTraceReplayAnswerComparisonStatus answerStatus = comparator.classifyAnswerStatus(replayResult.answerText());
            RuntimeTraceReplayComparisonOutcome outcome = deriveComparisonOutcome(mismatches);
            boolean exact = outcome == RuntimeTraceReplayComparisonOutcome.COMPARISON_SUCCEEDED_EXACT_MATCH;
            String summary = summarizeComparison(outcome, mismatches.size(), replayOutcome);
            RouteWorkflowEnrichment rw = enrich(original, replayTrace);
            return new RuntimeTraceReplayComparisonResult(
                    original.userId(),
                    original.projectId() != null ? original.projectId() : NIL,
                    original.id(),
                    original.conversationId(),
                    original.messageId(),
                    request.mode(),
                    echo(request),
                    outcome,
                    replayOutcome,
                    answerStatus,
                    exact,
                    summary,
                    mismatches,
                    rw.originalRouteKind(),
                    rw.replayRouteKind(),
                    rw.originalWorkflowName(),
                    rw.replayWorkflowName());
        } catch (RuntimeException e) {
            return buildComparisonFailedSafe(
                    original, request, replayResult, "comparison failed: " + e.getClass().getSimpleName());
        }
    }

    private RuntimeTraceReplayComparisonOutcome deriveComparisonOutcome(List<RuntimeTraceReplayFieldMismatch> mismatches) {
        if (mismatches.isEmpty()) {
            return RuntimeTraceReplayComparisonOutcome.COMPARISON_SUCCEEDED_EXACT_MATCH;
        }
        if (comparator.isCompatibleMismatchOnly(mismatches)) {
            return RuntimeTraceReplayComparisonOutcome.COMPARISON_SUCCEEDED_COMPATIBLE_MISMATCH;
        }
        return RuntimeTraceReplayComparisonOutcome.COMPARISON_SUCCEEDED_STRUCTURAL_MISMATCH;
    }

    /**
     * Single resolution path for the persisted original (same rules as P18 {@code loadTrace}).
     */
    private RuntimeExecutionTraceDetailDto loadOriginal(RuntimeTraceReplayComparisonRequest request) {
        UUID userId = request.userId();
        return switch (request.mode()) {
            case BY_TRACE_ID -> traceQueryService.getTraceDetailById(userId, request.traceId().orElseThrow());
            case BY_MESSAGE_ID ->
                    traceQueryService.getMostRecentTraceDetailByMessageId(
                            userId, request.conversationId().orElseThrow(), request.messageId().orElseThrow());
        };
    }

    private static boolean isSelectorValid(RuntimeTraceReplayComparisonRequest request) {
        return switch (request.mode()) {
            case BY_TRACE_ID -> request.traceId().isPresent();
            case BY_MESSAGE_ID -> request.conversationId().isPresent() && request.messageId().isPresent();
        };
    }

    private static boolean isReplayUnsupported(RuntimeTraceReplayOutcome outcome) {
        return outcome == RuntimeTraceReplayOutcome.NOT_ATTEMPTED || outcome.name().startsWith("UNSUPPORTED_");
    }

    private static RuntimeTraceReplayComparisonReplayEcho echo(RuntimeTraceReplayComparisonRequest request) {
        if (request == null) {
            return new RuntimeTraceReplayComparisonReplayEcho(Optional.empty(), Optional.empty(), Optional.empty());
        }
        return new RuntimeTraceReplayComparisonReplayEcho(
                request.traceId(), request.conversationId(), request.messageId());
    }

    private RuntimeTraceReplayComparisonResult emptyResult(
            UUID userId,
            RuntimeTraceReplayMode mode,
            RuntimeTraceReplayComparisonReplayEcho echo,
            RuntimeTraceReplayComparisonOutcome comparisonOutcome,
            RuntimeTraceReplayOutcome replayOutcome,
            String summary) {
        return new RuntimeTraceReplayComparisonResult(
                userId != null ? userId : NIL,
                NIL,
                NIL,
                null,
                null,
                mode,
                echo,
                comparisonOutcome,
                replayOutcome,
                RuntimeTraceReplayAnswerComparisonStatus.NOT_COMPARABLE_ORIGINAL_ABSENT,
                false,
                cap(summary),
                List.of(),
                "",
                "",
                "",
                "");
    }

    private RuntimeTraceReplayComparisonResult buildTerminalWithoutComparator(
            RuntimeExecutionTraceDetailDto original,
            RuntimeTraceReplayComparisonRequest request,
            RuntimeTraceReplayResult replayResult,
            RuntimeTraceReplayComparisonOutcome outcome,
            String summary) {
        RuntimeTraceReplayAnswerComparisonStatus answerStatus = comparator.classifyAnswerStatus(replayResult.answerText());
        RouteWorkflowEnrichment rw = enrich(original, replayResult.transientReplayTrace());
        return new RuntimeTraceReplayComparisonResult(
                original.userId(),
                original.projectId() != null ? original.projectId() : NIL,
                original.id(),
                original.conversationId(),
                original.messageId(),
                request.mode(),
                echo(request),
                outcome,
                replayResult.outcome(),
                answerStatus,
                false,
                cap(summary),
                List.of(),
                rw.originalRouteKind(),
                rw.replayRouteKind(),
                rw.originalWorkflowName(),
                rw.replayWorkflowName());
    }

    private RuntimeTraceReplayComparisonResult buildComparisonFailedSafe(
            RuntimeExecutionTraceDetailDto original,
            RuntimeTraceReplayComparisonRequest request,
            RuntimeTraceReplayResult replayResult,
            String summary) {
        RouteWorkflowEnrichment rw = enrich(original, replayResult.transientReplayTrace());
        return new RuntimeTraceReplayComparisonResult(
                original.userId(),
                original.projectId() != null ? original.projectId() : NIL,
                original.id(),
                original.conversationId(),
                original.messageId(),
                request.mode(),
                echo(request),
                RuntimeTraceReplayComparisonOutcome.COMPARISON_FAILED_SAFE,
                replayResult.outcome(),
                comparator.classifyAnswerStatus(replayResult.answerText()),
                false,
                cap(summary),
                List.of(),
                rw.originalRouteKind(),
                rw.replayRouteKind(),
                rw.originalWorkflowName(),
                rw.replayWorkflowName());
    }

    private static RouteWorkflowEnrichment enrich(RuntimeExecutionTraceDetailDto original, Optional<ExecutionTrace> replayTrace) {
        String ork = nz(original.routingRouteKind());
        String owf = nz(original.workflowName());
        if (replayTrace.isEmpty()) {
            return new RouteWorkflowEnrichment(ork, "", owf, "");
        }
        ExecutionTrace t = replayTrace.get();
        return new RouteWorkflowEnrichment(ork, nz(t.routingRouteKind()), owf, nz(t.workflowName()));
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private record RouteWorkflowEnrichment(
            String originalRouteKind, String replayRouteKind, String originalWorkflowName, String replayWorkflowName) {}

    private static String summarizeReplay(RuntimeTraceReplayOutcome replayOutcome) {
        return cap("replay outcome=" + replayOutcome.name());
    }

    private static String summarizeComparison(
            RuntimeTraceReplayComparisonOutcome outcome, int mismatchCount, RuntimeTraceReplayOutcome replayOutcome) {
        return cap("comparison outcome=" + outcome.name() + " mismatches=" + mismatchCount + " replay=" + replayOutcome.name());
    }

    private static String cap(String s) {
        if (s == null) {
            return "";
        }
        if (s.length() <= MAX_SUMMARY_CHARS) {
            return s;
        }
        return s.substring(0, MAX_SUMMARY_CHARS);
    }
}
