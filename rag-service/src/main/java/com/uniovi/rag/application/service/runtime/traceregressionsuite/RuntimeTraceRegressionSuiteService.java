package com.uniovi.rag.application.service.runtime.traceregressionsuite;

import com.uniovi.rag.application.service.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchService;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchOutcome;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchRequest;
import com.uniovi.rag.domain.runtime.tracecomparisonbatch.RuntimeTraceReplayComparisonBatchResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteBatchReturnedEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntry;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryFailureKind;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryKind;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteExecutionFailedEntryResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteOutcome;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteRequest;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteResult;
import com.uniovi.rag.domain.runtime.traceregressionsuite.RuntimeTraceRegressionSuiteSummary;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * P30 regression suite orchestration — delegates each entry to {@link RuntimeTraceReplayComparisonBatchService#execute}
 * exactly once, sequentially.
 */
@Service
public class RuntimeTraceRegressionSuiteService {

    public static final int MAX_SUITE_ENTRIES = 20;

    /**
     * Matches {@code com.uniovi.rag.interfaces.rest.NotFoundException} without depending on that package (P30 ArchUnit).
     */
    static final String NOT_FOUND_EXCEPTION_CLASS_NAME = "com.uniovi.rag.interfaces.rest.NotFoundException";

    private static final int SELECTOR_ECHO_MAX_CODE_POINTS = 256;
    private static final int FAILURE_DETAIL_MAX_CODE_POINTS = 4096;

    private final RuntimeTraceReplayComparisonBatchService batchService;

    public RuntimeTraceRegressionSuiteService(RuntimeTraceReplayComparisonBatchService batchService) {
        this.batchService = batchService;
    }

    /**
     * Runs the suite: validates request, then one batch execute per entry in order (capture exceptions per entry).
     */
    public RuntimeTraceRegressionSuiteResult execute(RuntimeTraceRegressionSuiteRequest request) {
        if (request == null || request.userId() == null || request.entries() == null) {
            return notAttempted();
        }
        if (request.entries().size() > MAX_SUITE_ENTRIES) {
            return notAttempted();
        }
        if (request.entries().isEmpty()) {
            return emptySuite();
        }

        UUID userId = request.userId();
        List<RuntimeTraceRegressionSuiteEntry> entries = request.entries();
        List<RuntimeTraceRegressionSuiteEntryResult> entryResults = new ArrayList<>(entries.size());

        for (int i = 0; i < entries.size(); i++) {
            RuntimeTraceRegressionSuiteEntry entry = entries.get(i);
            String selectorEcho = selectorEcho(entry);
            RuntimeTraceRegressionSuiteEntryKind entryKind = entryKind(entry);
            try {
                RuntimeTraceReplayComparisonBatchRequest batchRequest = toBatchRequest(userId, entry);
                RuntimeTraceReplayComparisonBatchResult batchResult = batchService.execute(batchRequest);
                entryResults.add(toBatchReturned(i, entryKind, selectorEcho, batchResult));
            } catch (Exception ex) {
                entryResults.add(toExecutionFailed(i, entryKind, selectorEcho, ex));
            }
        }

        RuntimeTraceRegressionSuiteSummary summary = buildSummary(entries.size(), entryResults);
        RuntimeTraceRegressionSuiteOutcome outcome =
                summary.executionFailedCount() > 0
                        ? RuntimeTraceRegressionSuiteOutcome.COMPLETED_WITH_ENTRY_EXECUTION_FAILURES
                        : RuntimeTraceRegressionSuiteOutcome.COMPLETED_ALL_BATCH_RETURNS;

        return new RuntimeTraceRegressionSuiteResult(outcome, summary, entryResults);
    }

    private static RuntimeTraceRegressionSuiteResult notAttempted() {
        return new RuntimeTraceRegressionSuiteResult(
                RuntimeTraceRegressionSuiteOutcome.NOT_ATTEMPTED,
                new RuntimeTraceRegressionSuiteSummary(0, 0, 0, 0, 0),
                List.of());
    }

    private static RuntimeTraceRegressionSuiteResult emptySuite() {
        return new RuntimeTraceRegressionSuiteResult(
                RuntimeTraceRegressionSuiteOutcome.EMPTY_SUITE,
                new RuntimeTraceRegressionSuiteSummary(0, 0, 0, 0, 0),
                List.of());
    }

    private static RuntimeTraceRegressionSuiteEntryKind entryKind(RuntimeTraceRegressionSuiteEntry entry) {
        return switch (entry) {
            case RuntimeTraceRegressionSuiteEntry.ByTraceIds ignored -> RuntimeTraceRegressionSuiteEntryKind.BY_TRACE_IDS;
            case RuntimeTraceRegressionSuiteEntry.ByConversation ignored ->
                    RuntimeTraceRegressionSuiteEntryKind.BY_CONVERSATION;
        };
    }

    private static RuntimeTraceReplayComparisonBatchRequest toBatchRequest(
            UUID userId, RuntimeTraceRegressionSuiteEntry entry) {
        return switch (entry) {
            case RuntimeTraceRegressionSuiteEntry.ByTraceIds e ->
                    RuntimeTraceReplayComparisonBatchRequest.byTraceIds(userId, e.traceIds());
            case RuntimeTraceRegressionSuiteEntry.ByConversation e -> {
                Optional<String> workflow = normalizeWorkflowName(e.workflowName());
                yield RuntimeTraceReplayComparisonBatchRequest.byConversation(
                        userId,
                        e.conversationId(),
                        e.createdAtFrom(),
                        e.createdAtTo(),
                        workflow);
            }
        };
    }

    private static Optional<String> normalizeWorkflowName(Optional<String> workflowName) {
        if (workflowName == null || workflowName.isEmpty()) {
            return Optional.empty();
        }
        String trimmed = workflowName.get().trim();
        return trimmed.isEmpty() ? Optional.empty() : Optional.of(trimmed);
    }

    private static RuntimeTraceRegressionSuiteBatchReturnedEntryResult toBatchReturned(
            int entryOrder,
            RuntimeTraceRegressionSuiteEntryKind entryKind,
            String selectorEcho,
            RuntimeTraceReplayComparisonBatchResult batchResult) {
        RuntimeTraceReplayComparisonBatchOutcome outcome = batchResult.batchOutcome();
        int requested = batchResult.requestedCount();
        int selected = batchResult.selectedCount();
        int processed = batchResult.summary().processedCount();
        return new RuntimeTraceRegressionSuiteBatchReturnedEntryResult(
                entryOrder, entryKind, selectorEcho, outcome, requested, selected, processed);
    }

    private static RuntimeTraceRegressionSuiteExecutionFailedEntryResult toExecutionFailed(
            int entryOrder,
            RuntimeTraceRegressionSuiteEntryKind entryKind,
            String selectorEcho,
            Exception ex) {
        RuntimeTraceRegressionSuiteEntryFailureKind failureKind;
        if (NOT_FOUND_EXCEPTION_CLASS_NAME.equals(ex.getClass().getName())) {
            failureKind = RuntimeTraceRegressionSuiteEntryFailureKind.NOT_FOUND;
        } else if (ex instanceof IllegalArgumentException) {
            failureKind = RuntimeTraceRegressionSuiteEntryFailureKind.ILLEGAL_ARGUMENT;
        } else {
            failureKind = RuntimeTraceRegressionSuiteEntryFailureKind.UNEXPECTED;
        }
        String detail = sanitizeFailureDetail(ex);
        return new RuntimeTraceRegressionSuiteExecutionFailedEntryResult(
                entryOrder, entryKind, selectorEcho, failureKind, detail);
    }

    private static String sanitizeFailureDetail(Exception ex) {
        String raw = ex.getMessage();
        if (raw == null) {
            return "";
        }
        return capCodePoints(raw.trim(), FAILURE_DETAIL_MAX_CODE_POINTS);
    }

    static String selectorEcho(RuntimeTraceRegressionSuiteEntry entry) {
        String raw =
                switch (entry) {
                    case RuntimeTraceRegressionSuiteEntry.ByTraceIds e ->
                            "BY_TRACE_IDS:"
                                    + e.traceIds().stream()
                                            .map(UUID::toString)
                                            .collect(Collectors.joining(","));
                    case RuntimeTraceRegressionSuiteEntry.ByConversation e -> conversationSelectorEcho(e);
                };
        return capCodePoints(raw, SELECTOR_ECHO_MAX_CODE_POINTS);
    }

    private static String conversationSelectorEcho(RuntimeTraceRegressionSuiteEntry.ByConversation e) {
        StringBuilder sb = new StringBuilder();
        sb.append("BY_CONVERSATION:conversationId=").append(e.conversationId());
        e.createdAtFrom().ifPresent(v -> sb.append(";from=").append(v));
        e.createdAtTo().ifPresent(v -> sb.append(";to=").append(v));
        e.workflowName().ifPresent(v -> sb.append(";workflow=").append(v));
        return sb.toString();
    }

    /**
     * Truncates to at most {@code max} Unicode code points (not Java {@code char} units).
     */
    static String capCodePoints(String s, int max) {
        if (s == null || s.isEmpty()) {
            return "";
        }
        return s.codePoints().limit(max).collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                .toString();
    }

    private static RuntimeTraceRegressionSuiteSummary buildSummary(
            int requestedEntryCount, List<RuntimeTraceRegressionSuiteEntryResult> entryResults) {
        int batchReturned = 0;
        int executionFailed = 0;
        int batchNotAttemptedSub = 0;
        for (RuntimeTraceRegressionSuiteEntryResult row : entryResults) {
            if (row instanceof RuntimeTraceRegressionSuiteBatchReturnedEntryResult br) {
                batchReturned++;
                if (br.batchOutcome() == RuntimeTraceReplayComparisonBatchOutcome.NOT_ATTEMPTED) {
                    batchNotAttemptedSub++;
                }
            } else if (row instanceof RuntimeTraceRegressionSuiteExecutionFailedEntryResult) {
                executionFailed++;
            }
        }
        int processed = requestedEntryCount;
        return new RuntimeTraceRegressionSuiteSummary(
                requestedEntryCount, processed, batchReturned, executionFailed, batchNotAttemptedSub);
    }
}
