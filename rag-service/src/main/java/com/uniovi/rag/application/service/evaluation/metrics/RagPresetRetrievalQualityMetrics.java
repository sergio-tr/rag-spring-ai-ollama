package com.uniovi.rag.application.service.evaluation.metrics;

import com.uniovi.rag.application.service.evaluation.baseline.EmbeddingRetrievalMetrics;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Gold-label retrieval quality for preset runs (symbol or id overlap matching). */
public final class RagPresetRetrievalQualityMetrics {

    private RagPresetRetrievalQualityMetrics() {}

    public static Map<String, Object> compute(Map<String, Object> metrics) {
        Map<String, Object> out = new LinkedHashMap<>();
        List<String> goldDocs = mergeGold(DatasetMetricContract.readStringList(metrics, DatasetMetricContract.KEY_GOLD_DOCUMENT_IDS));
        goldDocs = mergeLists(goldDocs, DatasetMetricContract.readStringList(metrics, "gold_document_ids"));
        List<String> goldChunks = mergeGold(DatasetMetricContract.readStringList(metrics, DatasetMetricContract.KEY_GOLD_CHUNK_IDS));
        goldChunks = mergeLists(goldChunks, DatasetMetricContract.readStringList(metrics, "gold_chunk_ids"));

        if (goldDocs.isEmpty() && goldChunks.isEmpty()) {
            out.put("retrievalQualityStatus", RetrievalQualityStatus.NOT_AVAILABLE.name());
            return out;
        }

        List<String> retrievedDocs = readIdList(metrics, "retrieved_document_ids", "retrievedDocumentIds");
        List<String> retrievedChunks = readIdList(metrics, "retrieved_chunk_ids", "retrievedChunkIds");

        Set<String> goldDocSet = normalizeGoldSet(goldDocs);
        Set<String> goldChunkSet = normalizeGoldSet(goldChunks);
        List<String> ranked =
                !retrievedChunks.isEmpty()
                        ? retrievedChunks
                        : !retrievedDocs.isEmpty() ? retrievedDocs : List.of();

        if (ranked.isEmpty()) {
            out.put("retrievalQualityStatus", RetrievalQualityStatus.NOT_AVAILABLE.name());
            return out;
        }

        Set<String> goldUnion = new LinkedHashSet<>();
        goldUnion.addAll(goldDocSet);
        goldUnion.addAll(goldChunkSet);

        double recall1 = recallAtNByOverlap(ranked, goldUnion, goldDocSet, goldChunkSet, 1);
        double recall3 = recallAtNByOverlap(ranked, goldUnion, goldDocSet, goldChunkSet, 3);
        double recall5 = recallAtNByOverlap(ranked, goldUnion, goldDocSet, goldChunkSet, 5);
        double mrr = mrrByOverlap(ranked, goldUnion, goldDocSet, goldChunkSet);
        double ndcg5 = ndcgAtK(ranked, goldUnion, goldDocSet, goldChunkSet, 5);

        out.put("retrievalQualityStatus", RetrievalQualityStatus.COMPUTED.name());
        out.put("recallAt1", recall1);
        out.put("recallAt3", recall3);
        out.put("recallAt5", recall5);
        out.put("recallAtK", recall5);
        out.put("mrr", mrr);
        out.put("ndcgAt5", ndcg5);
        out.put("goldDocumentFound", recall1 > 0 || mrr > 0);
        return out;
    }

    private static List<String> mergeGold(List<String> ids) {
        return DatasetMetricContract.sanitizeGoldIds(ids);
    }

    private static List<String> mergeLists(List<String> a, List<String> b) {
        if (b == null || b.isEmpty()) {
            return a;
        }
        List<String> out = new ArrayList<>(a);
        for (String id : b) {
            if (!out.contains(id)) {
                out.add(id);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static List<String> readIdList(Map<String, Object> metrics, String... keys) {
        for (String key : keys) {
            Object raw = metrics.get(key);
            if (raw instanceof List<?> list && !list.isEmpty()) {
                List<String> out = new ArrayList<>();
                for (Object o : list) {
                    if (o != null) {
                        String s = String.valueOf(o).trim();
                        if (!s.isEmpty()) {
                            out.add(s);
                        }
                    }
                }
                return out;
            }
        }
        return List.of();
    }

    private static Set<String> normalizeGoldSet(List<String> gold) {
        Set<String> out = new LinkedHashSet<>();
        for (String g : gold) {
            out.add(EmbeddingRetrievalMetrics.normalizeId(g));
        }
        return out;
    }

    private static boolean overlapsGold(
            String retrieved, Set<String> goldUnion, Set<String> goldDocs, Set<String> goldChunks) {
        if (retrieved == null || retrieved.isBlank()) {
            return false;
        }
        String norm = EmbeddingRetrievalMetrics.normalizeId(retrieved);
        if (goldUnion.contains(norm)) {
            return true;
        }
        String lower = retrieved.toLowerCase(Locale.ROOT);
        for (String g : goldDocs) {
            if (!g.isBlank() && lower.contains(g.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        for (String g : goldChunks) {
            if (!g.isBlank() && lower.contains(g.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return EmbeddingRetrievalMetrics.recallAtNByIds(List.of(retrieved), goldUnion, 1) > 0;
    }

    private static double recallAtNByOverlap(
            List<String> ranked,
            Set<String> goldUnion,
            Set<String> goldDocs,
            Set<String> goldChunks,
            int n) {
        if (ranked.isEmpty() || goldUnion.isEmpty()) {
            return 0.0;
        }
        int limit = Math.min(n, ranked.size());
        for (int i = 0; i < limit; i++) {
            if (overlapsGold(ranked.get(i), goldUnion, goldDocs, goldChunks)) {
                return 1.0;
            }
        }
        return 0.0;
    }

    private static double mrrByOverlap(
            List<String> ranked, Set<String> goldUnion, Set<String> goldDocs, Set<String> goldChunks) {
        for (int i = 0; i < ranked.size(); i++) {
            if (overlapsGold(ranked.get(i), goldUnion, goldDocs, goldChunks)) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    private static double ndcgAtK(
            List<String> ranked,
            Set<String> goldUnion,
            Set<String> goldDocs,
            Set<String> goldChunks,
            int k) {
        if (ranked.isEmpty() || goldUnion.isEmpty()) {
            return 0.0;
        }
        int limit = Math.min(k, ranked.size());
        double dcg = 0.0;
        for (int i = 0; i < limit; i++) {
            if (overlapsGold(ranked.get(i), goldUnion, goldDocs, goldChunks)) {
                dcg += 1.0 / (Math.log(i + 2) / Math.log(2));
                break;
            }
        }
        double idcg = 1.0;
        return idcg > 0 ? dcg / idcg : 0.0;
    }
}
