package com.uniovi.rag.domain.model;

/**
 * Represents an explanation with metadata.
 */
public class Explanation {
    private final String minuteId;
    private final String date;
    private final String place;
    private final String content;
    private final double relevanceScore;
    private final long timestamp;

    public Explanation(String minuteId, String date, String place, String content, double relevanceScore, long timestamp) {
        this.minuteId = minuteId;
        this.date = date;
        this.place = place;
        this.content = content;
        this.relevanceScore = relevanceScore;
        this.timestamp = timestamp;
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

    public String getContent() {
        return content;
    }

    public double getRelevanceScore() {
        return relevanceScore;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getIdentifier() {
        return String.format("%s (%s - %s)", minuteId, date != null ? date : "unknown", place != null ? place : "unknown");
    }

    public long getAge() {
        return System.currentTimeMillis() - timestamp;
    }

    @Override
    public String toString() {
        return String.format("Explanation[%s, score=%.2f, age=%dms]", getIdentifier(), relevanceScore, getAge());
    }
}
