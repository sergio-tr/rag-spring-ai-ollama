package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.application.service.evaluation.BenchmarkResultRowKeys;
import java.util.LinkedHashMap;
import java.util.Map;

import java.util.List;

/** Promotes advanced retrieval telemetry onto preset benchmark metrics rows. */
public final class RagPresetAdvancedRetrievalMetrics {

    public static final String KEY_ADVANCED_RETRIEVAL_APPLIED = "advancedRetrievalApplied";
    public static final String KEY_ADVANCED_RETRIEVAL_STRATEGY = "advancedRetrievalStrategy";
    public static final String KEY_DENSE_CANDIDATE_COUNT = "denseCandidateCount";
    public static final String KEY_SPARSE_CANDIDATE_COUNT = "sparseCandidateCount";
    public static final String KEY_HYBRID_CANDIDATE_COUNT = "hybridCandidateCount";
    public static final String KEY_MERGED_CANDIDATE_COUNT = "mergedCandidateCount";
    public static final String KEY_DEDUPED_CANDIDATE_COUNT = "dedupedCandidateCount";
    public static final String KEY_RERANKED_CANDIDATE_COUNT = "rerankedCandidateCount";
    public static final String KEY_FINAL_CONTEXT_CHUNK_COUNT = "finalContextChunkCount";
    public static final String KEY_RERANK_APPLIED = "rerankApplied";
    public static final String KEY_RERANK_CHANGED_ORDER = "rerankChangedOrder";
    public static final String KEY_COMPRESSION_APPLIED = "compressionApplied";
    public static final String KEY_COMPRESSED_CONTEXT_CHAR_COUNT = "compressedContextCharCount";
    public static final String KEY_ADVANCED_RETRIEVAL_FALLBACK_REASON = "advancedRetrievalFallbackReason";
    public static final String KEY_CANDIDATE_ORIGINS = "candidateOrigins";
    public static final String KEY_SPARSE_RETRIEVAL_STATUS = "sparseRetrievalStatus";
    public static final String KEY_HYBRID_APPLIED = "hybridApplied";
    public static final String KEY_RETRIEVAL_ROUTE = "retrievalRoute";
    public static final String KEY_RETRIEVAL_MODE = "retrievalMode";
    public static final String KEY_RERANK_NOOP_REASON = "rerankNoopReason";
    public static final String KEY_ORIGINAL_CONTEXT_CHAR_COUNT = "originalContextCharCount";
    public static final String KEY_SPARSE_QUERY_REWRITTEN = "sparseQueryRewritten";
    public static final String KEY_SPARSE_FALLBACK_STAGE = "sparseFallbackStage";
    public static final String KEY_SPARSE_HIT = "sparseHit";
    public static final String KEY_FUSION_STRATEGY = "fusionStrategy";
    public static final String KEY_PRE_FUSION_COUNT = "preFusionCount";
    public static final String KEY_POST_FUSION_COUNT = "postFusionCount";
    public static final String KEY_METADATA_CANDIDATE_COUNT = "metadataCandidateCount";
    public static final String KEY_METADATA_FILTER_APPLIED = "metadataFilterApplied";
    public static final String KEY_METADATA_FILTER_FALLBACK = "metadataFilterFallback";

    private RagPresetAdvancedRetrievalMetrics() {}

    public static void computeAndMerge(Map<String, Object> metrics) {
        if (metrics == null) {
            return;
        }
        metrics.putAll(compute(metrics));
    }

    public static Map<String, Object> compute(Map<String, Object> mp) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> source = mp != null ? mp : Map.of();

        boolean useRetrieval = bool(source.get("useRetrieval"));
        String materialization = str(source.get("materializationStrategy"));
        boolean hybridPreset = "HYBRID".equalsIgnoreCase(materialization);
        boolean executed = isExecuted(source);
        boolean runtimeRetrievalTelemetry = hasRuntimeRetrievalTelemetry(source);
        boolean advancedApplied = useRetrieval && hybridPreset && executed && runtimeRetrievalTelemetry;
        out.put(KEY_ADVANCED_RETRIEVAL_APPLIED, advancedApplied);

