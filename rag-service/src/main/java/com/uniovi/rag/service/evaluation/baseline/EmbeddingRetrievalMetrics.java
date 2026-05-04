package com.uniovi.rag.service.evaluation.baseline;

import org.springframework.ai.document.Document;

import java.util.List;

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
}
