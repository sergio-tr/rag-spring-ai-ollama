package com.uniovi.rag.model;

import java.util.List;

/**
 * Represents a decision with enhanced metadata.
 */
public class Decision {
    private final String minuteId;
    private final String date;
    private final String place;
    private final String decisionText;
    private final String decisionType;
    private final List<String> keyEntities;
    private final long timestamp;

    public Decision(String minuteId, String date, String place, String decisionText, String decisionType,
                    List<String> keyEntities, long timestamp) {
        this.minuteId = minuteId;
        this.date = date;
        this.place = place;
        this.decisionText = decisionText;
        this.decisionType = decisionType;
        this.keyEntities = keyEntities;
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

    public String getDecisionText() {
        return decisionText;
    }

    public String getDecisionType() {
        return decisionType;
    }

    public List<String> getKeyEntities() {
        return keyEntities;
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

    public String getKeyEntitiesAsString() {
        return keyEntities.isEmpty() ? "none" : String.join(", ", keyEntities);
    }

    @Override
    public String toString() {
        return String.format("Decision[%s, type=%s, age=%dms, entities=%s]",
                getIdentifier(), decisionType, getAge(), getKeyEntitiesAsString());
    }
}