        String retrievalMode = str(source.get("retrievalMode"));
        int dense = intVal(firstPresent(source, "denseCandidateCount", "retrievalDenseCandidateCount"));
        int sparse = intVal(firstPresent(source, "sparseCandidateCount", "retrievalSparseCandidateCount"));
        int merged = intVal(firstPresent(source, "mergedCandidateCount", "retrievalAfterFusionCount"));
        int deduped = intVal(firstPresent(source, "dedupedCandidateCount", "retrievalDedupedCandidateCount"));
        int reranked = intVal(firstPresent(source, "rerankedCandidateCount", "retrievalAfterRerankCount"));
        int finalChunks = intVal(firstPresent(source, "finalContextChunkCount", "contextChunkCount"));
        int compressedChars = intVal(firstPresent(source, "compressedContextCharCount", "retrievalCompressionCharsAfter"));
        int originalChars =
                intVal(firstPresent(source, KEY_ORIGINAL_CONTEXT_CHAR_COUNT, "retrievalCompressionCharsBefore"));
        int promptChars = intVal(source.get("promptContextCharCount"));

        boolean rerankApplied = bool(firstPresent(source, "rerankApplied", "retrievalRerankApplied"));
        boolean rerankChanged = bool(firstPresent(source, "rerankChangedOrder", "retrievalRerankOrderChanged"));
        boolean compressionApplied = bool(firstPresent(source, "compressionApplied", "retrievalCompressionApplied"));
        String rerankNoopReason = str(source.get(KEY_RERANK_NOOP_REASON));
        if (rerankNoopReason.isBlank() && rerankApplied && !rerankChanged) {
            rerankNoopReason = "order_unchanged";
        }

        String sparseStatus = deriveSparseStatus(source, retrievalMode, sparse, hybridPreset);
        boolean hybridApplied;
        if (source.containsKey(KEY_HYBRID_APPLIED)) {
            hybridApplied = bool(source.get(KEY_HYBRID_APPLIED));
        } else {
            hybridApplied = hybridPreset && sparse > 0 && merged > 0;
        }
        String strategy =
                deriveStrategy(
                        hybridPreset, executed, retrievalMode, hybridApplied, rerankApplied, compressionApplied);
        String fallback =
                deriveFallbackReason(
                        advancedApplied,
                        hybridPreset,
                        executed,
                        sparseStatus,
                        hybridApplied,
                        dense,
                        sparse);

        out.put(KEY_ADVANCED_RETRIEVAL_STRATEGY, strategy);
        if (dense >= 0) {
            out.put(KEY_DENSE_CANDIDATE_COUNT, dense);
        }
        if (sparse >= 0) {
            out.put(KEY_SPARSE_CANDIDATE_COUNT, sparse);
        }
        int hybridCandidateCount = dense >= 0 && sparse >= 0 ? dense + sparse : -1;
        if (hybridCandidateCount >= 0) {
            out.put(KEY_HYBRID_CANDIDATE_COUNT, hybridCandidateCount);
        }
        if (merged >= 0) {
            out.put(KEY_MERGED_CANDIDATE_COUNT, merged);
        }
        if (deduped >= 0) {
            out.put(KEY_DEDUPED_CANDIDATE_COUNT, deduped);
        } else if (merged >= 0) {
            out.put(KEY_DEDUPED_CANDIDATE_COUNT, merged);
        }
        if (reranked >= 0) {
            out.put(KEY_RERANKED_CANDIDATE_COUNT, reranked);
        }
        if (finalChunks >= 0) {
            out.put(KEY_FINAL_CONTEXT_CHUNK_COUNT, finalChunks);
        }
        out.put(KEY_RERANK_APPLIED, rerankApplied);
        out.put(KEY_RERANK_CHANGED_ORDER, rerankChanged);
        if (!rerankNoopReason.isBlank()) {
            out.put(KEY_RERANK_NOOP_REASON, rerankNoopReason);
        }
        out.put(KEY_COMPRESSION_APPLIED, compressionApplied);
        if (compressedChars >= 0) {
            out.put(KEY_COMPRESSED_CONTEXT_CHAR_COUNT, compressedChars);
        }
        if (originalChars >= 0) {
            out.put(KEY_ORIGINAL_CONTEXT_CHAR_COUNT, originalChars);
        }
        if (promptChars >= 0) {
            out.put("promptContextCharCount", promptChars);
        }
        if (!sparseStatus.isBlank()) {
            out.put(KEY_SPARSE_RETRIEVAL_STATUS, sparseStatus);
        }
        out.put(KEY_HYBRID_APPLIED, hybridApplied);
        if (!fallback.isBlank()) {
            out.put(KEY_ADVANCED_RETRIEVAL_FALLBACK_REASON, fallback);
        }
        String origins = str(source.get(KEY_CANDIDATE_ORIGINS));
        if (origins.isBlank()) {
            origins = candidateOrigins(dense, sparse, merged);
        }
        if (!origins.isBlank()) {
            out.put(KEY_CANDIDATE_ORIGINS, origins);
        }

