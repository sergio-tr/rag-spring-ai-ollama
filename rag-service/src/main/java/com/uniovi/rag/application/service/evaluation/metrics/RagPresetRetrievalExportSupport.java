package com.uniovi.rag.application.service.evaluation.metrics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Promotes chunk-level retrieval telemetry from persisted {@code metrics_payload} onto Lab export surfaces.
 */
public final class RagPresetRetrievalExportSupport {

    public static final List<String> FLAT_CSV_KEYS =
            List.of(
                    "retrievalDenseCandidateCount",
                    "retrievalAfterFilterCount",
                    "contextChunkCount",
                    "promptContextCharCount",
                    "sourceCount",
                    RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_APPLIED,
                    RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_STRATEGY,
                    RagPresetAdvancedRetrievalMetrics.KEY_DENSE_CANDIDATE_COUNT,
                    RagPresetAdvancedRetrievalMetrics.KEY_SPARSE_CANDIDATE_COUNT,
                    RagPresetAdvancedRetrievalMetrics.KEY_HYBRID_CANDIDATE_COUNT,
                    RagPresetAdvancedRetrievalMetrics.KEY_MERGED_CANDIDATE_COUNT,
                    RagPresetAdvancedRetrievalMetrics.KEY_DEDUPED_CANDIDATE_COUNT,
                    RagPresetAdvancedRetrievalMetrics.KEY_RERANKED_CANDIDATE_COUNT,
                    RagPresetAdvancedRetrievalMetrics.KEY_FINAL_CONTEXT_CHUNK_COUNT,
                    RagPresetAdvancedRetrievalMetrics.KEY_RERANK_APPLIED,
                    RagPresetAdvancedRetrievalMetrics.KEY_RERANK_CHANGED_ORDER,
                    RagPresetAdvancedRetrievalMetrics.KEY_COMPRESSION_APPLIED,
                    RagPresetAdvancedRetrievalMetrics.KEY_COMPRESSED_CONTEXT_CHAR_COUNT,
                    RagPresetAdvancedRetrievalMetrics.KEY_ADVANCED_RETRIEVAL_FALLBACK_REASON,
                    RagPresetAdvancedRetrievalMetrics.KEY_CANDIDATE_ORIGINS,
                    RagPresetAdvancedRetrievalMetrics.KEY_SPARSE_RETRIEVAL_STATUS,
                    RagPresetAdvancedRetrievalMetrics.KEY_HYBRID_APPLIED,
                    RagPresetAdvancedRetrievalMetrics.KEY_RETRIEVAL_ROUTE,
                    RagPresetAdvancedRetrievalMetrics.KEY_RETRIEVAL_MODE,
                    RagPresetAdvancedRetrievalMetrics.KEY_RERANK_NOOP_REASON,
                    RagPresetAdvancedRetrievalMetrics.KEY_ORIGINAL_CONTEXT_CHAR_COUNT,
                    RagPresetAdvancedRetrievalMetrics.KEY_SPARSE_QUERY_REWRITTEN,
                    RagPresetAdvancedRetrievalMetrics.KEY_SPARSE_FALLBACK_STAGE,
                    RagPresetAdvancedRetrievalMetrics.KEY_SPARSE_HIT,
                    RagPresetAdvancedRetrievalMetrics.KEY_FUSION_STRATEGY,
                    RagPresetAdvancedRetrievalMetrics.KEY_PRE_FUSION_COUNT,
                    RagPresetAdvancedRetrievalMetrics.KEY_POST_FUSION_COUNT,
                    RagPresetAdvancedRetrievalMetrics.KEY_METADATA_CANDIDATE_COUNT,
                    RagPresetAdvancedRetrievalMetrics.KEY_METADATA_FILTER_APPLIED,
                    RagPresetAdvancedRetrievalMetrics.KEY_METADATA_FILTER_FALLBACK,
                    "materializationStrategy",
                    "supportsMetadata",
                    "metadataEnabled");

    public static final List<String> BASELINE_FLAT_KEYS =
            List.of(
                    "workflowName",
                    "useRetrieval",
                    "corpusRequired",
                    "requiresVectorIndex",
                    "groupKey",
                    "corpusAvailable",
                    "corpusChars",
                    "naiveFullCorpusInPromptEnabled");

