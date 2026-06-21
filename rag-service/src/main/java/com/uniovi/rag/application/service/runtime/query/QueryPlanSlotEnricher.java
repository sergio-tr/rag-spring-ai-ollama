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
        String existing = slots.get("field");
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
        if (q.contains("orden del día")
                || q.contains("orden del dia")
                || q.contains("puntos del orden")
                || q.contains("agenda")) {
            return Optional.of("agenda");
        }
        if (q.contains("secretario") || q.contains("secretaria")) {
            return Optional.of("secretary");
        }
        if (q.contains("duración") || q.contains("duracion") || q.contains("duration")) {
            return Optional.of("duration");
        }
        return Optional.empty();
    }
}
