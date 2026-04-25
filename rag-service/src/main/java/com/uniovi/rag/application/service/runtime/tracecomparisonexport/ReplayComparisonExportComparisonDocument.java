package com.uniovi.rag.application.service.runtime.tracecomparisonexport;

import java.util.List;
import java.util.UUID;

/**
 * Bounded {@code comparison.json} payload for P21 (mirrors P20 field set; owned by export package).
 */
public record ReplayComparisonExportComparisonDocument(
        UUID originalTraceId,
        UUID conversationId,
        UUID messageId,
        String comparisonMode,
        String comparisonOutcome,
        String replayOutcome,
        String answerComparisonStatus,
        boolean exactMatch,
        String summary,
        String originalRouteKind,
        String replayRouteKind,
        String originalWorkflowName,
        String replayWorkflowName,
        List<ReplayComparisonExportMismatchLine> mismatches) {}