    public static final List<String> ANALYSIS_JSON_KEYS =
            List.of(
                    "presetKey",
                    "presetLabel",
                    "workflowName",
                    "queryTypeExpected",
                    "queryTypePredicted",
                    "queryTypeMatch",
                    DatasetMetricContract.KEY_ANSWERABILITY,
                    DatasetMetricContract.KEY_ANSWERABILITY_SOURCE,
                    DatasetMetricContract.KEY_ANSWERABILITY_RULE_ID,
                    DatasetMetricContract.KEY_ANSWERABILITY_CONFIDENCE,
                    DatasetMetricContract.KEY_ANSWERABILITY_RULES_VERSION,
                    DatasetMetricContract.KEY_LABELLED_DATASET_SHA256,
                    DatasetMetricContract.KEY_REVIEW_REQUIRED,
                    "subsetId",
                    "subsetName",
                    "subsetVersion",
                    "negativeEvidenceFalsePositive",
                    DatasetMetricContract.KEY_EXPECTED_ANSWER_PRESENT,
                    AbstentionDetector.KEY_ABSTAINED,
                    AbstentionDetector.KEY_ABSTENTION_REASON,
                    RagPresetAnalysisMetrics.KEY_ABSTENTION_CORRECTNESS,
                    RagPresetAnalysisMetrics.KEY_ABSTENTION_SCORE,
                    "finalScore",
                    "scoreFinal",
                    "structuredScore",
                    "structuredScoreStatus",
                    "semanticScore",
                    "exactMatchNormalized",
                    "expectedAnswerContained",
                    "countMatch",
                    "booleanMatch",
                    "dateMatch",
                    "durationMatch",
                    "fieldMatchScore",
                    "entityPrecision",
                    "entityRecall",
                    "entityF1",
                    "listPrecision",
                    "listRecall",
                    "listF1",
                    "sourceSupport",
                    "faithfulness",
                    "answerRelevance",
                    RagPresetAnalysisMetrics.KEY_SCORE_UNAVAILABLE_REASON,
                    "finalScoreAvailable",
                    "finalScoreStatus",
                    "retrievalCoverageStatus",
                    "sourceCoverageStatus",
                    "retrievalQualityStatus",
                    "contextPresent",
                    "recallAt1",
                    "recallAt3",
                    "recallAt5",
                    "recallAtK",
                    "mrr",
                    "ndcgAt5",
                    "classifierStatus",
                    "classifierConfidence",
                    "classifierModelId",
                    "classifierLabelSetHash",
                    "classifierFallback",
                    "classifierFallbackReason",
                    RagPresetClassifierMetrics.KEY_ROUTE_SUPPRESSED_BY_CLASSIFIER,
                    RagPresetClassifierMetrics.KEY_ROUTE_SUPPRESSED_REASON,
                    RagPresetClassifierMetrics.KEY_HEURISTIC_ROUTE_USED,
                    "correctAbstention",
                    "wrongAbstention",
                    RagPresetToolMetrics.KEY_TOOL_APPLICABLE,
                    RagPresetToolMetrics.KEY_TOOL_SELECTED,
                    RagPresetToolMetrics.KEY_TOOL_NAME,
                    RagPresetToolMetrics.KEY_TOOL_EXECUTED,
                    RagPresetToolMetrics.KEY_TOOL_SUCCEEDED,
                    RagPresetToolMetrics.KEY_TOOL_FALLBACK_REASON,
                    RagPresetToolMetrics.KEY_TOOL_RESULT_USED_AS_FINAL,
                    RagPresetToolMetrics.KEY_DETERMINISTIC_TOOL_ROUTE,
                    RagPresetToolMetrics.KEY_FUNCTION_CALLING_USED,
                    RagPresetToolMetrics.KEY_FUNCTION_CALL_ATTEMPTED,
                    RagPresetToolMetrics.KEY_FUNCTION_CALL_NAME,
                    RagPresetToolMetrics.KEY_FUNCTION_CALL_ARGUMENTS_VALID,
                    RagPresetToolMetrics.KEY_FUNCTION_CALL_SUCCEEDED,
                    RagPresetToolMetrics.KEY_FUNCTION_CALL_FALLBACK_REASON,
                    RagPresetToolMetrics.KEY_FUNCTION_RESULT_USED_AS_FINAL,
                    RagPresetToolMetrics.KEY_FUNCTION_RESULT_USED_AS_CONTEXT,
                    RagPresetToolMetrics.KEY_FUNCTION_CALL_ROUTE,
                    RagPresetToolMetrics.KEY_EXECUTION_ROUTE,
                    RagPresetToolMetrics.KEY_QUERY_TYPE_SOURCE,
                    RagPresetToolMetrics.KEY_TOOL_COVERAGE_STATUS,
                    RagPresetToolMetrics.KEY_ROUTING_ROUTE_KIND,
                    RagPresetAdvisorMetrics.KEY_ADVISOR_ENABLED,
                    RagPresetAdvisorMetrics.KEY_ADVISOR_ROUTE,
                    RagPresetAdvisorMetrics.KEY_ADVISOR_ATTEMPTED,
                    RagPresetAdvisorMetrics.KEY_ADVISOR_APPLIED,
                    RagPresetAdvisorMetrics.KEY_ADVISOR_NAME,
                    RagPresetAdvisorMetrics.KEY_ADVISOR_TYPE,
                    RagPresetAdvisorMetrics.KEY_ADVISOR_CONTRIBUTION_TYPE,
                    RagPresetAdvisorMetrics.KEY_ADVISOR_CHANGED_QUERY,
                    RagPresetAdvisorMetrics.KEY_ADVISOR_CHANGED_CONTEXT,
                    RagPresetAdvisorMetrics.KEY_ADVISOR_CHANGED_PROMPT,
                    RagPresetAdvisorMetrics.KEY_ADVISOR_VALIDATED_ANSWER,
                    RagPresetAdvisorMetrics.KEY_ADVISOR_FALLBACK_REASON,
                    RagPresetAdvisorMetrics.KEY_ADVISOR_RESULT_USED);

