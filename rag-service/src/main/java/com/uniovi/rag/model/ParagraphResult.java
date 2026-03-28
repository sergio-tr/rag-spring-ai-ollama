package com.uniovi.rag.model;

/**
 * Represents a paragraph result with enhanced metadata
 */
public class ParagraphResult {
    private final String minuteId;
    private final String date;
    private final String place;
    private final String paragraph;
    private final int score;

    public ParagraphResult(String minuteId, String date, String place, String paragraph, int score) {
        this.minuteId = minuteId;
        this.date = date;
        this.place = place;
        this.paragraph = paragraph;
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

    public String getParagraph() {
        return paragraph;
    }

    public int getScore() {
        return score;
    }

    public String getIdentifier() {
        return String.format("%s (%s - %s)", minuteId, date != null ? date : "unknown", place != null ? place : "unknown");
    }

    @Override
    public String toString() {
        return String.format("ParagraphResult[%s, score=%d]", getIdentifier(), score);
    }
}
