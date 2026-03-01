package com.uniovi.rag.model;

/**
 * Represents statistical analysis results.
 */
public class ComparisonAnalysis {
    private final Double min;
    private final Double max;
    private final Double avg;

    public ComparisonAnalysis(Double min, Double max, Double avg) {
        this.min = min;
        this.max = max;
        this.avg = avg;
    }

    public Double getMin() {
        return min;
    }

    public Double getMax() {
        return max;
    }

    public Double getAvg() {
        return avg;
    }
}
