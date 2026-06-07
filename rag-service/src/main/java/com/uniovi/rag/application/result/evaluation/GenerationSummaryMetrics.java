package com.uniovi.rag.application.result.evaluation;

/**
 * Aggregated generation-side metrics from a judge-evaluated benchmark batch.
 */
public record GenerationSummaryMetrics(
        Double meanCorrectness,
        Double meanContextSufficiency,
        Double meanRelevance,
        Double meanIndependence,
        Double meanGroundedness,
        Double pctCorrectnessGe4,
        Integer nParsed,
        Double bleu,
        Double rougeL,
        Double meteor) {}
