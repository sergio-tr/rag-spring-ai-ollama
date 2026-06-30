package com.uniovi.rag.application.service.runtime.query;

import com.uniovi.rag.domain.model.QueryType;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/** Deterministic slot defaults when structured rewrite omits required tool fields. */
public final class QueryPlanSlotEnricher {

    private QueryPlanSlotEnricher() {}

    public static Map<String, String> enrich(
            String normalizedText, Optional<QueryType> classifierQueryType, Map<String, String> rewriteSlots) {
        Map<String, String> slots = new LinkedHashMap<>(rewriteSlots == null ? Map.of() : rewriteSlots);
        if (classifierQueryType.filter(qt -> qt == QueryType.GET_FIELD).isEmpty()) {
            return Map.copyOf(slots);
        }
        String q = normalizedText == null ? "" : normalizedText.toLowerCase(Locale.ROOT);
        String existing = slots.get("field");
        if ((q.contains("cuántos") || q.contains("cuantos") || q.contains("cuántas") || q.contains("cuantas"))
                && (q.contains("participante")
                        || q.contains("asistente")
                        || q.contains("propietario")
                        || q.contains("personas")
                        || q.contains("asistieron"))) {
            slots.put("field", "attendeesCount");
            return Map.copyOf(slots);
        }
        if (existing != null && !existing.isBlank()) {
            return Map.copyOf(slots);
        }
        inferFieldSlot(normalizedText).ifPresent(field -> slots.put("field", field));
        return Map.copyOf(slots);
    }

    /** Infers metadata field name from Spanish/English acta phrasing. */
    public static Optional<String> inferFieldSlot(String normalizedText) {
        String q = normalizedText == null ? "" : normalizedText.toLowerCase(Locale.ROOT);
        if (q.contains("participantes")
                || q.contains("participante")
                || q.contains("asistentes")
                || q.contains("asistente")
                || q.contains("attendees")) {
            return Optional.of("attendees");
        }
        if (q.contains("presidente") || q.contains("presidió") || q.contains("presidio")) {
            return Optional.of("president");
        }
        if (q.contains("qué papel tuvo") || q.contains("que papel tuvo")) {
            return Optional.of("role");
        }
        if (q.contains("orden del día")
                || q.contains("orden del dia")
                || q.contains("puntos del orden")
                || q.contains("agenda")) {
            return Optional.of("agenda");
        }
        if (q.contains("secretario") || q.contains("secretaria")) {
            return Optional.of("secretary");
        }
        if (q.contains("hora")
                && (q.contains("empez") || q.contains("comenz") || q.contains("inicio"))
                && (q.contains("termin") || q.contains("finaliz"))) {
            return Optional.of("startEndTime");
        }
        if (q.contains("hora") && (q.contains("empez") || q.contains("comenz") || q.contains("inicio"))) {
            return Optional.of("startTime");
        }
        if (q.contains("hora") && (q.contains("termin") || q.contains("finaliz"))) {
            return Optional.of("endTime");
        }
        if (q.contains("duración") || q.contains("duracion") || q.contains("duration")) {
            return Optional.of("duration");
        }
        return Optional.empty();
    }
}
