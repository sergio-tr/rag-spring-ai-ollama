package com.uniovi.rag.domain.model;

import java.util.List;

/**
 * Represents statistical analysis of durations.
 */
public class DurationAnalysis {
    private final int minDuration;
    private final int maxDuration;
    private final double averageDuration;
    private final List<Integer> allDurations;

    public DurationAnalysis(int minDuration, int maxDuration, double averageDuration, List<Integer> allDurations) {
        this.minDuration = minDuration;
        this.maxDuration = maxDuration;
        this.averageDuration = averageDuration;
        this.allDurations = allDurations;
    }

    public int getMinDuration() {
        return minDuration;
    }

    public int getMaxDuration() {
        return maxDuration;
    }

    public double getAverageDuration() {
        return averageDuration;
    }

    public List<Integer> getAllDurations() {
        return allDurations;
    }
}
