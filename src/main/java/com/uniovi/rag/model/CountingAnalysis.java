package com.uniovi.rag.model;

import java.util.List;

/**
 * Represents comprehensive counting analysis results.
 */
public class CountingAnalysis {
    private final int totalCount;
    private final List<String> dates;
    private final List<String> places;
    private final List<String> topics;

    public CountingAnalysis(int totalCount, List<String> dates, List<String> places, List<String> topics) {
        this.totalCount = totalCount;
        this.dates = dates;
        this.places = places;
        this.topics = topics;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public List<String> getDates() {
        return dates;
    }

    public List<String> getPlaces() {
        return places;
    }

    public List<String> getTopics() {
        return topics;
    }
}
