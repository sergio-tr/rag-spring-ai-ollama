package com.uniovi.rag.application.service.runtime;

import com.uniovi.rag.application.service.runtime.factual.FactualVerifierTelemetry;
import com.uniovi.rag.application.service.runtime.routing.CompositionRouteTelemetryMapper;
import com.uniovi.rag.application.service.runtime.routing.safety.MonotonicSafetyTelemetrySupport;
import com.uniovi.rag.application.service.runtime.query.DefaultQueryClassifierAdapter;
import com.uniovi.rag.domain.model.QueryType;
import com.uniovi.rag.domain.runtime.engine.ExecutionStageTrace;
import com.uniovi.rag.domain.runtime.engine.ExecutionTrace;
import com.uniovi.rag.domain.runtime.retrieval.FusionTelemetry;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalDiagnostics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.uniovi.rag.domain.runtime.engine.ExecutionStageOutcome;
import com.uniovi.rag.domain.runtime.retrieval.RetrievalMode;

/**
 * Privacy-safe execution hints exposed on chat assistant rows ({@code execution_metadata}) and {@link com.uniovi.rag.application.result.chat.QueryResponse} telemetry.
 */
public final class ChatExecutionTelemetryMapper {

    private ChatExecutionTelemetryMapper() {
    }

    /**
     * Adds Lab-export retrieval id lists ({@code retrieved_chunk_ids}, {@code retrieved_document_ids}) from
     * persisted response sources when retrieval succeeded.
     */
    public static void enrichRetrievedIdentifiersFromSources(
            Map<String, Object> telemetry, List<Map<String, Object>> responseSources) {
        if (telemetry == null || responseSources == null || responseSources.isEmpty()) {
            return;
        }
        Set<String> chunkIds = new LinkedHashSet<>();
        Set<String> documentIds = new LinkedHashSet<>();
        for (Map<String, Object> source : responseSources) {
            if (source == null || source.isEmpty()) {
                continue;
            }
            String chunkId = firstNonBlank(source, "chunkId");
            if (chunkId == null && source.get("metadata") instanceof Map<?, ?> meta) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mm = (Map<String, Object>) meta;
                chunkId = firstNonBlank(mm, "chunkId");
            }
            if (chunkId != null) {
                chunkIds.add(chunkId);
            }
            String documentId = firstNonBlank(source, "documentId", "document_id", "projectDocumentId");
            if (documentId != null) {
                documentIds.add(documentId);
            }
        }
        if (!chunkIds.isEmpty()) {
            telemetry.put("retrieved_chunk_ids", new ArrayList<>(chunkIds));
        }
        if (!documentIds.isEmpty()) {
            telemetry.put("retrieved_document_ids", new ArrayList<>(documentIds));
        }
    }

    private static String firstNonBlank(Map<String, Object> row, String... keys) {
        for (String key : keys) {
            Object v = row.get(key);
            if (v == null) {
                continue;
            }
            String s = String.valueOf(v).trim();
            if (!s.isEmpty()) {
                return s;
            }
        }
        return null;
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
        putRetrievalRouteTelemetry(trace, m);
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
        putQueryExpansionTelemetry(trace, m);
        putReasoningTelemetry(trace, m);
        trace.retrievalDiagnostics().ifPresent(d -> putRetrievalDiagnosticsTelemetry(trace, d, m));
        putDateGroundingTelemetry(trace, m);
        putRuntimeAnswerMetaTelemetry(trace, m);
        putFactualVerifierTelemetry(trace, m);

        m.putAll(ToolExecutionTelemetryMapper.fromTrace(trace));

        parseCorpusBudgetTelemetry(trace, m);

        if (!trace.answerGroundingPolicy().isBlank()) {
            m.put("answerGroundingPolicy", trace.answerGroundingPolicy());
            // Stable key (avoid recomputing policy elsewhere).
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

        // Summary fields (no chain-of-thought).
        int contextChunkCount = resolveContextChunkCount(trace);
        if (contextChunkCount >= 0) {
            m.put("contextChunkCount", contextChunkCount);
        }
        m.put(
                "effectiveContextPresent",
                trace.promptContextCharCount() > 0 || contextChunkCount > 0 || trace.sourceCount() > 0);
        m.put("closestEvidenceAvailable", trace.sourceCount() > 0);
        m.put("judgeApplied", trace.judgeAttempted());
        m.put("memoryApplied", trace.memoryCondensationUsed());
        m.put("adaptiveRoutingApplied", trace.routingAttempted());
        m.put("clarificationRequired", clarificationRequired);

        CompositionRouteTelemetryMapper.enrich(m);
        putMonotonicSafetyTelemetry(trace, m);

        return Map.copyOf(m);
    }

    private static void putMonotonicSafetyTelemetry(ExecutionTrace trace, Map<String, Object> m) {
        if (trace.stages() == null) {
            return;
        }
        for (ExecutionStageTrace stage : trace.stages()) {
            if (stage != null && MonotonicSafetyTelemetrySupport.STAGE_NAME.equals(stage.stageName())) {
                MonotonicSafetyTelemetrySupport.enrichFromStageMessage(m, stage.message());
                return;
            }
        }
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
                    || "UNAVAILABLE".equalsIgnoreCase(classifierStatus)
                    || "LOW_CONFIDENCE".equalsIgnoreCase(classifierStatus)
                    || "TIMEOUT".equalsIgnoreCase(classifierStatus)
                    || "INVALID_REQUEST".equalsIgnoreCase(classifierStatus)) {
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
                    m.put("queryTypePredicted", classifierLabel.trim());
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
            String confidence = quClassifyTokenAfter(msg, "classifierConfidence=");
            if (!confidence.isBlank()) {
                try {
                    m.put("classifierConfidence", Double.parseDouble(confidence));
                } catch (NumberFormatException ignored) {
                    m.put("classifierConfidence", confidence);
                }
            }
            String labelSetHash = quClassifyTokenAfter(msg, "classifierLabelSetHash=");
            if (!labelSetHash.isBlank()) {
                m.put("classifierLabelSetHash", labelSetHash);
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

    private static String quClassifyTokenAfter(String msg, String key) {
        int start = msg.indexOf(key);
        if (start < 0) {
            return "";
        }
        start += key.length();
        int end = msg.length();
        for (String next :
                List.of(
                        " classifierModelId=",
                        " classifierLabel=",
                        " classifierStatus=",
                        " classifierConfidence=",
                        " classifierLabelSetHash=",
                        " note=")) {
            int idx = msg.indexOf(next, start);
            if (idx > start && idx < end) {
                end = idx;
            }
        }
        return msg.substring(start, end).trim();
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

    private static void putQueryExpansionTelemetry(ExecutionTrace trace, Map<String, Object> m) {
        Optional<ExecutionStageTrace> stage =
                trace.stages().stream().filter(s -> "qu_expand".equals(s.stageName())).findFirst();
        if (stage.isEmpty()) {
            m.put("query_expansion.enabled", false);
            m.put("query_expansion.applied", false);
            return;
        }
        String msg = stage.get().message() != null ? stage.get().message() : "";
        boolean disabled = msg.contains("qu_status=DISABLED");
        m.put("query_expansion.enabled", !disabled);
        m.put("query_expansion.applied", msg.contains("applied=true"));
        putTraceField(msg, "strategy=", "query_expansion.strategy", m);
        putTraceField(msg, "original=", "query_expansion.original_query", m);
        putTraceField(msg, "expanded=", "query_expansion.expanded_query", m);
        if (msg.contains("qu_status=ERROR") || msg.contains(" note=")) {
            putTraceField(msg, "note=", "query_expansion.warning", m);
        }
    }

    private static void putTraceField(String msg, String key, String targetKey, Map<String, Object> m) {
        int idx = msg.indexOf(key);
        if (idx < 0) {
            return;
        }
        String tail = msg.substring(idx + key.length()).trim();
        int space = tail.indexOf(' ');
        String value = space >= 0 ? tail.substring(0, space).trim() : tail.trim();
        if (!value.isBlank()) {
            m.put(targetKey, value);
        }
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

    /**
     * Final packed chunk count when retrieval ran; -1 when retrieval was not used (omit from export).
     */
    private static int resolveContextChunkCount(ExecutionTrace trace) {
        if (trace == null || !trace.retrievalUsed()) {
            return -1;
        }
        if (trace.retrievalDiagnostics().isPresent()) {
            RetrievalDiagnostics d = trace.retrievalDiagnostics().get();
            if (d.afterCompressionCount() > 0) {
                return d.afterCompressionCount();
            }
            if (d.afterFilterCount() > 0) {
                return d.afterFilterCount();
            }
            if (d.denseCandidateCount() > 0) {
                return d.denseCandidateCount();
            }
        }
        if (trace.sourceCount() > 0) {
            return trace.sourceCount();
        }
        return 0;
    }

    private static void putRetrievalDiagnosticsTelemetry(
            ExecutionTrace trace, RetrievalDiagnostics d, Map<String, Object> m) {
        m.put("retrievalMode", d.retrievalMode().name());
        m.put("retrievalRerankApplied", d.rerankApplied());
        m.put("rerankApplied", d.rerankApplied());
        m.put("retrievalDenseCandidateCount", d.denseCandidateCount());
        m.put("denseCandidateCount", d.denseCandidateCount());
        m.put("retrievalSparseCandidateCount", d.sparseCandidateCount());
        m.put("sparseCandidateCount", d.sparseCandidateCount());
        m.put("retrievalAfterFusionCount", d.afterFusionCount());
        m.put("mergedCandidateCount", d.afterFusionCount());
        m.put("retrievalDedupedCandidateCount", d.dedupedCandidateCount());
        m.put("dedupedCandidateCount", d.dedupedCandidateCount());
        m.put("retrievalBeforePostRetrievalCount", d.beforePostRetrievalCount());
        m.put("retrievalAfterRerankCount", d.afterRerankCount());
        m.put("rerankedCandidateCount", d.afterRerankCount());
        m.put("retrievalAfterFilterCount", d.afterFilterCount());
        m.put("retrievalAfterCompressionCount", d.afterCompressionCount());
        m.put("finalContextChunkCount", d.afterCompressionCount());
        d.retrievalEffectiveTopK().ifPresent(v -> m.put("retrievalEffectiveTopK", v));
        d.retrievalEffectiveSimilarityThreshold().ifPresent(v -> m.put("retrievalEffectiveSimilarityThreshold", v));
        d.retrievalDenseFetchLimit().ifPresent(v -> m.put("retrievalDenseFetchLimit", v));
        d.retrievalContextReductionReason().ifPresent(v -> m.put("retrievalContextReductionReason", v));
        m.put("retrievalProtectedCandidateCount", d.protectedCandidateCount());
        m.put("retrievalDroppedCandidateCount", d.droppedCandidateCount());
        m.put("retrievalRerankOrderChanged", d.rerankOrderChanged());
        m.put("rerankChangedOrder", d.rerankOrderChanged());
        m.put("retrievalCompressionCharsBefore", d.compressionCharsBefore());
        m.put("retrievalCompressionCharsAfter", d.compressionCharsAfter());
        m.put("compressedContextCharCount", d.compressionCharsAfter());
        m.put("originalContextCharCount", d.compressionCharsBefore());
        boolean compressionApplied =
                d.compressionCharsAfter() > 0
                        && d.compressionCharsBefore() > 0
                        && d.compressionCharsAfter() < d.compressionCharsBefore();
        m.put("retrievalCompressionApplied", compressionApplied);
        m.put("compressionApplied", compressionApplied);
        d.fusionMode().ifPresent(mode -> m.put("retrievalFusionMode", mode.name()));
        d.rerankScoreSummary().ifPresent(s -> m.put("retrievalRerankScoreSummaryTruncated", s));
        if (d.rerankApplied() && !d.rerankOrderChanged()) {
            m.put("rerankNoopReason", "order_unchanged");
        }
        int hybridCandidateCount = d.denseCandidateCount() + d.sparseCandidateCount();
        if (hybridCandidateCount >= 0) {
            m.put("hybridCandidateCount", hybridCandidateCount);
        }
        boolean hybridApplied =
                d.fusionTelemetry()
                        .map(FusionTelemetry::hybridApplied)
                        .orElseGet(
                                () ->
                                        d.retrievalMode()
                                                        == RetrievalMode
                                                                .HYBRID_DENSE_SPARSE
                                                && d.denseCandidateCount() > 0
                                                && d.sparseCandidateCount() > 0
                                                && d.afterFusionCount() > 0);
        m.put("hybridApplied", hybridApplied);
        d.fusionTelemetry().ifPresent(ft -> m.put("fusionStrategy", ft.fusionStrategy()));
        d.fusionTelemetry().ifPresent(ft -> m.put("preFusionCount", ft.preFusionCount()));
        d.fusionTelemetry().ifPresent(ft -> m.put("postFusionCount", ft.postFusionCount()));
        d.fusionTelemetry().ifPresent(ft -> m.put("metadataCandidateCount", ft.metadataCandidateCount()));
        d.sparseTelemetry().ifPresent(st -> m.put("sparseQueryOriginal", st.originalQuery()));
        d.sparseTelemetry().ifPresent(st -> m.put("sparseQueryRewritten", st.rewrittenQuery()));
        d.sparseTelemetry().ifPresent(st -> m.put("sparseFallbackStage", st.fallbackStage().name()));
        d.sparseTelemetry().ifPresent(st -> m.put("sparseHit", st.hit()));
        d.metadataFilterTelemetry()
                .ifPresent(mt -> m.put("metadataFilterApplied", mt.applied()));
        d.metadataFilterTelemetry()
                .ifPresent(mt -> m.put("metadataFilterFallback", mt.fallback()));
        String origins = candidateOriginsFromFusionStage(trace);
        if (!origins.isBlank()) {
            m.put("candidateOrigins", origins);
        }
        putRetrievalSparseStatusFromStages(trace, d, m);
    }

    private static void putRetrievalRouteTelemetry(ExecutionTrace trace, Map<String, Object> m) {
        String route = inferRetrievalRouteFromTrace(trace);
        if (!route.isBlank()) {
            m.put("retrievalRoute", route);
        }
    }

    private static String inferRetrievalRouteFromTrace(ExecutionTrace trace) {
        if (trace == null) {
            return "";
        }
        Optional<RetrievalDiagnostics> diag = trace.retrievalDiagnostics();
        if (diag.isPresent()) {
            RetrievalDiagnostics d = diag.get();
            if (d.retrievalMode() == RetrievalMode.HYBRID_DENSE_SPARSE) {
                return trace.metadataUsed() ? "HYBRID_DENSE_SPARSE_METADATA" : "HYBRID_DENSE_SPARSE";
            }
            if (d.retrievalMode() == RetrievalMode.DENSE_ONLY) {
                if (trace.metadataUsed()) {
                    return "CHUNK_DENSE_METADATA";
                }
                String workflow = trace.workflowName() != null ? trace.workflowName() : "";
                if ("DocumentDenseRagWorkflow".equals(workflow)) {
                    return "DOCUMENT_DENSE";
                }
                return "CHUNK_DENSE";
            }
        }
        String workflow = trace.workflowName() != null ? trace.workflowName() : "";
        return switch (workflow) {
            case "DirectLlmWorkflow" -> "DIRECT_LLM";
            case "FullCorpusWorkflow", "CorpusGroundedDirectWorkflow" -> "FULL_CORPUS";
            case "DocumentDenseRagWorkflow" -> "DOCUMENT_DENSE";
            case "ChunkDenseMetadataWorkflow" -> "CHUNK_DENSE_METADATA";
            case "ChunkDenseRagWorkflow" -> "CHUNK_DENSE";
            default -> "";
        };
    }

    private static String candidateOriginsFromFusionStage(ExecutionTrace trace) {
        if (trace.stages() == null) {
            return "";
        }
        for (ExecutionStageTrace stage : trace.stages()) {
            if (stage == null || !"retrieval_fuse".equals(stage.stageName())) {
                continue;
            }
            String msg = stage.message() != null ? stage.message() : "";
            int idx = msg.indexOf("origins=");
            if (idx < 0) {
                return "";
            }
            return msg.substring(idx + "origins=".length()).trim();
        }
        return "";
    }

    private static void putRetrievalSparseStatusFromStages(
            ExecutionTrace trace, RetrievalDiagnostics d, Map<String, Object> m) {
        if (d.retrievalMode() != RetrievalMode.HYBRID_DENSE_SPARSE) {
            m.put("sparseRetrievalStatus", "NOT_APPLICABLE");
            return;
        }
        if (trace.stages() != null) {
            for (ExecutionStageTrace stage : trace.stages()) {
                if (stage == null || !"retrieval_sparse".equals(stage.stageName())) {
                    continue;
                }
                String msg = stage.message() != null ? stage.message() : "";
                if (stage.outcome() == ExecutionStageOutcome.SKIPPED
                        && msg.contains("sparse_unavailable")) {
                    m.put("retrievalSparseStatus", "sparse_unavailable");
                    m.put("sparseRetrievalStatus", "UNAVAILABLE");
                    return;
                }
                if (stage.outcome() == ExecutionStageOutcome.SUCCESS) {
                    if (d.sparseCandidateCount() > 0) {
                        m.put("sparseRetrievalStatus", "OK");
                    } else {
                        m.put("sparseRetrievalStatus", "ZERO_MATCHES");
                    }
                    return;
                }
            }
        }
        if (d.sparseCandidateCount() > 0) {
            m.put("sparseRetrievalStatus", "OK");
        } else {
            m.put("sparseRetrievalStatus", "ZERO_MATCHES");
        }
    }

    private static void putFactualVerifierTelemetry(ExecutionTrace trace, Map<String, Object> m) {
        if (trace.stages() == null) {
            return;
        }
        FactualVerifierTelemetry.enrichFromStages(trace.stages(), m);
        String policy = trace.answerGroundingPolicy();
        if (policy != null && !policy.isBlank()) {
            m.put("groundingPolicy", policy);
            m.put("negativeEvidenceGuardTriggered", "NEGATIVE_EVIDENCE".equals(policy)
                    || "NEGATIVE_FALSE_POSITIVE".equals(String.valueOf(m.getOrDefault("verifierFailureReason", "")))
                    || "UNRELATED_TOPIC".equals(String.valueOf(m.getOrDefault("verifierFailureReason", ""))));
            if (!m.containsKey("constraintType")) {
                m.put("constraintType", constraintTypeForPolicy(policy));
            }
        }
        for (ExecutionStageTrace stage : trace.stages()) {
            if (stage == null || !"final_answer_source".equals(stage.stageName())) {
                continue;
            }
            String source = tokenAfter(stage.message(), "finalAnswerSource=");
            if (source != null && !source.isBlank()) {
                m.put("finalAnswerSource", source);
            }
            return;
        }
    }

    private static String constraintTypeForPolicy(String policy) {
        return switch (policy) {
            case "NUMERIC_OR_DATE" -> "NUMERIC";
            case "ENTITY_OR_TOPIC" -> "TOPIC";
            case "NEGATIVE_EVIDENCE" -> "TOPIC";
            case "STRICT_GROUNDED" -> "MIXED";
            default -> "NONE";
        };
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
