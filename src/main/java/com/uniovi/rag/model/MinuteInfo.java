package com.uniovi.rag.model;

/**
 * Summary info for a single minute used in comparisons (e.g. attendees, duration, proposals).
 */
public record MinuteInfo(
        String date,
        int attendees,
        int duration,
        int proposals,
        int agendaItems,
        int questions,
        String location
) {
    @Override
    public String toString() {
        return date + ": " + attendees + " asistentes, " + duration + " minutos, "
                + proposals + " propuestas, " + agendaItems + " puntos del orden del día, "
                + questions + " ruegos/preguntas, lugar: " + location;
    }
}