        copyString(out, source, KEY_SPARSE_QUERY_REWRITTEN);
        copyString(out, source, KEY_SPARSE_FALLBACK_STAGE);
        copyString(out, source, KEY_FUSION_STRATEGY);
        copyIfPresent(out, source, KEY_SPARSE_HIT);
        copyIfPresent(out, source, KEY_PRE_FUSION_COUNT);
        copyIfPresent(out, source, KEY_POST_FUSION_COUNT);
        copyIfPresent(out, source, KEY_METADATA_CANDIDATE_COUNT);
        copyIfPresent(out, source, KEY_METADATA_FILTER_APPLIED);
        copyIfPresent(out, source, KEY_METADATA_FILTER_FALLBACK);

        copyString(out, source, "workflowName");
        copyString(out, source, KEY_RETRIEVAL_ROUTE);
        copyString(out, source, KEY_RETRIEVAL_MODE);
        copyString(out, source, "materializationStrategy");
        copyList(out, source, "retrieved_chunk_ids", "retrievedChunkIds");
        copyList(out, source, "retrieved_document_ids", "retrievedDocumentIds");
        copyIfPresent(out, source, "sourceCount");

        return out;
    }

    private static boolean isExecuted(Map<String, Object> mp) {
        String outcome =
                firstNonBlank(
                        str(mp.get(BenchmarkResultRowKeys.ITEM_OUTCOME)),
                        str(mp.get("outcome")),
                        str(mp.get("itemOutcome")));
        return "EXECUTED".equalsIgnoreCase(outcome);
    }

    private static boolean hasRuntimeRetrievalTelemetry(Map<String, Object> mp) {
        if (!str(mp.get("retrievalMode")).isBlank()) {
            return true;
        }
        for (String key :
                new String[] {
                    "denseCandidateCount",
                    "retrievalDenseCandidateCount",
                    "mergedCandidateCount",
                    "retrievalAfterFusionCount",
                    "sparseCandidateCount",
                    "retrievalSparseCandidateCount"
                }) {
            if (mp.containsKey(key) && mp.get(key) != null) {
                return true;
            }
        }
        return false;
    }

    private static String deriveStrategy(
            boolean hybridPreset,
            boolean executed,
            String retrievalMode,
            boolean hybridApplied,
            boolean rerankApplied,
            boolean compressionApplied) {
        if (!hybridPreset) {
            return "DENSE_ONLY";
        }
        if (!executed) {
            return "HYBRID_NOT_EXECUTED";
        }
        if ("HYBRID_DENSE_SPARSE".equalsIgnoreCase(retrievalMode) && hybridApplied) {
            StringBuilder sb = new StringBuilder("HYBRID_DENSE_SPARSE_RRF");
            if (rerankApplied) {
                sb.append("_RERANK");
            }
            if (compressionApplied) {
                sb.append("_COMPRESS");
            }
            return sb.toString();
        }
        if ("HYBRID_DENSE_SPARSE".equalsIgnoreCase(retrievalMode)) {
            return "HYBRID_DENSE_FALLBACK";
        }
        return "DENSE_ONLY";
    }

    private static String deriveSparseStatus(
            Map<String, Object> mp, String retrievalMode, int sparse, boolean hybridPreset) {
        String explicit = str(mp.get(KEY_SPARSE_RETRIEVAL_STATUS));
        if (!explicit.isBlank()) {
            return explicit;
        }
        if (!hybridPreset) {
            return "NOT_APPLICABLE";
        }
        if ("sparse_unavailable".equalsIgnoreCase(str(mp.get("retrievalSparseStatus")))) {
            return "UNAVAILABLE";
        }
        if (sparse > 0) {
            return "OK";
        }
        if ("HYBRID_DENSE_SPARSE".equalsIgnoreCase(retrievalMode) || hybridPreset) {
            return sparse == 0 ? "ZERO_MATCHES" : "NOT_APPLICABLE";
        }
        return "NOT_APPLICABLE";
    }

    private static String deriveFallbackReason(
            boolean advancedApplied,
            boolean hybridPreset,
            boolean executed,
            String sparseStatus,
            boolean hybridApplied,
            int dense,
            int sparse) {
        if (!hybridPreset) {
            return "preset_not_hybrid";
        }
        if (!executed) {
            return "";
        }
        if (!advancedApplied) {
            return "";
        }
        if ("UNAVAILABLE".equals(sparseStatus)) {
            return "sparse_unavailable";
        }
        if (!hybridApplied && hybridPreset && dense > 0) {
            if ("ZERO_MATCHES".equals(sparseStatus)) {
                return "sparse_zero_matches";
            }
            return "hybrid_not_applied";
        }
        if (dense == 0 && sparse == 0) {
            return "retrieval_empty_result";
        }
        return "";
    }

    private static String candidateOrigins(int dense, int sparse, int merged) {
        if (dense < 0 && sparse < 0 && merged < 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        if (dense >= 0) {
            sb.append("dense=").append(dense);
        }
        if (sparse >= 0) {
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append("sparse=").append(sparse);
        }
        if (merged >= 0) {
            if (!sb.isEmpty()) {
                sb.append(';');
            }
            sb.append("fused=").append(merged);
        }
        return sb.toString();
    }

    private static Object firstPresent(Map<String, Object> mp, String... keys) {
        for (String key : keys) {
            if (mp.containsKey(key) && mp.get(key) != null) {
                return mp.get(key);
            }
        }
        return null;
    }

    private static void copyIfPresent(Map<String, Object> out, Map<String, Object> mp, String key) {
        if (mp.containsKey(key) && mp.get(key) != null) {
            out.put(key, mp.get(key));
        }
    }

    private static void copyString(Map<String, Object> out, Map<String, Object> mp, String key) {
        String v = str(mp.get(key));
        if (!v.isBlank()) {
            out.put(key, v);
        }
    }

    @SuppressWarnings("unchecked")
    private static void copyList(Map<String, Object> out, Map<String, Object> mp, String sourceKey, String targetKey) {
        Object raw = mp.get(sourceKey);
        if (raw == null) {
            raw = mp.get(targetKey);
        }
        if (raw instanceof List<?> list && !list.isEmpty()) {
            out.put(targetKey, List.copyOf(list));
        }
    }

    private static boolean bool(Object raw) {
        if (raw instanceof Boolean b) {
            return b;
        }
        if (raw instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        if (raw instanceof Number n) {
            return n.doubleValue() != 0.0;
        }
        return false;
    }

    private static int intVal(Object raw) {
        if (raw == null) {
            return -1;
        }
        if (raw instanceof Number n) {
            return n.intValue();
        }
        if (raw instanceof String s && !s.isBlank()) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException ignored) {
                return -1;
            }
        }
        return -1;
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}
