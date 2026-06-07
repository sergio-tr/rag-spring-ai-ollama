package com.uniovi.rag.application.service.evaluation.baseline;

import org.springframework.ai.document.Document;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Recall@k and MRR-style helpers for embedding retrieval benchmarks (chunk contains gold heuristic). */
public final class EmbeddingRetrievalMetrics {

    private EmbeddingRetrievalMetrics() {}

    public static boolean chunkContainsGold(Document doc, String expectedAnswer) {
        if (expectedAnswer == null || expectedAnswer.isBlank()) {
            return false;
        }
        String text = doc != null ? doc.getText() : null;
        if (text == null || text.isBlank()) {
            return false;
        }
        String needle = expectedAnswer.length() > 120 ? expectedAnswer.substring(0, 120).trim() : expectedAnswer.trim();
        return text.toLowerCase().contains(needle.toLowerCase());
    }

    public static double recallAt1(List<Document> docs, String expected) {
        if (docs == null || docs.isEmpty()) {
            return 0.0;
        }
        return chunkContainsGold(docs.get(0), expected) ? 1.0 : 0.0;
    }

    public static double recallAtK(List<Document> docs, String expected) {
        if (docs == null || docs.isEmpty()) {
            return 0.0;
        }
        return docs.stream().anyMatch(d -> chunkContainsGold(d, expected)) ? 1.0 : 0.0;
    }

    /** Recall@N: 1 if any of the first {@code n} documents contain gold, else 0. */
    public static double recallAtN(List<Document> docs, String expected, int n) {
        if (docs == null || docs.isEmpty() || n <= 0) {
            return 0.0;
        }
        int limit = Math.min(n, docs.size());
        for (int i = 0; i < limit; i++) {
            if (chunkContainsGold(docs.get(i), expected)) {
                return 1.0;
            }
        }
        return 0.0;
    }

    /** Reciprocal rank of first chunk containing gold (0 if none). */
    public static double mrr(List<Document> docs, String expected) {
        if (docs == null || docs.isEmpty()) {
            return 0.0;
        }
        for (int i = 0; i < docs.size(); i++) {
            if (chunkContainsGold(docs.get(i), expected)) {
                return 1.0 / (i + 1);
            }
        }
        return 0.0;
    }

    /** 1-based rank of first hit or empty if none. */
    public static int firstRelevantRank(List<Document> docs, String expected) {
        if (docs == null) {
            return 0;
        }
        for (int i = 0; i < docs.size(); i++) {
            if (chunkContainsGold(docs.get(i), expected)) {
                return i + 1;
            }
        }
        return 0;
    }

    // --- Gold ID-based retrieval metrics (canonical for embedding evaluation) ---

    public static double recallAtNByIds(List<String> retrievedIds, Set<String> goldIds, int n) {
        if (retrievedIds == null || retrievedIds.isEmpty() || goldIds == null || goldIds.isEmpty() || n <= 0) {
            return 0.0;
        }
        int limit = Math.min(n, retrievedIds.size());
        for (int i = 0; i < limit; i++) {
            if (containsNormalized(goldIds, retrievedIds.get(i))) {
                return 1.0;
            }
        }
        return 0.0;
    }

    public static double recallAtKByIds(List<String> retrievedIds, Set<String> goldIds) {
        if (retrievedIds == null || retrievedIds.isEmpty() || goldIds == null || goldIds.isEmpty()) {
            return 0.0;
        }
        return retrievedIds.stream().anyMatch(id -> containsNormalized(goldIds, id)) ? 1.0 : 0.0;
    }

    public static double mrrByIds(List<String> retrievedIds, Set<String> goldIds) {
        int rank = firstRelevantRankByIds(retrievedIds, goldIds);
        return rank > 0 ? 1.0 / rank : 0.0;
    }

    /** 1-based rank of first hit or 0 if none. */
    public static int firstRelevantRankByIds(List<String> retrievedIds, Set<String> goldIds) {
        if (retrievedIds == null || retrievedIds.isEmpty() || goldIds == null || goldIds.isEmpty()) {
            return 0;
        }
        for (int i = 0; i < retrievedIds.size(); i++) {
            if (containsNormalized(goldIds, retrievedIds.get(i))) {
                return i + 1;
            }
        }
        return 0;
    }

    private static boolean containsNormalized(Set<String> goldIds, String candidate) {
        if (candidate == null || candidate.isBlank() || goldIds == null || goldIds.isEmpty()) {
            return false;
        }
        String c = normalizeId(candidate);
        if (c.isEmpty()) {
            return false;
        }
        // Most gold sets are small; normalize on the fly (keeps caller contract simple).
        for (String g : goldIds) {
            if (normalizeId(g).equals(c)) {
                return true;
            }
        }
        return false;
    }

    public static String normalizeId(String raw) {
        if (raw == null) {
            return "";
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return "";
        }
        return t.toUpperCase(Locale.ROOT);
    }
}
