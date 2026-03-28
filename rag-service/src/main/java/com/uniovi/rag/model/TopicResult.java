package com.uniovi.rag.model;

/**
 * Represents a topic result with enhanced metadata
 */
public class TopicResult {
    private final String minuteId;
    private final String date;
    private final String place;
    private final String topicSummary;

    public TopicResult(String minuteId, String date, String place, String topicSummary) {
        this.minuteId = minuteId;
        this.date = date;
        this.place = place;
        this.topicSummary = topicSummary;
    }

    public String getMinuteId() {
        return minuteId;
    }

    public String getDate() {
        return date;
    }

    public String getPlace() {
        return place;
    }

    public String getTopicSummary() {
        return topicSummary;
    }

    public String getIdentifier() {
        return String.format("%s (%s - %s)", minuteId, date != null ? date : "unknown", place != null ? place : "unknown");
    }

    @Override
    public String toString() {
        return String.format("TopicResult[%s]", getIdentifier());
    }
}
