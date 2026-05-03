package com.uniovi.rag.application.service.runtime.tracecomparisonexport;

import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayFieldMismatch;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayMismatchCategory;
import com.uniovi.rag.domain.runtime.tracecomparison.RuntimeTraceReplayComparisonResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps domain comparison results to bounded export documents (same caps as P20 JSON: 512 / 50 / 256).
 */
final class ReplayComparisonExportDocumentMapper {

    static final int MAX_SUMMARY_CHARS = 512;
    static final int MAX_MISMATCHES = 50;
    static final int MAX_SNIPPET_CHARS = 256;

    private ReplayComparisonExportDocumentMapper() {}

    record BoundedComparison(ReplayComparisonExportComparisonDocument document, boolean truncated) {}

    static BoundedComparison toBoundedComparison(RuntimeTraceReplayComparisonResult result) {
        List<RuntimeTraceReplayFieldMismatch> raw = result.mismatches();
        boolean mismatchesCapped = raw.size() > MAX_MISMATCHES;
        List<ReplayComparisonExportMismatchLine> lines =
                new ArrayList<>(Math.min(raw.size(), MAX_MISMATCHES));
        boolean snippetTruncated = false;
        for (int i = 0; i < raw.size() && i < MAX_MISMATCHES; i++) {
            var line = toMismatchLine(raw.get(i));
            lines.add(line);
            snippetTruncated =
                    snippetTruncated
                            || wasSnippetTruncated(raw.get(i).originalSnippet(), line.originalSnippet())
                            || wasSnippetTruncated(raw.get(i).replaySnippet(), line.replaySnippet());
        }
        String summary = truncateSummary(result.summary());
        boolean summaryTruncated = wasSummaryTruncated(result.summary(), summary);
        boolean truncated = mismatchesCapped || summaryTruncated || snippetTruncated;
        ReplayComparisonExportComparisonDocument doc =
                new ReplayComparisonExportComparisonDocument(
                        result.originalTraceId(),
                        result.conversationId(),
                        result.messageId(),
                        result.comparisonMode().name(),
                        result.runtimeTraceReplayComparisonOutcome().name(),
                        result.replayOutcome().name(),
                        result.answerComparisonStatus().name(),
                        result.exactMatch(),
                        summary,
                        result.originalRouteKind(),
                        result.replayRouteKind(),
                        result.originalWorkflowName(),
                        result.replayWorkflowName(),
                        List.copyOf(lines));
        return new BoundedComparison(doc, truncated);
    }

    private static boolean wasSummaryTruncated(String original, String applied) {
        if (original == null) {
            return false;
        }
        return original.length() > MAX_SUMMARY_CHARS;
    }

    private static boolean wasSnippetTruncated(String original, String applied) {
        if (original == null) {
            return false;
        }
        return original.length() > MAX_SNIPPET_CHARS;
    }

    private static ReplayComparisonExportMismatchLine toMismatchLine(RuntimeTraceReplayFieldMismatch m) {
        RuntimeTraceReplayMismatchCategory c =
                m.category() != null ? m.category() : RuntimeTraceReplayMismatchCategory.FIELD_VALUE_MISMATCH;
        return new ReplayComparisonExportMismatchLine(
                m.fieldPath() != null ? m.fieldPath() : "",
                c.name(),
                snippet(m.originalSnippet()),
                snippet(m.replaySnippet()));
    }

    private static String snippet(String s) {
        if (s == null) {
            return "";
        }
        if (s.length() <= MAX_SNIPPET_CHARS) {
            return s;
        }
        return s.substring(0, MAX_SNIPPET_CHARS);
    }

    private static String truncateSummary(String s) {
        if (s == null) {
            return "";
        }
        if (s.length() <= MAX_SUMMARY_CHARS) {
            return s;
        }
        return s.substring(0, MAX_SUMMARY_CHARS);
    }
}
