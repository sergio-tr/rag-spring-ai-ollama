package com.uniovi.rag.application.service.runtime.retrieval;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Shared lexical/semantic entity matching for reranking and constraint validation. */
public final class RetrievalEntityMatchingSupport {

    private static final Map<String, List<String>> TOPIC_SYNONYMS =
            Map.of(
                    "videovigilancia",
                    List.of("camara", "camaras", "vigilancia", "seguridad"),
                    "calefaccion",
                    List.of("calefacción", "climatizacion", "climatización"),
                    "ascensor",
                    List.of("elevador", "lift"),
                    "convivencia",
                    List.of("normas de convivencia", "convivencia"),
                    "electrico",
                    List.of("eléctric", "electricidad", "instalacion electrica", "instalación eléctrica"),
                    "presupuesto",
                    List.of("cuentas", "estado de cuentas", "presupuesto anual"));

    private RetrievalEntityMatchingSupport() {}

    public static boolean containsEntityToken(String haystack, String token) {
        if (token == null || token.isBlank() || haystack == null || haystack.isBlank()) {
            return false;
        }
        String foldedHay = SpanishRetrievalTextSupport.foldAccents(haystack.toLowerCase(Locale.ROOT));
        String foldedToken = SpanishRetrievalTextSupport.foldAccents(token.toLowerCase(Locale.ROOT).trim());
        if (foldedHay.contains(foldedToken)) {
            return true;
        }
        for (String part : foldedToken.split("\\s+")) {
            if (part.length() >= 4 && foldedHay.contains(part)) {
                return true;
            }
        }
        for (String synonym : synonymsFor(foldedToken)) {
            String foldedSyn = SpanishRetrievalTextSupport.foldAccents(synonym.toLowerCase(Locale.ROOT));
            if (foldedHay.contains(foldedSyn)) {
                return true;
            }
        }
        return false;
    }

    public static boolean metadataContainsPerson(Map<String, Object> metadata, String personToken) {
        if (metadata == null || personToken == null || personToken.isBlank()) {
            return false;
        }
        for (String key : List.of("president", "secretary", "attendees", "namedPeople")) {
            Object value = metadata.get(key);
            if (value instanceof String s && containsEntityToken(s, personToken)) {
                return true;
            }
            if (value instanceof List<?> list) {
                for (Object item : list) {
                    if (item != null && containsEntityToken(item.toString(), personToken)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean metadataContainsDateToken(Map<String, Object> metadata, String dateToken) {
        if (metadata == null || dateToken == null || dateToken.isBlank()) {
            return false;
        }
        for (String key : List.of("date_iso", "actaDate", "date", "meetingDate", "documentDate")) {
            Object value = metadata.get(key);
            if (value != null && containsEntityToken(value.toString(), dateToken)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> synonymsFor(String foldedToken) {
        if (TOPIC_SYNONYMS.containsKey(foldedToken)) {
            return TOPIC_SYNONYMS.get(foldedToken);
        }
        for (Map.Entry<String, List<String>> entry : TOPIC_SYNONYMS.entrySet()) {
            if (foldedToken.contains(entry.getKey()) || entry.getKey().contains(foldedToken)) {
                return entry.getValue();
            }
        }
        return List.of();
    }

    public static Set<String> topicSynonymHeads() {
        return TOPIC_SYNONYMS.keySet();
    }
}
