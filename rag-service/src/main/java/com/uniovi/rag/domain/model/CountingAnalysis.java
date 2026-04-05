package com.uniovi.rag.domain.model;

import java.util.List;

/**
 * Represents comprehensive counting analysis results.
 */
public class CountingAnalysis {
    private final int totalCount;
    private final List<String> dates;
    private final List<String> places;
    private final List<String> topics;
    private final List<Integer> attendeesCounts;

    public CountingAnalysis(int totalCount, List<String> dates, List<String> places, List<String> topics) {
        this(totalCount, dates, places, topics, null);
    }

    public CountingAnalysis(int totalCount, List<String> dates, List<String> places, List<String> topics, List<Integer> attendeesCounts) {
        this.totalCount = totalCount;
        this.dates = dates;
        this.places = places;
        this.topics = topics;
        this.attendeesCounts = attendeesCounts;
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

    public List<Integer> getAttendeesCounts() {
        return attendeesCounts;
    }
}
