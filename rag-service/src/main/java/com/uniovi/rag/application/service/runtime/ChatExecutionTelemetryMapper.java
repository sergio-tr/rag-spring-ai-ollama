package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.query.DefaultQueryClassifierAdapter;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalDiagnostics;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Privacy-safe execution hints exposed on chat assistant rows ({@code execution_metadata}) and {@link com.uniovi.rag.application.result.chat.QueryResponse} telemetry.
 */
public final class ChatExecutionTelemetryMapper {

    private ChatExecutionTelemetryMapper() {
    }

    /**
     * Maps orchestrator trace fields suitable for the product Chat UI (no raw prompts, packed context, or internal rationales).
     */
    public static Map<String, Object> fromTrace(ExecutionTrace trace) {
        if (trace == null) {
            return Map.of();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        if (trace.workflowName() != null && !trace.workflowName().isBlank()) {
            m.put("workflowName", trace.workflowName());
        }
        List<UUID> snapshotIds = trace.usedKnowledgeSnapshotIds();
        if (snapshotIds != null && !snapshotIds.isEmpty()) {
            m.put("selectedSnapshotIds", snapshotIds.stream().map(UUID::toString).toList());
        }
        boolean clarificationRequired =
                trace.clarificationQuestionAsked()
                        || "ASKED_CLARIFICATION".equalsIgnoreCase(trace.clarificationOutcome());
        m.put("clarificationRequired", clarificationRequired);
        if (!trace.clarificationOutcome().isBlank()) {
            m.put("clarificationOutcome", trace.clarificationOutcome());
        }

        m.put("memoryAttempted", trace.memoryAttempted());
        if (!trace.memoryOutcome().isBlank()) {
            m.put("memoryOutcome", trace.memoryOutcome());
        }
        m.put("memoryCondensationUsed", trace.memoryCondensationUsed());
        m.put("memoryFallbackApplied", trace.memoryFallbackApplied());

        m.put("routingAttempted", trace.routingAttempted());
        if (!trace.routingOutcome().isBlank()) {
            m.put("routingOutcome", trace.routingOutcome());
        }
        if (!trace.routingRouteKind().isBlank()) {
            m.put("routingRouteKind", trace.routingRouteKind());
        }
        m.put("routingFallbackApplied", trace.routingFallbackApplied());
        if (!trace.routingFallbackRouteKind().isBlank()) {
            m.put("routingFallbackRouteKind", trace.routingFallbackRouteKind());
        }

        m.put("judgeAttempted", trace.judgeAttempted());
        if (!trace.judgeFinalOutcome().isBlank()) {
            m.put("judgeFinalOutcome", trace.judgeFinalOutcome());
        }
        m.put("judgeFinalAnswerFromRetry", trace.judgeFinalAnswerFromRetry());
        if (!trace.judgeCandidateSource().isBlank()) {
            m.put("judgeCandidateSource", trace.judgeCandidateSource());
        }

        putClassifierTelemetry(trace, m);
        putReasoningTelemetry(trace, m);
        trace.retrievalDiagnostics().ifPresent(d -> putRetrievalDiagnosticsTelemetry(d, m));
        putDateGroundingTelemetry(trace, m);
        putRuntimeAnswerMetaTelemetry(trace, m);

        parseCorpusBudgetTelemetry(trace, m);

        if (!trace.answerGroundingPolicy().isBlank()) {
            m.put("answerGroundingPolicy", trace.answerGroundingPolicy());
            // R4 stable key (avoid recomputing policy elsewhere).
            m.put("answerPolicy", trace.answerGroundingPolicy());
            m.put("groundingPolicy", trace.answerGroundingPolicy());
        }
        m.put("promptContextCharCount", trace.promptContextCharCount());
        m.put("corpusChars", trace.promptContextCharCount());
        m.put("sourceCount", trace.sourceCount());
        m.put("abstentionTriggered", trace.abstentionTriggered());
        if (!trace.abstentionReason().isBlank()) {
            m.put("abstentionReason", trace.abstentionReason());
            m.put("abstentionReasonCode", trace.abstentionReason());
        }

        // R4 summary fields (no chain-of-thought).
        m.put("contextChunkCount", trace.packedContextBlockCount());
        m.put("effectiveContextPresent", trace.promptContextCharCount() > 0 || trace.packedContextBlockCount() > 0);
        m.put("closestEvidenceAvailable", trace.sourceCount() > 0);
        m.put("judgeApplied", trace.judgeAttempted());
        m.put("memoryApplied", trace.memoryCondensationUsed());
        m.put("adaptiveRoutingApplied", trace.routingAttempted());
        m.put("clarificationRequired", clarificationRequired);

        return Map.copyOf(m);
    }

    private static void parseCorpusBudgetTelemetry(ExecutionTrace trace, Map<String, Object> m) {
        if (trace.stages() == null) {
            return;
        }
        for (ExecutionStageTrace st : trace.stages()) {
            if (st == null || !"context_budget".equals(st.stageName())) {
                continue;
            }
            String msg = st.message();
            if (msg == null || msg.isBlank()) {
                return;
            }
            if (msg.contains("truncated=true")) {
                m.put("corpusTruncated", true);
            } else if (msg.contains("truncated=false")) {
                m.put("corpusTruncated", false);
            }
            return;
        }
    }

    private static void putClassifierTelemetry(ExecutionTrace trace, Map<String, Object> m) {
        String classifierStatus = trace.classifierStatus();
        if (classifierStatus != null && !classifierStatus.isBlank()) {
            m.put("classifierStatus", classifierStatus);
            if ("INVALID_OUTPUT".equalsIgnoreCase(classifierStatus)
                    || "UNAVAILABLE".equalsIgnoreCase(classifierStatus)) {
                m.put("classifierFallback", true);
            } else if ("OK".equalsIgnoreCase(classifierStatus)) {
                m.put("classifierFallback", false);
            }
        }
        String classifierLabel = trace.classifierLabel();
        if (classifierLabel != null && !classifierLabel.isBlank()) {
            m.put("classifierLabel", classifierLabel);
            if ("OK".equalsIgnoreCase(classifierStatus)
                    && !DefaultQueryClassifierAdapter.UNCLASSIFIED.equals(classifierLabel)) {
                try {
                    QueryType.valueOf(classifierLabel.trim());
                    m.put("predictedQueryType", classifierLabel.trim());
                } catch (IllegalArgumentException ignored) {
                    // Leave predictedQueryType unset when label is not a Java enum constant.
                }
            }
        }
        if (trace.stages() == null) {
            return;
        }
        for (ExecutionStageTrace stage : trace.stages()) {
            if (stage == null || !"qu_classify".equals(stage.stageName())) {
                continue;
            }
            String msg = stage.message();
            if (msg == null || msg.isBlank()) {
                return;
            }
            String modelId = classifierModelIdFromQuClassifyMessage(msg);
            if (!modelId.isBlank()) {
                m.put("classifierModelId", modelId);
                m.put("classifierModelIdUsed", modelId);
            }
            String note = classifierNoteFromQuClassifyMessage(msg);
            if (!note.isBlank() && Boolean.TRUE.equals(m.get("classifierFallback"))) {
                m.put("classifierFallbackReason", note);
            }
            return;
        }
    }

    private static String classifierNoteFromQuClassifyMessage(String msg) {
        int start = msg.indexOf("note=");
        if (start < 0) {
            return "";
        }
        return msg.substring(start + "note=".length()).trim();
    }

    private static String classifierModelIdFromQuClassifyMessage(String msg) {
        int start = msg.indexOf("classifierModelId=");
        if (start < 0) {
            return "";
        }
        start += "classifierModelId=".length();
        int end = msg.length();
        for (String next : List.of(" classifierLabel=", " classifierStatus=", " note=")) {
            int idx = msg.indexOf(next, start);
            if (idx > start && idx < end) {
                end = idx;
            }
        }
        return msg.substring(start, end).trim();
    }

    private static void putReasoningTelemetry(ExecutionTrace trace, Map<String, Object> m) {
        Optional<ExecutionStageTrace> plan =
                trace.stages().stream().filter(s -> "reasoning_plan".equals(s.stageName())).findFirst();
        if (plan.isEmpty()) {
            m.put("reasoningAttempted", false);
            return;
        }
        m.put("reasoningAttempted", true);
        String msg = plan.get().message();
        if (msg != null && !msg.isBlank()) {
            int si = msg.indexOf("strategy=");
            if (si >= 0) {
                String tail = msg.substring(si + "strategy=".length()).trim();
                int sumMark = tail.indexOf("summary=");
                String strategy = sumMark >= 0 ? tail.substring(0, sumMark).trim() : tail.trim();
                if (!strategy.isBlank()) {
                    m.put("reasoningStrategy", strategy);
                }
            }
            int sx = msg.indexOf("summary=");
            if (sx >= 0) {
                String summary = msg.substring(sx + "summary=".length()).trim();
                if (!summary.isBlank()) {
                    m.put("reasoningPlanSummaryTruncated", summary);
                }
            }
        }
    }

    private static void putRetrievalDiagnosticsTelemetry(RetrievalDiagnostics d, Map<String, Object> m) {
        m.put("retrievalRerankApplied", d.rerankApplied());
        m.put("retrievalDenseCandidateCount", d.denseCandidateCount());
        m.put("retrievalAfterFusionCount", d.afterFusionCount());
        m.put("retrievalBeforePostRetrievalCount", d.beforePostRetrievalCount());
        m.put("retrievalAfterRerankCount", d.afterRerankCount());
        m.put("retrievalAfterFilterCount", d.afterFilterCount());
        m.put("retrievalAfterCompressionCount", d.afterCompressionCount());
        m.put("retrievalProtectedCandidateCount", d.protectedCandidateCount());
        m.put("retrievalDroppedCandidateCount", d.droppedCandidateCount());
        d.rerankScoreSummary().ifPresent(s -> m.put("retrievalRerankScoreSummaryTruncated", s));
    }

    private static void putRuntimeAnswerMetaTelemetry(ExecutionTrace trace, Map<String, Object> m) {
        if (trace.stages() == null) {
            return;
        }
        for (ExecutionStageTrace stage : trace.stages()) {
            if (stage == null || !"runtime_answer_meta".equals(stage.stageName())) {
                continue;
            }
            String msg = stage.message();
            if (msg == null || msg.isBlank()) {
                return;
            }
            putBooleanIfPresent(m, "documentBound", tokenAfter(msg, "documentBound="));
            return;
        }
    }

    private static void putDateGroundingTelemetry(ExecutionTrace trace, Map<String, Object> m) {
        if (trace.stages() == null || trace.stages().isEmpty()) {
            return;
        }
        Optional<ExecutionStageTrace> stage = trace.stages().stream()
                .filter(s -> s != null
                        && ("date_grounding_answer_policy".equals(s.stageName())
                        || "date_grounding".equals(s.stageName())))
                .reduce((first, second) -> second);
        if (stage.isEmpty() || stage.get().message() == null || stage.get().message().isBlank()) {
            return;
        }
        String msg = stage.get().message();
        putIfPresent(m, "requestedDate", tokenAfter(msg, "requestedDate="));
        putIfPresent(m, "requestedDatePrecision", tokenAfter(msg, "requestedDatePrecision="));
        putIfPresent(m, "matchedDocumentDates", splitToken(tokenAfter(msg, "matchedDocumentDates=")));
        putIfPresent(m, "sourceDates", splitToken(tokenAfter(msg, "sourceDates=")));
        putIfPresent(m, "topSourceDate", tokenAfter(msg, "topSourceDate="));
        putIfPresent(m, "closestAvailableDate", tokenAfter(msg, "closestAvailableDate="));
        putIfPresent(m, "abstentionReason", tokenAfter(msg, "abstentionReason="));
        putIfPresent(m, "groundingPolicyApplied", tokenAfter(msg, "groundingPolicyApplied="));
        putBooleanIfPresent(m, "exactDateMatch", tokenAfter(msg, "exactDateMatch="));
        putBooleanIfPresent(m, "exactDocumentMatch", tokenAfter(msg, "exactDocumentMatch="));
        putBooleanIfPresent(m, "dateMismatchDetected", tokenAfter(msg, "dateMismatchDetected="));
        putIntIfPresent(m, "candidateSourceCountBeforeDateFilter", tokenAfter(msg, "candidateSourceCountBeforeDateFilter="));
        putIntIfPresent(m, "candidateSourceCountAfterDateFilter", tokenAfter(msg, "candidateSourceCountAfterDateFilter="));
        putBooleanIfPresent(m, "dateBoostApplied", tokenAfter(msg, "dateBoostApplied="));
    }

    private static void putIntIfPresent(Map<String, Object> m, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        try {
            m.put(key, Integer.parseInt(value));
        } catch (NumberFormatException ignored) {
            // Keep telemetry best-effort; malformed trace tokens should not fail Chat responses.
        }
    }

    private static void putBooleanIfPresent(Map<String, Object> m, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        m.put(key, Boolean.parseBoolean(value));
    }

    private static void putIfPresent(Map<String, Object> m, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String s && s.isBlank()) {
            return;
        }
        if (value instanceof List<?> l && l.isEmpty()) {
            return;
        }
        m.put(key, value);
    }

    private static List<String> splitToken(String value) {
        if (value == null || value.isBlank() || "[]".equals(value)) {
            return List.of();
        }
        String normalized = value.replace("[", "").replace("]", "").trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        return List.of(normalized.split("\\|")).stream()
                .filter(s -> s != null && !s.isBlank())
                .toList();
    }

    private static String tokenAfter(String msg, String key) {
        int start = msg.indexOf(key);
        if (start < 0) {
            return "";
        }
        start += key.length();
        int end = msg.length();
        for (String next : List.of(
                "requestedDate=",
                "requestedDatePrecision=",
                "exactDateMatch=",
                "dateMismatchDetected=",
                "sourceDates=",
                "matchedDocumentDates=",
                "exactDocumentMatch=",
                "topSourceDate=",
                "closestAvailableDate=",
                "abstentionReason=",
                "groundingPolicyApplied=",
                "candidateSourceCountBeforeDateFilter=",
                "candidateSourceCountAfterDateFilter=",
                "dateBoostApplied=",
                "documentBound=",
                "before=",
                "after=")) {
            int idx = msg.indexOf(next, start);
            if (idx > start && idx < end) {
                end = idx;
            }
        }
        return msg.substring(start, end).trim();
    }
}
