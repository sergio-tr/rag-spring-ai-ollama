package com.uniovi.rag.application.service.runtime.tracecomparisonbatch;

import com.uniovi.rag.application.service.runtime.tracecomparison.RuntimeTraceReplayComparisonService;
import com.uniovi.rag.application.service.runtime.tracequery.RuntimeTraceQueryService;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonOutcome;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonRequest;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonResult;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchItemResult;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchMode;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchOutcome;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchRequest;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchResult;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchSelection;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchSummary;
import com.uniovi.rag.interfaces.rest.dto.RuntimeExecutionTraceSummaryDto;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * P24: sole owner of sequential batch orchestration over {@link RuntimeTraceReplayComparisonService#compare}. No HTTP,
 * no persistence, no parallel execution.
 */
@Service
public class RuntimeTraceReplayComparisonBatchService {

    public static final int MAX_RAW_TRACE_IDS = 50;
    public static final int LIST_CONVERSATION_PAGE_SIZE = 50;
    public static final int MAX_ITEM_SUMMARY_CHARS = 512;
    public static final int MAX_MISMATCH_COUNT_REPORTED = 50;

    private static final UUID NIL_UUID = new UUID(0L, 0L);

    private final RuntimeTraceReplayComparisonService comparisonService;
    private final RuntimeTraceQueryService traceQueryService;

    public RuntimeTraceReplayComparisonBatchService(
            RuntimeTraceReplayComparisonService comparisonService, RuntimeTraceQueryService traceQueryService) {
        this.comparisonService = comparisonService;
        this.traceQueryService = traceQueryService;
    }

    /**
     * Executes up to 50 single-item P19 comparisons sequentially.
     */
    public RuntimeTraceReplayComparisonBatchResult execute(RuntimeTraceReplayComparisonBatchRequest request) {
        if (request == null || request.userId() == null) {
            return notAttempted(0, 0);
        }

        return switch (request.mode()) {
            case BY_TRACE_IDS -> executeByTraceIds(request);
            case BY_CONVERSATION -> executeByConversation(request);
        };
    }

    private RuntimeTraceReplayComparisonBatchResult executeByTraceIds(RuntimeTraceReplayComparisonBatchRequest request) {
        RuntimeTraceReplayComparisonBatchSelection.ByTraceIds sel =
                (RuntimeTraceReplayComparisonBatchSelection.ByTraceIds) request.selection();
        List<UUID> raw = sel.traceIds();
        int requestedCount = raw.size();
        if (requestedCount > MAX_RAW_TRACE_IDS) {
            return notAttempted(requestedCount, 0);
        }
        if (requestedCount == 0) {
            return emptySelection(0, 0);
        }

        List<UUID> selected = dedupePreserveOrder(raw);
        int selectedCount = selected.size();
        if (selectedCount == 0) {
            return emptySelection(requestedCount, 0);
        }

        return runCompares(request.userId(), selected, requestedCount, selectedCount);
    }

    private RuntimeTraceReplayComparisonBatchResult executeByConversation(RuntimeTraceReplayComparisonBatchRequest request) {
        RuntimeTraceReplayComparisonBatchSelection.ByConversation sel =
                (RuntimeTraceReplayComparisonBatchSelection.ByConversation) request.selection();
        Page<RuntimeExecutionTraceSummaryDto> page =
                traceQueryService.listConversationTraceSummaries(
                        request.userId(),
                        sel.conversationId(),
                        sel.createdAtFrom(),
                        sel.createdAtTo(),
                        sel.workflowName(),
                        0,
                        LIST_CONVERSATION_PAGE_SIZE);

        List<UUID> selected = page.getContent().stream().map(RuntimeExecutionTraceSummaryDto::id).toList();
        int selectedCount = selected.size();
        int requestedCount = selectedCount;

        if (selectedCount == 0) {
            return emptySelection(requestedCount, selectedCount);
        }

        return runCompares(request.userId(), selected, requestedCount, selectedCount);
    }

    private RuntimeTraceReplayComparisonBatchResult runCompares(
            UUID userId, List<UUID> selectedTraceIds, int requestedCount, int selectedCount) {
        List<RuntimeTraceReplayComparisonBatchItemResult> items = new ArrayList<>();
        int[] cat = new int[8];

        int order = 0;
        for (UUID traceId : selectedTraceIds) {
            RuntimeTraceReplayComparisonResult r =
                    comparisonService.compare(RuntimeTraceReplayComparisonRequest.byTraceId(userId, traceId));
            RuntimeTraceReplayComparisonBatchItemResult row = toItem(order, traceId, r);
            items.add(row);
            incrementCategory(cat, r);
            order++;
        }

        RuntimeTraceReplayComparisonBatchSummary summary = buildSummary(requestedCount, selectedCount, cat);
        RuntimeTraceReplayComparisonBatchOutcome outcome = deriveBatchOutcome(summary);
        return new RuntimeTraceReplayComparisonBatchResult(outcome, summary, items, requestedCount, selectedCount);
    }

    private static RuntimeTraceReplayComparisonBatchItemResult toItem(
            int itemOrder, UUID requestedTraceId, RuntimeTraceReplayComparisonResult r) {
        RuntimeTraceReplayComparisonOutcome o = r.runtimeTraceReplayComparisonOutcome();
        int mm = r.mismatches().size();
        int compat = o == RuntimeTraceReplayComparisonOutcome.COMPARISON_SUCCEEDED_COMPATIBLE_MISMATCH
                ? Math.min(mm, MAX_MISMATCH_COUNT_REPORTED)
                : 0;
        int struct = o == RuntimeTraceReplayComparisonOutcome.COMPARISON_SUCCEEDED_STRUCTURAL_MISMATCH
                ? Math.min(mm, MAX_MISMATCH_COUNT_REPORTED)
                : 0;
        boolean unsupported = o == RuntimeTraceReplayComparisonOutcome.REPLAY_UNSUPPORTED;
        boolean failedSafe =
                o == RuntimeTraceReplayComparisonOutcome.REPLAY_FAILED_SAFE
                        || o == RuntimeTraceReplayComparisonOutcome.COMPARISON_FAILED_SAFE;

        return new RuntimeTraceReplayComparisonBatchItemResult(
                itemOrder,
                requestedTraceId,
                resolveResolvedOriginalTraceId(o, r.originalTraceId()),
                o.name(),
                r.replayOutcome().name(),
                r.exactMatch(),
                r.answerComparisonStatus().name(),
                truncateSummary(r.summary()),
                compat,
                struct,
                unsupported,
                failedSafe);
    }

    private static Optional<UUID> resolveResolvedOriginalTraceId(
            RuntimeTraceReplayComparisonOutcome outcome, UUID originalTraceId) {
        if (outcome == RuntimeTraceReplayComparisonOutcome.ORIGINAL_TRACE_NOT_FOUND_OR_INACCESSIBLE) {
            return Optional.empty();
        }
        if (originalTraceId == null || NIL_UUID.equals(originalTraceId)) {
            return Optional.empty();
        }
        return Optional.of(originalTraceId);
    }

    private static String truncateSummary(String s) {
        if (s == null) {
            return "";
        }
        if (s.length() <= MAX_ITEM_SUMMARY_CHARS) {
            return s;
        }
        return s.substring(0, MAX_ITEM_SUMMARY_CHARS);
    }

    /**
     * Categories 1..7 map to summary counter indices; see {@link #buildSummary}.
     */
    private static void incrementCategory(int[] cat, RuntimeTraceReplayComparisonResult r) {
        RuntimeTraceReplayComparisonOutcome o = r.runtimeTraceReplayComparisonOutcome();
        switch (o) {
            case ORIGINAL_TRACE_NOT_FOUND_OR_INACCESSIBLE -> cat[7]++;
            case REPLAY_UNSUPPORTED -> cat[6]++;
            case REPLAY_FAILED_SAFE -> cat[5]++;
            case COMPARISON_FAILED_SAFE -> cat[4]++;
            case COMPARISON_SUCCEEDED_STRUCTURAL_MISMATCH -> cat[3]++;
            case COMPARISON_SUCCEEDED_COMPATIBLE_MISMATCH -> cat[2]++;
            case COMPARISON_SUCCEEDED_EXACT_MATCH -> {
                if (!r.exactMatch()) {
                    throw new IllegalStateException("P19 contract violation: EXACT_MATCH without exactMatch");
                }
                cat[1]++;
            }
            case NOT_ATTEMPTED, INVALID_REQUEST ->
                    throw new IllegalStateException("P24 batch received unexpected comparison outcome: " + o);
        }
    }

    private static RuntimeTraceReplayComparisonBatchSummary buildSummary(
            int requestedCount, int selectedCount, int[] cat) {
        int exact = cat[1];
        int compatible = cat[2];
        int structural = cat[3];
        int compFailedSafe = cat[4];
        int replayFailedSafe = cat[5];
        int replayUnsup = cat[6];
        int notFound = cat[7];
        int processed =
                exact
                        + compatible
                        + structural
                        + compFailedSafe
                        + replayFailedSafe
                        + replayUnsup
                        + notFound;
        return new RuntimeTraceReplayComparisonBatchSummary(
                requestedCount,
                selectedCount,
                processed,
                exact,
                compatible,
                structural,
                compFailedSafe,
                replayFailedSafe,
                replayUnsup,
                notFound);
    }

    /** Visible for unit tests (batch outcome precedence). */
    public static RuntimeTraceReplayComparisonBatchOutcome deriveBatchOutcome(RuntimeTraceReplayComparisonBatchSummary s) {
        int p = s.processedCount();
        if (p == 0) {
            return RuntimeTraceReplayComparisonBatchOutcome.EMPTY_SELECTION;
        }
        int exact = s.exactMatchCount();
        int comp = s.compatibleMismatchItemCount();
        int str = s.structuralMismatchItemCount();
        int cfs = s.comparisonFailedSafeItemCount();
        int rfs = s.replayFailedSafeItemCount();
        int ru = s.replayUnsupportedItemCount();
        int nf = s.originalNotFoundOrInaccessibleItemCount();

        if (p >= 1
                && exact == p
                && comp == 0
                && str == 0
                && cfs == 0
                && rfs == 0
                && ru == 0
                && nf == 0) {
            return RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_ALL_EXACT_MATCH;
        }
        if (comp >= 1
                && exact + comp == p
                && str == 0
                && cfs == 0
                && rfs == 0
                && ru == 0
                && nf == 0) {
            return RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_WITH_COMPATIBLE_MISMATCHES_ONLY;
        }
        if (str >= 1 && ru == 0 && rfs == 0 && cfs == 0 && nf == 0) {
            return RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_WITH_STRUCTURAL_MISMATCHES;
        }
        if (ru >= 1 && str == 0 && rfs == 0 && cfs == 0 && nf == 0) {
            return RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_WITH_UNSUPPORTED_ITEMS;
        }
        if ((rfs + cfs) >= 1 && str == 0 && ru == 0 && nf == 0) {
            return RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_WITH_FAILED_SAFE_ITEMS;
        }
        return RuntimeTraceReplayComparisonBatchOutcome.COMPLETED_MIXED;
    }

    private static RuntimeTraceReplayComparisonBatchResult notAttempted(int requestedCount, int selectedCount) {
        RuntimeTraceReplayComparisonBatchSummary summary =
                new RuntimeTraceReplayComparisonBatchSummary(requestedCount, selectedCount, 0, 0, 0, 0, 0, 0, 0, 0);
        return new RuntimeTraceReplayComparisonBatchResult(
                RuntimeTraceReplayComparisonBatchOutcome.NOT_ATTEMPTED, summary, List.of(), requestedCount, selectedCount);
    }

    private static RuntimeTraceReplayComparisonBatchResult emptySelection(int requestedCount, int selectedCount) {
        RuntimeTraceReplayComparisonBatchSummary summary =
                new RuntimeTraceReplayComparisonBatchSummary(requestedCount, selectedCount, 0, 0, 0, 0, 0, 0, 0, 0);
        return new RuntimeTraceReplayComparisonBatchResult(
                RuntimeTraceReplayComparisonBatchOutcome.EMPTY_SELECTION, summary, List.of(), requestedCount, selectedCount);
    }

    /**
     * First-seen wins; preserves caller order of first occurrences.
     */
    /** Visible for selection unit tests. */
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
