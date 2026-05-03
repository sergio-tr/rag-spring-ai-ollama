package com.uniovi.rag.domain.model;

/**
 * Represents a duration result with enhanced metadata.
 */
public class DurationResult {
    private final String minuteId;
    private final String date;
    private final String place;
    private final String startTime;
    private final String endTime;
    private final int durationMinutes;

    public DurationResult(String minuteId, String date, String place, String startTime, String endTime,
                          int durationMinutes) {
        this.minuteId = minuteId;
        this.date = date;
        this.place = place;
        this.startTime = startTime;
        this.endTime = endTime;
        this.durationMinutes = durationMinutes;
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

    public String getStartTime() {
        return startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getIdentifier() {
        return String.format("%s (%s - %s)", minuteId, date != null ? date : "unknown", place != null ? place : "unknown");
    }

    @Override
    public String toString() {
        return String.format("DurationResult[%s, %d min]", getIdentifier(), durationMinutes);
    }
}