    private RagPresetRetrievalExportSupport() {}

    public static void putJsonExportFields(Map<String, Object> target, Map<String, Object> metricsPayload) {
        if (target == null || metricsPayload == null || metricsPayload.isEmpty()) {
            return;
        }
        for (String key : FLAT_CSV_KEYS) {
            putIfPresent(target, key, metricsPayload.get(key));
        }
        for (String key : BASELINE_FLAT_KEYS) {
            putIfPresent(target, key, metricsPayload.get(key));
        }
        putEffectiveContextPresent(target, metricsPayload);
        putIdList(target, "retrievedChunkIds", metricsPayload.get("retrieved_chunk_ids"));
        putIdList(target, "retrievedDocumentIds", metricsPayload.get("retrieved_document_ids"));
        promoteSnapshotCapabilityFields(target, metricsPayload);
        for (String key : ANALYSIS_JSON_KEYS) {
            putIfPresent(target, key, metricsPayload.get(key));
        }
        putFinalScoreAvailability(target, metricsPayload);
    }

    public static void putCsvExportFields(Map<String, String> target, Map<String, Object> metricsPayload) {
        if (target == null || metricsPayload == null || metricsPayload.isEmpty()) {
            return;
        }
        for (String key : FLAT_CSV_KEYS) {
            target.put(key, csvVal(metricsPayload.get(key)));
        }
        for (String key : BASELINE_FLAT_KEYS) {
            target.put(key, csvVal(metricsPayload.get(key)));
        }
        target.put("effectiveContextPresent", csvVal(effectiveContextPresent(metricsPayload)));
        target.put("retrievedChunkIds", joinIds(metricsPayload.get("retrieved_chunk_ids")));
        if (target.get("retrievedChunkIds") == null || target.get("retrievedChunkIds").isBlank()) {
            target.put("retrievedChunkIds", joinIds(metricsPayload.get("retrievedChunkIds")));
        }
        target.put("retrievedDocumentIds", joinIds(metricsPayload.get("retrieved_document_ids")));
        if (target.get("retrievedDocumentIds") == null || target.get("retrievedDocumentIds").isBlank()) {
            target.put("retrievedDocumentIds", joinIds(metricsPayload.get("retrievedDocumentIds")));
        }
        promoteSnapshotCapabilityFieldsCsv(target, metricsPayload);
        putFinalScoreAvailabilityCsv(target, metricsPayload);
    }

    public static List<Map<String, String>> enrichSourceEntries(List<Map<String, String>> sources, Map<String, Object> mp) {
        if (sources != null && !sources.isEmpty()) {
            List<Map<String, String>> enriched = new ArrayList<>();
            for (Map<String, String> src : sources) {
                Map<String, String> copy = new LinkedHashMap<>(src);
                if (copy.get("chunkId") == null || copy.get("chunkId").isBlank()) {
                    String chunkId = chunkIdFromMetricsSources(mp, copy.get("documentId"));
                    if (!chunkId.isBlank()) {
                        copy.put("chunkId", chunkId);
                    }
                }
                enriched.add(Map.copyOf(copy));
            }
            return List.copyOf(enriched);
        }
        return sources != null ? sources : List.of();
    }

