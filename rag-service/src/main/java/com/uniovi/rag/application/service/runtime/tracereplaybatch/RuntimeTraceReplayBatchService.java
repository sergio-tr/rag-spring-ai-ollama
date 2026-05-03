package com.uniovi.rag.application.service.runtime.tracereplaybatch;

import com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService;
import com.uniovi.rag.application.service.runtime.tracereplay.RuntimeTraceReplayService;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayOutcome;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayRequest;
import com.uniovi.rag.domain.runtime.tracereplay.RuntimeTraceReplayResult;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchItemOutcome;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchItemResult;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchOutcome;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchRequest;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchResult;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchSelection;
import com.uniovi.rag.domain.runtime.tracereplaybatch.RuntimeTraceReplayBatchSummary;
import com.uniovi.rag.interfaces.rest.NotFoundException;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceSummaryDto;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * P27: sole owner of sequential batch orchestration over {@link RuntimeTraceReplayService#replay}. No HTTP, no
 * persistence, no parallel execution.
 */
@Service
public class RuntimeTraceReplayBatchService {

    public static final int MAX_RAW_TRACE_IDS = 50;
    public static final int LIST_CONVERSATION_PAGE_SIZE = 50;
    public static final int MAX_ANSWER_TEXT_CHARS = 1024;
    public static final int MAX_FAILURE_DETAIL_CHARS = 512;
    public static final int MAX_ROUTE_OR_WORKFLOW_CHARS = 128;

    private final RuntimeTraceReplayService replayService;
    private final RuntimeTraceQueryService traceQueryService;

    public RuntimeTraceReplayBatchService(RuntimeTraceReplayService replayService, RuntimeTraceQueryService traceQueryService) {
        this.replayService = replayService;
        this.traceQueryService = traceQueryService;
    }

    /**
     * Executes up to 50 single-item P18 replays sequentially.
     */
    public RuntimeTraceReplayBatchResult execute(RuntimeTraceReplayBatchRequest request) {
        if (request == null || request.userId() == null || request.mode() == null || request.selection() == null) {
            return notAttempted(0, 0);
        }

        return switch (request.mode()) {
            case BY_TRACE_IDS -> executeByTraceIds(request);
            case BY_CONVERSATION -> executeByConversation(request);
        };
    }

    private RuntimeTraceReplayBatchResult executeByTraceIds(RuntimeTraceReplayBatchRequest request) {
        RuntimeTraceReplayBatchSelection.ByTraceIds sel = (RuntimeTraceReplayBatchSelection.ByTraceIds) request.selection();
        List<UUID> raw = sel.traceIds();
        int requestedCount = raw.size();
        if (requestedCount > MAX_RAW_TRACE_IDS) {
            return notAttempted(requestedCount, 0);
        }
        for (UUID id : raw) {
            if (id == null) {
                return notAttempted(requestedCount, 0);
            }
        }
        if (requestedCount == 0) {
            return emptySelection(0, 0);
        }

        List<UUID> selected = dedupePreserveOrder(raw);
        int selectedCount = selected.size();
        if (selectedCount == 0) {
            return emptySelection(requestedCount, 0);
        }

        return runReplays(request.userId(), selected, requestedCount, selectedCount);
    }

    /**
     * {@link RuntimeTraceQueryService#listConversationTraceSummaries} may throw {@link NotFoundException}; P27
     * propagates it unchanged (no {@link RuntimeTraceReplayBatchOutcome#EMPTY_SELECTION}).
     */
    private RuntimeTraceReplayBatchResult executeByConversation(RuntimeTraceReplayBatchRequest request) {
        RuntimeTraceReplayBatchSelection.ByConversation sel =
                (RuntimeTraceReplayBatchSelection.ByConversation) request.selection();
        Optional<String> workflowFilter = normalizeWorkflowFilter(sel.workflowName());

        Page<RuntimeExecutionTraceSummaryDto> page =
                traceQueryService.listConversationTraceSummaries(
                        request.userId(),
                        sel.conversationId(),
                        sel.createdAtFrom(),
                        sel.createdAtTo(),
                        workflowFilter,
                        0,
                        LIST_CONVERSATION_PAGE_SIZE);

        List<UUID> selected = page.getContent().stream().map(RuntimeExecutionTraceSummaryDto::id).toList();
        int selectedCount = selected.size();
        int requestedCount = 1;

        if (selectedCount == 0) {
            return emptySelection(requestedCount, 0);
        }

        return runReplays(request.userId(), selected, requestedCount, selectedCount);
    }

    private static Optional<String> normalizeWorkflowFilter(Optional<String> workflowName) {
        return workflowName.map(String::trim).filter(s -> !s.isEmpty());
    }

    private RuntimeTraceReplayBatchResult runReplays(
            UUID userId, List<UUID> selectedTraceIds, int requestedCount, int selectedCount) {
        List<RuntimeTraceReplayBatchItemResult> items = new ArrayList<>();
        int replaySucceeded = 0;
        int replayUnsupported = 0;
        int replayFailedSafe = 0;
        int notFound = 0;
        int notAttempted = 0;

        int order = 0;
        for (UUID traceId : selectedTraceIds) {
            RuntimeTraceReplayBatchItemResult row;
            try {
                RuntimeTraceReplayResult r =
                        replayService.replay(RuntimeTraceReplayRequest.byTraceId(userId, traceId));
                row = toItem(order, traceId, r);
            } catch (NotFoundException e) {
                row = notFoundItem(order, traceId);
            }
            items.add(row);
            switch (row.itemOutcome()) {
                case REPLAY_SUCCEEDED -> replaySucceeded++;
                case REPLAY_UNSUPPORTED -> replayUnsupported++;
                case REPLAY_FAILED_SAFE -> replayFailedSafe++;
                case ORIGINAL_TRACE_NOT_FOUND_OR_INACCESSIBLE -> notFound++;
                case REPLAY_NOT_ATTEMPTED -> notAttempted++;
            }
            order++;
        }

        int processed =
                replaySucceeded + replayUnsupported + replayFailedSafe + notFound + notAttempted;
        if (processed != selectedCount) {
            throw new IllegalStateException("P27 summary identity violation: processed != selectedCount");
        }

        RuntimeTraceReplayBatchSummary summary =
                new RuntimeTraceReplayBatchSummary(
                        processed,
                        replaySucceeded,
                        replayUnsupported,
                        replayFailedSafe,
                        notFound,
                        notAttempted);

        RuntimeTraceReplayBatchOutcome outcome = deriveBatchOutcome(summary);
        return new RuntimeTraceReplayBatchResult(requestedCount, selectedCount, outcome, summary, items);
    }

    /**
     * Maps P18 {@link RuntimeTraceReplayOutcome} to P27 {@link RuntimeTraceReplayBatchItemOutcome} (total, frozen).
     */
    static RuntimeTraceReplayBatchItemOutcome mapReplayOutcomeToItemOutcome(RuntimeTraceReplayOutcome o) {
        if (o.name().startsWith("UNSUPPORTED_")) {
            return RuntimeTraceReplayBatchItemOutcome.REPLAY_UNSUPPORTED;
        }
        return switch (o) {
            case REPLAY_SUCCEEDED -> RuntimeTraceReplayBatchItemOutcome.REPLAY_SUCCEEDED;
            case REPLAY_FAILED_SAFE -> RuntimeTraceReplayBatchItemOutcome.REPLAY_FAILED_SAFE;
            case NOT_ATTEMPTED -> RuntimeTraceReplayBatchItemOutcome.REPLAY_NOT_ATTEMPTED;
            default -> RuntimeTraceReplayBatchItemOutcome.REPLAY_UNSUPPORTED;
        };
    }

    private static RuntimeTraceReplayBatchItemResult toItem(int itemOrder, UUID requestedTraceId, RuntimeTraceReplayResult r) {
        RuntimeTraceReplayOutcome o = r.outcome();
        RuntimeTraceReplayBatchItemOutcome itemOutcome = mapReplayOutcomeToItemOutcome(o);

        Optional<ExecutionTrace> traceOpt = r.transientReplayTrace();
        String routing = traceOpt.map(ExecutionTrace::routingRouteKind).orElse("");
        String workflow = traceOpt.map(ExecutionTrace::workflowName).orElse("");
        int stageCount = traceOpt.map(t -> t.stages().size()).orElse(0);

        boolean replaySucceeded = itemOutcome == RuntimeTraceReplayBatchItemOutcome.REPLAY_SUCCEEDED;
        boolean unsupported = itemOutcome == RuntimeTraceReplayBatchItemOutcome.REPLAY_UNSUPPORTED;
        boolean failedSafe = itemOutcome == RuntimeTraceReplayBatchItemOutcome.REPLAY_FAILED_SAFE;

        return new RuntimeTraceReplayBatchItemResult(
                requestedTraceId,
                Optional.of(requestedTraceId),
                itemOrder,
                itemOutcome,
                Optional.of(o.name()),
                cap(r.answerText().orElse(""), MAX_ANSWER_TEXT_CHARS),
                cap(r.failureDetail().orElse(""), MAX_FAILURE_DETAIL_CHARS),
                cap(routing, MAX_ROUTE_OR_WORKFLOW_CHARS),
                cap(workflow, MAX_ROUTE_OR_WORKFLOW_CHARS),
                stageCount,
                true,
                replaySucceeded,
                unsupported,
                failedSafe);
    }

    private static RuntimeTraceReplayBatchItemResult notFoundItem(int itemOrder, UUID requestedTraceId) {
        return new RuntimeTraceReplayBatchItemResult(
                requestedTraceId,
                Optional.empty(),
                itemOrder,
                RuntimeTraceReplayBatchItemOutcome.ORIGINAL_TRACE_NOT_FOUND_OR_INACCESSIBLE,
                Optional.empty(),
                "",
                "",
                "",
                "",
                0,
                false,
                false,
                false,
                false);
    }

    private static String cap(String s, int maxCodePoints) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.codePoints().limit(maxCodePoints).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append).toString();
    }

    /** Visible for unit tests (batch outcome precedence). */
    public static RuntimeTraceReplayBatchOutcome deriveBatchOutcome(RuntimeTraceReplayBatchSummary s) {
        int p = s.processedCount();
        if (p == 0) {
            return RuntimeTraceReplayBatchOutcome.EMPTY_SELECTION;
        }

        int na = s.replayNotAttemptedItemCount();
        if (na > 0) {
            return RuntimeTraceReplayBatchOutcome.COMPLETED_MIXED;
        }

        int succ = s.replaySucceededItemCount();
        int unsup = s.replayUnsupportedItemCount();
        int fs = s.replayFailedSafeItemCount();
        int nf = s.originalNotFoundOrInaccessibleItemCount();

        if (p >= 1 && succ == p) {
            return RuntimeTraceReplayBatchOutcome.COMPLETED_ALL_REPLAY_SUCCEEDED;
        }
        if (unsup >= 1 && succ + unsup == p && fs == 0 && nf == 0) {
            return RuntimeTraceReplayBatchOutcome.COMPLETED_WITH_UNSUPPORTED_ITEMS;
        }
        if (fs >= 1 && succ + fs == p && unsup == 0 && nf == 0) {
            return RuntimeTraceReplayBatchOutcome.COMPLETED_WITH_FAILED_SAFE_ITEMS;
        }
        if (nf >= 1 && succ + nf == p && unsup == 0 && fs == 0) {
            return RuntimeTraceReplayBatchOutcome.COMPLETED_WITH_NOT_FOUND_ITEMS;
        }
        return RuntimeTraceReplayBatchOutcome.COMPLETED_MIXED;
    }

    private static RuntimeTraceReplayBatchResult notAttempted(int requestedCount, int selectedCount) {
        RuntimeTraceReplayBatchSummary summary = RuntimeTraceReplayBatchSummary.zeros();
        return new RuntimeTraceReplayBatchResult(
                requestedCount,
                selectedCount,
                RuntimeTraceReplayBatchOutcome.NOT_ATTEMPTED,
                summary,
                List.of());
    }

    private static RuntimeTraceReplayBatchResult emptySelection(int requestedCount, int selectedCount) {
        RuntimeTraceReplayBatchSummary summary = RuntimeTraceReplayBatchSummary.zeros();
        return new RuntimeTraceReplayBatchResult(
                requestedCount,
                selectedCount,
                RuntimeTraceReplayBatchOutcome.EMPTY_SELECTION,
                summary,
                List.of());
    }

    /**
     * First-seen wins; preserves caller order of first occurrences.
     */
    public static List<UUID> dedupePreserveOrder(List<UUID> raw) {
        LinkedHashSet<UUID> seen = new LinkedHashSet<>();
        List<UUID> out = new ArrayList<>();
        for (UUID id : raw) {
            if (seen.add(id)) {
                out.add(id);
            }
        }
        return out;
    }
}
