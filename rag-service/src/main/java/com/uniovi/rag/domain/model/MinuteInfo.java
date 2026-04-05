package com.uniovi.rag.domain.model;

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
        return date + ": " + attendees + " attendees, " + duration + " minutes, "
                + proposals + " proposals, " + agendaItems + " agenda items, "
                + questions + " any other business, location: " + location;
    }
}
