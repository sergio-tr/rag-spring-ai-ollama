package com.uniovi.rag.domain.model;

/**
 * Represents a field result with enhanced metadata.
 */
public class FieldResult {
    private final String minuteId;
    private final String date;
    private final String place;
    private final String fieldName;
    private final String fieldValue;

    public FieldResult(String minuteId, String date, String place, String fieldName, String fieldValue) {
        this.minuteId = minuteId;
        this.date = date;
        this.place = place;
        this.fieldName = fieldName;
        this.fieldValue = fieldValue;
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

    public String getFieldName() {
        return fieldName;
    }

    public String getFieldValue() {
        return fieldValue;
    }

    public String getIdentifier() {
        return String.format("%s (%s - %s)", minuteId, date != null ? date : "unknown", place != null ? place : "unknown");
    }

    @Override
    public String toString() {
        return String.format("FieldResult[%s, %s=%s]", getIdentifier(), fieldName, fieldValue);
    }
}