    private static void promoteSnapshotCapabilityFields(Map<String, Object> target, Map<String, Object> mp) {
        if (target.containsKey("supportsMetadata")) {
            return;
        }
        Object caps = mp.get("activeSnapshotCapabilities");
        if (caps instanceof Map<?, ?> raw) {
            @SuppressWarnings("unchecked")
            Map<String, Object> snapshotCaps = (Map<String, Object>) raw;
            putIfPresent(target, "supportsMetadata", snapshotCaps.get("supportsMetadata"));
        }
    }

    private static void promoteSnapshotCapabilityFieldsCsv(Map<String, String> target, Map<String, Object> mp) {
        if (target.containsKey("supportsMetadata") && !target.get("supportsMetadata").isBlank()) {
            return;
        }
        Object caps = mp.get("activeSnapshotCapabilities");
        if (caps instanceof Map<?, ?> raw) {
            @SuppressWarnings("unchecked")
            Map<String, Object> snapshotCaps = (Map<String, Object>) raw;
            Object value = snapshotCaps.get("supportsMetadata");
            if (value != null) {
                target.put("supportsMetadata", csvVal(value));
            }
        }
    }

    private static void putFinalScoreAvailability(Map<String, Object> target, Map<String, Object> mp) {
        if (target.containsKey("finalScoreAvailable")) {
            return;
        }
        target.put("finalScoreAvailable", ScoreExportSupport.isFinalScoreAvailable(mp));
        target.put("finalScoreStatus", ScoreExportSupport.finalScoreStatus(mp));
    }

    private static void putFinalScoreAvailabilityCsv(Map<String, String> target, Map<String, Object> mp) {
        if (target.containsKey("finalScoreAvailable") && !target.get("finalScoreAvailable").isBlank()) {
            return;
        }
        target.put("finalScoreAvailable", csvVal(ScoreExportSupport.isFinalScoreAvailable(mp)));
        target.put("finalScoreStatus", csvVal(ScoreExportSupport.finalScoreStatus(mp)));
    }

    private static void putEffectiveContextPresent(Map<String, Object> target, Map<String, Object> mp) {
        Object existing = mp.get("effectiveContextPresent");
        if (existing != null) {
            target.put("effectiveContextPresent", existing);
            return;
        }
        Boolean derived = effectiveContextPresent(mp);
        if (derived != null) {
            target.put("effectiveContextPresent", derived);
        }
    }

    private static Boolean effectiveContextPresent(Map<String, Object> mp) {
        if (intVal(mp.get("promptContextCharCount")) > 0) {
            return true;
        }
        if (intVal(mp.get("contextChunkCount")) > 0) {
            return true;
        }
        if (intVal(mp.get("sourceCount")) > 0) {
            return true;
        }
        return null;
    }

    private static void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value == null) {
            return;
        }
        if (value instanceof String s && s.isBlank()) {
            return;
        }
        target.put(key, value);
    }

    @SuppressWarnings("unchecked")
    private static void putIdList(Map<String, Object> target, String key, Object raw) {
        if (raw instanceof List<?> list && !list.isEmpty()) {
            target.put(key, List.copyOf(list));
            return;
        }
        if (raw instanceof String s && !s.isBlank()) {
            target.put(key, List.of(s.trim()));
        }
    }

    private static String chunkIdFromMetricsSources(Map<String, Object> mp, String documentId) {
        Object raw = mp.get("sources");
        if (!(raw instanceof List<?> list)) {
            return "";
        }
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> sm)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> src = (Map<String, Object>) sm;
            String doc = firstNonBlank(str(src.get("documentId")), str(src.get("document_id")));
            if (documentId != null && !documentId.isBlank() && !documentId.equals(doc)) {
                continue;
            }
            String chunkId = firstNonBlank(str(src.get("chunkId")));
            if (!chunkId.isBlank()) {
                return chunkId;
            }
            if (src.get("metadata") instanceof Map<?, ?> meta) {
                @SuppressWarnings("unchecked")
                Map<String, Object> mm = (Map<String, Object>) meta;
                chunkId = firstNonBlank(str(mm.get("chunkId")));
                if (!chunkId.isBlank()) {
                    return chunkId;
                }
            }
        }
        return "";
    }

    private static int intVal(Object raw) {
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private static String csvVal(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String joinIds(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Object o : list) {
            if (o == null) {
                continue;
            }
            String s = String.valueOf(o).trim();
            if (s.isEmpty()) {
                continue;
            }
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append(s);
        }
        return sb.toString();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return "";
    }
}
