package com.uniovi.rag.application.result.evaluation;

/**
 * Aggregated retrieval-side metrics from a judge-evaluated benchmark batch.
 */
public record RetrievalSummaryMetrics(
        Double meanContextSufficiency,
        Double precisionAtK,
        Double recallAtK,
        Double mrr) {}
