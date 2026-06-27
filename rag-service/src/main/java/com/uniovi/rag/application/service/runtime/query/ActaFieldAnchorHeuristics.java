package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.runtime.query.EntityExtractionResult;
import com.uniovi.rag.domain.runtime.query.QueryPlan;
import java.util.Locale;

/** Shared heuristics for acta-scoped field queries that require a meeting/date anchor (FD-CL-01). */
public final class ActaFieldAnchorHeuristics {

    private ActaFieldAnchorHeuristics() {}

    public static boolean needsActaAnchor(String normalizedText, EntityExtractionResult entities) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return false;
        }
        String q = normalizedText.toLowerCase(Locale.ROOT);
        if (isCompoundMonthTopicAttendeeFilter(q)) {
            return false;
        }
        if (isCorpusWideExactAttendeeCountListing(q)) {
            return false;
        }
        boolean hasTemporal =
                (entities != null && !entities.dates().isEmpty())
                        || hasExplicitDateInText(q)
                        || q.contains("last")
                        || q.contains("últim")
                        || q.contains("next")
                        || q.contains("próxim");
        return asksActaScopedFieldWithoutAnchor(q, hasTemporal);
    }

    public static boolean needsActaAnchor(QueryPlan plan) {
        if (plan == null) {
            return false;
        }
        return needsActaAnchor(plan.normalizedQueryText(), plan.entityExtractionResult());
    }

    public static boolean isUndatedParticipantCount(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return false;
        }
        String q = normalizedText.toLowerCase(Locale.ROOT);
        boolean participants =
                q.contains("participantes")
                        || q.contains("asistieron")
                        || q.contains("asistentes")
                        || q.contains("asistió")
                        || q.contains("asistio");
        boolean countParticipants =
                (q.contains("cuántos") || q.contains("cuantos") || q.contains("cuántas") || q.contains("cuantas"))
                        && participants;
        return countParticipants && !hasExplicitDateInText(q) && !isCorpusWideAggregate(q);
    }

    public static boolean hasExplicitDateInPlan(QueryPlan plan) {
        if (plan == null) {
            return false;
        }
        if (plan.entityExtractionResult() != null && !plan.entityExtractionResult().dates().isEmpty()) {
            for (String iso : plan.entityExtractionResult().dates()) {
                if (iso != null && !iso.isBlank() && !iso.matches("\\d{4}-01-01")) {
                    return true;
                }
            }
        }
        String query =
                ((plan.rewrittenQueryText() == null ? "" : plan.rewrittenQueryText())
                                + " "
                                + (plan.normalizedQueryText() == null ? "" : plan.normalizedQueryText()))
                        .toLowerCase(Locale.ROOT);
        return hasExplicitDateInText(query);
    }

    public static boolean isAttendeeScopedField(String field) {
        if (field == null || field.isBlank()) {
            return false;
        }
        String f = field.toLowerCase(Locale.ROOT);
        return f.equals("attendees")
                || f.equals("attendeescount")
                || f.equals("numberofattendees")
                || f.equals("participants");
    }

    private static boolean asksActaScopedFieldWithoutAnchor(String q, boolean hasTemporal) {
        if (hasTemporal || isCorpusWideAggregate(q)) {
            return false;
        }
        boolean participants =
                q.contains("participantes")
                        || q.contains("asistieron")
                        || q.contains("asistentes")
                        || q.contains("asistió")
                        || q.contains("asistio");
        boolean countParticipants =
                (q.contains("cuántos") || q.contains("cuantos") || q.contains("cuántas") || q.contains("cuantas"))
                        && participants;
        boolean actaMeetingField =
                participants
                        || q.contains("presidente")
                        || q.contains("secretari")
                        || q.contains("duración")
                        || q.contains("duracion")
                        || q.contains("orden del día")
                        || q.contains("orden del dia")
                        || ((q.contains("acta") || q.contains("reunión") || q.contains("reunion"))
                                && !q.contains("reuniones"));
        return countParticipants || (actaMeetingField && (participants || q.contains("presidente")));
    }

    /** FD-CE-02: corpus-wide exact attendee listing — not acta-scoped clarification. */
    public static boolean isCorpusWideExactAttendeeCountListing(String normalizedText) {
        if (normalizedText == null || normalizedText.isBlank()) {
            return false;
        }
        String q = normalizedText.toLowerCase(Locale.ROOT);
        return (q.contains("en qué reuniones") || q.contains("en que reuniones"))
                && q.contains("asistentes")
                && (q.contains("exactamente") || q.matches(".*\\bcon\\s+\\d+\\s+asistentes\\b.*"));
    }

    static boolean isCorpusWideAggregate(String q) {
        return q.contains("todas las actas")
                || q.contains("cada acta")
                || q.contains("cuántas actas")
                || q.contains("cuantas actas")
                || q.contains("hay actas")
                || q.contains("todas las reuniones")
                || q.contains("cuántas reuniones")
                || q.contains("cuantas reuniones")
                || isCompoundMonthTopicAttendeeFilter(q)
                || isCorpusWideExactAttendeeCountListing(q);
    }

    /** FD-FL-03: month + topic + attendee threshold listing — corpus-wide filter, not acta-scoped clarification. */
    public static boolean isCompoundMonthTopicAttendeeFilter(String q) {
        if (q == null || q.isBlank()) {
            return false;
        }
        boolean month = hasMonthNameInText(q);
        boolean topic =
                q.contains("videovigilancia")
                        || q.contains("vigilancia")
                        || q.contains("cámaras")
                        || q.contains("camaras");
        boolean attendeeThreshold =
                q.contains("asistentes")
                        && (q.contains("más de") || q.contains("mas de") || q.contains("more than"));
        boolean reunionListing =
                q.contains("reuniones")
                        || q.contains("qué reuniones")
                        || q.contains("que reuniones")
                        || q.contains("celebradas");
        return month && topic && attendeeThreshold && reunionListing;
    }

    public static boolean hasExplicitDateInText(String q) {
        return q.matches(".*\\b\\d{4}-\\d{2}-\\d{2}\\b.*")
                || q.matches(".*\\b\\d{1,2}[/-]\\d{1,2}[/-]\\d{4}\\b.*")
                || q.matches(".*\\b\\d{1,2}\\s+de\\s+\\p{L}+\\s+de\\s+\\d{4}\\b.*")
                || q.matches(".*\\baño\\s+(del\\s+)?\\d{4}\\b.*")
                || q.matches(".*\\bdel\\s+año\\s+\\d{4}\\b.*")
                || q.matches(".*\\ben\\s+20\\d{2}\\b.*")
                || hasMonthNameInText(q);
    }

    static boolean hasMonthNameInText(String q) {
        if (q == null || q.isBlank()) {
            return false;
        }
        return q.contains("enero")
                || q.contains("febrero")
                || q.contains("marzo")
                || q.contains("abril")
                || q.contains("mayo")
                || q.contains("junio")
                || q.contains("julio")
                || q.contains("agosto")
                || q.contains("septiembre")
                || q.contains("octubre")
                || q.contains("noviembre")
                || q.contains("diciembre");
    }
}
