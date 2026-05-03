package com.uniovi.rag.domain.model;

/**
 * Resultado de un resumen de acta: id, fecha, lugar y texto de resumen.
 */
public class SummaryResult {
    private final String minuteId;
    private final String date;
    private final String place;
    private final String summary;

    public SummaryResult(String minuteId, String date, String place, String summary) {
        this.minuteId = minuteId;
        this.date = date;
        this.place = place;
        this.summary = summary;
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
}
