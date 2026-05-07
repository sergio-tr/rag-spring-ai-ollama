package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalDiagnostics;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Privacy-safe execution hints exposed on chat assistant rows ({@code execution_metadata}) and {@link com.uniovi.rag.application.model.QueryResponse} telemetry.
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

        putReasoningTelemetry(trace, m);
        trace.retrievalDiagnostics().ifPresent(d -> putRetrievalDiagnosticsTelemetry(d, m));

        if (!trace.answerGroundingPolicy().isBlank()) {
            m.put("answerGroundingPolicy", trace.answerGroundingPolicy());
            // R4 stable key (avoid recomputing policy elsewhere).
            m.put("answerPolicy", trace.answerGroundingPolicy());
        }
        m.put("promptContextCharCount", trace.promptContextCharCount());
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
}
