package com.uniovi.rag.model;

/**
 * Represents a filter result with enhanced metadata.
 */
public class FilterResult {
    private final String minuteId;
    private final String date;
    private final String place;
    private final String summary;
    private final int score;

    public FilterResult(String minuteId, String date, String place, String summary, int score) {
        this.minuteId = minuteId;
        this.date = date;
        this.place = place;
        this.summary = summary;
        this.score = score;
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

    public String getSummary() {
        return summary;
    }

    public int getScore() {
        return score;
    }

    public String getIdentifier() {
        return String.format("%s (%s - %s)", minuteId, date != null ? date : "unknown", place != null ? place : "unknown");
    }

    @Override
    public String toString() {
        return String.format("FilterResult[%s, score=%d]", getIdentifier(), score);
    }
}
