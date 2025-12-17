package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.AbstractTool;
import com.uniovi.rag.services.tools.EnhancedNERHandler;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.cache.annotation.Cacheable;
import com.uniovi.rag.model.Minute;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Arrays;

import static com.uniovi.rag.utils.InfoExtractor.containsAnyKeyword;

public abstract class AbstractMetadataTool extends AbstractTool {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final ObjectMapper objectMapper = new ObjectMapper();
    protected final EnhancedNERHandler nerHandler;

    public AbstractMetadataTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
        this.nerHandler = new EnhancedNERHandler(chatClient);
    }

    protected boolean matchesBooleanCondition(Document doc, String query, JSONObject nerEntities) {
        // First try semantic matching with the full query
        if (semanticallyMatchesMetadata(doc, query)) {
            return true;
        }

        // If NER entities are available, try to use them for more specific matching
        if (nerEntities != null && nerEntities.has("entities")) {
            JSONObject entidades = nerEntities.getJSONObject("entities");
            String[] terms = extractTermsFromNER(entidades);
            if (terms.length > 0) {
                String prompt = String.format("""
                    You are a metadata matching system. Analyze if document metadata semantically matches specific terms.
                    
                    Terms from query (may be in any language):
                    %s
                    
                    Document metadata (values may be in any language):
                    %s
                    
                    Task: Determine if the metadata semantically matches these terms.
                    Consider semantic meaning, not just exact word matches.
                    
                    Respond with ONLY one word: YES or NO.
                    Do not include any explanation or additional text.
                    """, String.join(", ", terms), doc.getMetadata().toString());

                try {
                    String result = chatClient
                            .prompt()
                            .user(prompt)
                            .call()
                            .content()
                            .strip()
                            .toLowerCase();

                    if (result.contains("yes") || result.contains("sí")) {
                        return true;
                    }
                } catch (Exception e) {
                    log().warn("Error in NER-based metadata matching, continuing with fallback", e);
                }
            }
        }

        // As a last resort, try semantic matching on the content
        return semanticallyMatches(doc.getContent(), new String[]{query});
    }

    protected boolean containsInMetadata(Document doc, String[] terms) {
        for (Object val : doc.getMetadata().values()) {
            if (val instanceof String str && containsAnyKeyword(str, terms)) return true;
            if (val instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String s && containsAnyKeyword(s, terms)) return true;
                }
            }
        }
        return false;
    }

    protected String[] extractTermsFromNER(JSONObject entidades) {
        Set<String> terms = new HashSet<>();

        if (entidades.has("person"))
            entidades.getJSONArray("person").forEach(item -> terms.add(item.toString().toLowerCase()));

        if (entidades.has("filters")) {
            JSONObject filtros = entidades.getJSONObject("filters");
            for (String key : new String[]{"date", "place", "section", "time"}) {
                if (filtros.has(key))
                    filtros.getJSONArray(key).forEach(item -> terms.add(item.toString().toLowerCase()));
            }
        }

        if (entidades.has("answerType"))
            terms.add(entidades.getString("answerType").toLowerCase());

        return terms.toArray(new String[0]);
    }

    /**
     * Checks if content semantically matches keywords.
     * Uses English for internal processing, but preserves original language in content.
     */
    protected boolean semanticallyMatches(String content, String[] keywords) {
        String prompt = String.format("""
            You are a content matching system. Analyze if meeting minutes content mentions specific topics.
            
            Topics to check (may be in any language):
            %s
            
            Meeting minutes content (may be in any language):
            %s
            
            Task: Determine if the content mentions or discusses any of the specified topics.
            Consider semantic meaning, not just exact word matches.
            
            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """, String.join(", ", keywords), content);

        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip()
                    .toLowerCase();

            return result.contains("yes") || result.contains("sí");
        } catch (Exception e) {
            log().warn("Error in semantic content matching, defaulting to false", e);
            return false;
        }
    }

    /**
     * Checks if document metadata semantically matches the query.
     * Uses English for internal processing, but preserves original language in query and metadata.
     * 
     * SOLUTION 3.1: Added robust validation and fallback to avoid false negatives.
     */
    protected boolean semanticallyMatchesMetadata(Document doc, String query) {
        String prompt = String.format("""
            You are a metadata matching system. Analyze if document metadata semantically matches a user query.
            
            User query (may be in any language):
            "%s"
            
            Document metadata (values may be in any language):
            %s
            
            Task: Determine if this document's metadata semantically matches the intent of the query.
            
            Matching criteria:
            - Consider semantic meaning, not just exact word matches
            - Consider all metadata fields and their semantic meaning
            - Match regardless of exact wording or language
            - If query mentions dates, people, topics, etc., check if metadata contains relevant information
            
            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """, query, doc.getMetadata().toString());

        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            Boolean validated = validateLLMFilterResponse(result, "semanticallyMatchesMetadata");
            
            // If validation returns null (unknown), default to true (keep document) to avoid false negatives
            return validated != null ? validated : true;
        } catch (Exception e) {
            log().warn("Error in semantic metadata matching, defaulting to true (keep document)", e);
            return true; // Default to true on error to avoid false negatives
        }
    }
    
    /**
     * Validates LLM response for filtering operations.
     * Returns true if response is valid and indicates a match, false otherwise.
     * On error or invalid response, returns null to indicate "unknown" (should not filter).
     * 
     * SOLUTION 3.1: Robust validation of LLM responses for filtering.
     */
    private Boolean validateLLMFilterResponse(String response, String context) {
        if (response == null || response.trim().isEmpty()) {
            log().warn("Empty LLM response in {}", context);
            return null; // Unknown, don't filter
        }
        
        String cleaned = response.trim().toLowerCase();
        
        // Check for positive responses
        if (cleaned.contains("yes") || cleaned.contains("sí") || cleaned.contains("true")) {
            return true;
        }
        
        // Check for negative responses
        if (cleaned.contains("no") || cleaned.contains("false")) {
            return false;
        }
        
        // If unclear, don't filter (default to keeping the document)
        log().warn("Unclear LLM response in {}: '{}', defaulting to keep document", context, response);
        return null; // Unknown, don't filter
    }

    /**
     * Checks if minute metadata semantically matches the query.
     */
    protected boolean semanticallyMatchesMinute(Minute minute, String query) {
        String prompt = String.format("""
            You are a meeting metadata matching system. Analyze if meeting metadata semantically matches a user query.
            
            User query (may be in any language):
            "%s"
            
            Meeting metadata (values may be in any language):
            Date: %s
            Place: %s
            Topics: %s
            Decisions: %s
            Summary: %s
            Agenda: %s
            
            Task: Determine if this meeting metadata semantically matches the intent of the query.
            
            Matching criteria:
            - Consider semantic meaning, not just exact word matches
            - Consider all fields and their semantic meaning
            - Match regardless of exact wording or language
            - If query mentions dates, people, topics, etc., check if metadata contains relevant information
            
            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """,
            query,
            minute.date() != null ? minute.date() : "unknown",
            minute.place() != null ? minute.place() : "unknown",
            minute.topics() != null ? String.join(", ", minute.topics()) : "unknown",
            minute.decisions() != null ? String.join(", ", minute.decisions()) : "unknown",
            minute.summary() != null ? minute.summary() : "unknown",
            minute.agenda() != null ? minute.agenda().toString() : "unknown"
        );

        try {
            String result = getLLMResponseCached(prompt);
            
            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in semanticallyMatchesMinute, defaulting to true to avoid false negatives");
                return true; // Avoid false negatives
            }
            
            // Use validateLLMFilterResponse for consistent validation
            Boolean validated = validateLLMFilterResponse(result, "semanticallyMatchesMinute");
            
            // If validation returns null (unknown), default to true (keep document) to avoid false negatives
            if (validated != null) {
                return validated;
            }
            
            // Fallback: parse response manually
            String normalized = result.strip().toLowerCase();
            boolean matches = normalized.contains("yes") || normalized.contains("sí");
            
            // If response is unclear, default to true
            if (!matches && !normalized.contains("no") && !normalized.contains("no")) {
                log().warn("Unclear LLM response in semanticallyMatchesMinute: '{}', defaulting to true", result);
                return true; // Default to true when response is unclear
            }
            
            return matches;
        } catch (Exception e) {
            log().warn("Error in semantic minute matching, defaulting to true to avoid false negatives", e);
            return true; // Avoid false negatives
        }
    }

    /**
     * Extracts and deserializes the Minute object from Document metadata.
     * First tries to get the complete object from "minute" key (JSON or object).
     * If not available, reconstructs it from individual metadata fields.
     */
    @Cacheable(value = "minuteObjects", key = "#doc.hashCode()")
    protected Minute getMinuteFromMetadata(Document doc) {
        Map<String, Object> metadata = doc.getMetadata();
        
        // Try to get complete Minute object from "minute" key
        Object minuteObj = metadata.get("minute");
        if (minuteObj instanceof Minute) {
            return (Minute) minuteObj;
        }
        if (minuteObj instanceof String json) {
            try {
                return objectMapper.readValue(json, Minute.class);
            } catch (Exception ex) {
                log().info("Failed to deserialize Minute from JSON, attempting reconstruction from fields", ex);
            }
        }
        
        // This handles documents created before the fix or when serialization failed
        try {
            return reconstructMinuteFromMetadata(metadata);
        } catch (Exception ex) {
            log().warn("Failed to reconstruct Minute from metadata fields for document: {}", doc.getId(), ex);
            return null;
        }
    }
    
    /**
     * Reconstructs a Minute object from individual metadata fields.
     * This is a fallback when the complete object is not stored in metadata.
     */
    private Minute reconstructMinuteFromMetadata(Map<String, Object> metadata) {
        // Extract all fields with proper type handling using safe methods
        String id = safeGetString(metadata, "id");
        String filename = safeGetString(metadata, "filename");
        String date = safeGetString(metadata, "date");
        String place = safeGetString(metadata, "place");
        String startTime = safeGetString(metadata, "startTime");
        String endTime = safeGetString(metadata, "endTime");
        String president = safeGetString(metadata, "president");
        String secretary = safeGetString(metadata, "secretary");
        
        // Use safe methods for complex types
        List<String> attendees = safeGetStringList(metadata, "attendees");
        
        int numberOfAttendees = metadata.containsKey("numberOfAttendees") 
            ? safeGetInt(metadata, "numberOfAttendees", attendees.size())
            : attendees.size();
        
        Map<String, String> agenda = safeGetStringMap(metadata, "agenda");
        List<String> decisions = safeGetStringList(metadata, "decisions");
        List<String> mentionedEntities = safeGetStringList(metadata, "mentionedEntities");
        List<String> topics = safeGetStringList(metadata, "topics");
        
        String summary = safeGetString(metadata, "summary");
        
        // Generate ID if missing
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        
        return new Minute(
            id,
            filename,
            date,
            place,
            startTime,
            endTime,
            president,
            secretary,
            attendees,
            numberOfAttendees,
            agenda,
            decisions,
            mentionedEntities,
            topics,
            summary
        );
    }
    
    /**
     * Safely extracts a string value from metadata, handling null and type conversion.
     */
    private String safeGetString(Map<String, Object> metadata, String key) {
        if (metadata == null || key == null) {
            return null;
        }
        
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        
        if (value instanceof String) {
            String str = (String) value;
            return str.isBlank() ? null : str;
        }
        
        // Convert other types to string
        String result = value.toString();
        return result.isBlank() ? null : result;
    }
    
    /**
     * Safely extracts a list of strings from metadata, handling type conversion.
     */
    private List<String> safeGetStringList(Map<String, Object> metadata, String key) {
        if (metadata == null || key == null) {
            return new ArrayList<>();
        }
        
        Object value = metadata.get(key);
        if (value == null) {
            return new ArrayList<>();
        }
        
        // If already a List, convert elements to strings
        if (value instanceof List) {
            List<?> list = (List<?>) value;
            return list.stream()
                    .map(obj -> obj != null ? obj.toString() : "")
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        
        // If String, try to parse comma-separated values
        if (value instanceof String) {
            String str = ((String) value).trim();
            if (str.isEmpty()) {
                return new ArrayList<>();
            }
            
            // Try to parse as comma-separated string
            return Arrays.stream(str.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }
        
        log().warn("Unexpected type for key '{}': {}, expected List<String> or String. Returning empty list.", 
                  key, value.getClass().getName());
        return new ArrayList<>();
    }
    
    /**
     * Safely extracts a map of string to string from metadata, handling type conversion.
     */
    private Map<String, String> safeGetStringMap(Map<String, Object> metadata, String key) {
        if (metadata == null || key == null) {
            return new LinkedHashMap<>();
        }
        
        Object value = metadata.get(key);
        if (value == null) {
            return new LinkedHashMap<>();
        }
        
        // If already a Map, convert keys and values to strings
        if (value instanceof Map) {
            Map<String, String> result = new LinkedHashMap<>();
            ((Map<?, ?>) value).forEach((k, v) -> {
                if (k != null && v != null) {
                    result.put(k.toString(), v.toString());
                }
            });
            return result;
        }
        
        log().warn("Unexpected type for key '{}': {}, expected Map<String, String>. Returning empty map.", 
                  key, value.getClass().getName());
        return new LinkedHashMap<>();
    }
    
    /**
     * Safely extracts an integer value from metadata, handling type conversion.
     */
    private int safeGetInt(Map<String, Object> metadata, String key, int defaultValue) {
        if (metadata == null || key == null) {
            return defaultValue;
        }
        
        Object value = metadata.get(key);
        if (value == null) {
            return defaultValue;
        }
        
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        
        if (value instanceof String) {
            try {
                return Integer.parseInt((String) value);
            } catch (NumberFormatException e) {
                log().warn("Could not parse integer from key '{}': {}", key, value);
                return defaultValue;
            }
        }
        
        log().warn("Unexpected type for key '{}': {}, expected Number or String. Using default value: {}", 
                  key, value.getClass().getName(), defaultValue);
        return defaultValue;
    }
    
    /**
     * Checks if minute semantically matches NER entities.
     * Uses English for internal processing, but preserves original language in metadata values.
     */
    @Cacheable(value = "nerMatching", key = "#minute.hashCode() + '_' + #ner.hashCode()")
    protected boolean matchesMinuteWithNER(Minute minute, JSONObject ner) {
        if (ner == null || ner.isEmpty()) return true;

        if (ner.has("mentionedEntities") && !ner.getJSONArray("mentionedEntities").isEmpty()) {
            if (!matchesMentionedEntities(minute, ner)) {
                log().info("Minute {} filtered out by mentionedEntities mismatch", minute.id());
                return false;
            }
        }

        if (ner.has("agenda") && !ner.getJSONArray("agenda").isEmpty()) {
            if (!matchesAgendaItems(minute, ner)) {
                log().info("Minute {} filtered out by agenda items mismatch", minute.id());
                return false;
            }
        }

        String prompt = String.format("""
            You are a meeting metadata matching system. Analyze if meeting metadata matches specified NER entities.
            
            Meeting metadata (values may be in any language):
            Date: %s
            Place: %s
            President: %s
            Secretary: %s
            Topics: %s
            Decisions: %s
            Mentioned Entities: %s
            Summary: %s
            
            NER entities to match (JSON format):
            %s
            
            Task: Determine if this meeting metadata semantically matches ALL the specified NER entities.
            
            Matching criteria:
            - Consider semantic meaning, not just exact word matches
            - Consider context and relationships between entities
            - Match dates, people, topics, and other entities semantically
            - If multiple entities are specified, all should be present or relevant in the metadata
            
            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """,
            minute.date() != null ? minute.date() : "unknown",
            minute.place() != null ? minute.place() : "unknown",
            minute.president() != null ? minute.president() : "unknown",
            minute.secretary() != null ? minute.secretary() : "unknown",
            minute.topics() != null ? String.join(", ", minute.topics()) : "unknown",
            minute.decisions() != null ? String.join(", ", minute.decisions()) : "unknown",
            minute.mentionedEntities() != null ? String.join(", ", minute.mentionedEntities()) : "unknown",
            minute.summary() != null ? minute.summary() : "unknown",
            ner.toString(2)
        );

        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip()
                    .toLowerCase();
            return result.contains("yes") || result.contains("sí");
        } catch (Exception e) {
            log().warn("Error matching minute with NER, defaulting to true to avoid false negatives", e);
            return true; // Avoid false negatives (consistent with EnhancedNERHandler)
        }
    }
    
    /**
     * Checks if minute's agenda items match NER agenda items.
     * This is a direct filter before LLM call for better efficiency.
     * 
     * MEJORA: Added direct filtering by agenda items to improve correlation.
     */
    private boolean matchesAgendaItems(Minute minute, JSONObject ner) {
        if (minute.agenda() == null || minute.agenda().isEmpty()) {
            // If minute has no agenda but NER requires it, it doesn't match
            // However, we allow it to pass to LLM for semantic matching (maybe agenda info is in content)
            return true; // Let LLM decide
        }
        
        try {
            org.json.JSONArray nerAgenda = ner.getJSONArray("agenda");
            if (nerAgenda.length() == 0) {
                return true; // No agenda items to match
            }
            
            // Build a searchable string from all agenda items (values from the map)
            String agendaStr = minute.agenda().values().stream()
                    .filter(item -> item != null && !item.trim().isEmpty())
                    .map(String::toLowerCase)
                    .collect(Collectors.joining(" "));
            
            if (agendaStr.isEmpty()) {
                return true; // No agenda content to search
            }
            
            // Check if any NER agenda item matches any minute agenda item (case-insensitive, partial matching)
            for (int i = 0; i < nerAgenda.length(); i++) {
                String nerAgendaItem = nerAgenda.getString(i).toLowerCase().trim();
                if (nerAgendaItem.isEmpty()) continue;
                
                // Check if NER agenda item is contained in any agenda value
                boolean found = minute.agenda().values().stream()
                        .anyMatch(agendaValue -> {
                            if (agendaValue == null || agendaValue.trim().isEmpty()) return false;
                            String agendaValueLower = agendaValue.toLowerCase().trim();
                            return agendaValueLower.contains(nerAgendaItem) || 
                                   nerAgendaItem.contains(agendaValueLower) ||
                                   agendaValueLower.equals(nerAgendaItem);
                        });
                
                if (found) {
                    log().info("Found matching agenda item: NER='{}' matches Minute agenda", nerAgendaItem);
                    return true; // At least one match found
                }
            }
            
            log().info("No matching agenda items found. NER agenda: {}, Minute agenda: {}", 
                       nerAgenda, minute.agenda());
            return false; // No matches found
        } catch (Exception e) {
            log().warn("Error matching agenda items, allowing through to LLM", e);
            return true; // On error, let LLM decide
        }
    }

    /**
     * Checks if minute's mentionedEntities match NER mentionedEntities.
     * This is a direct filter before LLM call for better efficiency.
     * 
     * MEJORA: Added direct filtering by mentionedEntities to improve correlation.
     */
    private boolean matchesMentionedEntities(Minute minute, JSONObject ner) {
        if (minute.mentionedEntities() == null || minute.mentionedEntities().isEmpty()) {
            // If minute has no mentioned entities but NER requires them, it doesn't match
            // However, we allow it to pass to LLM for semantic matching (maybe entity is in content)
            return true; // Let LLM decide
        }
        
        try {
            org.json.JSONArray nerEntities = ner.getJSONArray("mentionedEntities");
            if (nerEntities.length() == 0) {
                return true; // No entities to match
            }
            
            // Check if any NER entity matches any minute entity (case-insensitive, partial matching)
            for (int i = 0; i < nerEntities.length(); i++) {
                String nerEntity = nerEntities.getString(i).toLowerCase().trim();
                if (nerEntity.isEmpty()) continue;
                
                for (String minuteEntity : minute.mentionedEntities()) {
                    if (minuteEntity == null || minuteEntity.trim().isEmpty()) continue;
                    
                    String minuteEntityLower = minuteEntity.toLowerCase().trim();
                    
                    // Exact match or substring match (entity name might be part of longer string)
                    if (minuteEntityLower.equals(nerEntity) ||
                        minuteEntityLower.contains(nerEntity) ||
                        nerEntity.contains(minuteEntityLower)) {
                        log().info("Found matching entity: NER='{}' matches Minute='{}'", nerEntity, minuteEntity);
                        return true; // At least one match found
                    }
                }
            }
            
            log().info("No matching entities found. NER entities: {}, Minute entities: {}", 
                       nerEntities, minute.mentionedEntities());
            return false; // No matches found
        } catch (Exception e) {
            log().warn("Error matching mentionedEntities, allowing through to LLM", e);
            return true; // On error, let LLM decide
        }
    }

    /**
     * Extracts a specific field from a Minute and formats it according to its type.
     */
    protected String extractFieldFromMinute(String field, Minute minute) {
        Object value = getMinuteFieldValue(minute, field);
        if (value == null) return null;

        if (value instanceof List<?> list) {
            return list.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(", "));
        }

        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining(", "));
        }

        return value.toString();
    }

    /**
     * Calcula la duración de una reunión en minutos
     */
    protected int calculateDurationFromMinute(Minute minute) {
        try {
            LocalTime start = LocalTime.parse(minute.startTime(), TIME_FORMATTER);
            LocalTime end = LocalTime.parse(minute.endTime(), TIME_FORMATTER);
            return (end.getHour() * 60 + end.getMinute()) - (start.getHour() * 60 + start.getMinute());
        } catch (DateTimeParseException | NullPointerException ex) {
            return 0;
        }
    }

    /**
     * Obtiene el valor de un campo específico del Minute
     */
    private Object getMinuteFieldValue(Minute minute, String field) {
        return switch (field.toLowerCase()) {
            case "date", "fecha" -> minute.date();
            case "place", "lugar" -> minute.place();
            case "president", "presidente" -> minute.president();
            case "secretary", "secretario" -> minute.secretary();
            case "starttime", "hora_inicio" -> minute.startTime();
            case "endtime", "hora_fin" -> minute.endTime();
            case "topics", "temas" -> minute.topics();
            case "decisions", "decisiones" -> minute.decisions();
            case "summary", "resumen" -> minute.summary();
            case "agenda" -> minute.agenda();
            case "attendees", "asistentes" -> minute.attendees();
            default -> null;
        };
    }

    /**
     * Compara dos valores de duración y devuelve la diferencia
     */
    protected int compareDurations(Minute minute1, Minute minute2) {
        int duration1 = calculateDurationFromMinute(minute1);
        int duration2 = calculateDurationFromMinute(minute2);
        return duration1 - duration2;
    }

    /**
     * Compara dos listas de asistentes y devuelve las diferencias
     */
    protected Map<String, List<String>> compareAttendees(Minute minute1, Minute minute2) {
        Set<String> attendees1 = new HashSet<>(minute1.attendees() != null ? minute1.attendees() : List.of());
        Set<String> attendees2 = new HashSet<>(minute2.attendees() != null ? minute2.attendees() : List.of());

        List<String> onlyIn1 = attendees1.stream()
                .filter(a -> !attendees2.contains(a))
                .collect(Collectors.toList());

        List<String> onlyIn2 = attendees2.stream()
                .filter(a -> !attendees1.contains(a))
                .collect(Collectors.toList());

        return Map.of(
            "only_in_first", onlyIn1,
            "only_in_second", onlyIn2
        );
    }

    /**
     * Compara dos listas de temas y devuelve las diferencias
     */
    protected Map<String, List<String>> compareTopics(Minute minute1, Minute minute2) {
        List<String> topics1 = minute1.topics() != null ? minute1.topics() : List.of();
        List<String> topics2 = minute2.topics() != null ? minute2.topics() : List.of();

        String prompt = """
                Compare these two lists of topics and identify:
                1. Topics that are semantically similar (even if worded differently)
                2. Topics that are unique to each list
                
                List 1: %s
                List 2: %s
                
                Format the response as a JSON object with these keys:
                - "similar": list of similar topics
                - "only_in_first": list of topics unique to first list
                - "only_in_second": list of topics unique to second list
                """.formatted(
                    String.join(", ", topics1),
                    String.join(", ", topics2)
                );

        try {
            String result = chatClient.prompt().user(prompt).call().content().strip();
            return objectMapper.readValue(result, new TypeReference<Map<String, List<String>>>() {});
        } catch (Exception ex) {
            return Map.of(
                "similar", List.of(),
                "only_in_first", topics1,
                "only_in_second", topics2
            );
        }
    }

    /**
     * Extracts Minute objects from documents in parallel.
     */
    protected List<Minute> extractMinutesInParallel(List<Document> docs) {
        return docs.parallelStream()
                .map(doc -> {
                    try {
                        return getMinuteFromMetadata(doc);
                    } catch (Exception e) {
                        log().warn("Error extracting minute from document {}, skipping: {}", 
                                 doc != null ? doc.getId() : "null", e.getMessage());
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .filter(this::isMinuteComplete)
                .filter(this::hasUsefulData)
                .collect(Collectors.toList());
    }
    
    /**
     * Validates that a Minute object has all critical fields.
     * 
     * @param minute The minute to validate
     * @return true if minute has critical fields, false otherwise
     */
    private boolean isMinuteComplete(Minute minute) {
        if (minute == null) {
            return false;
        }
        
        // Critical fields: id, filename, date
        if (minute.id() == null || minute.id().trim().isEmpty()) {
            log().info("Minute missing id, filtering out");
            return false;
        }
        
        if (minute.filename() == null || minute.filename().trim().isEmpty()) {
            log().info("Minute missing filename, filtering out");
            return false;
        }
        
        // Date is critical for most queries
        if (minute.date() == null || minute.date().trim().isEmpty()) {
            log().info("Minute {} missing date, filtering out", minute.id());
            return false;
        }
        
        return true;
    }
    
    /**
     * Validates that a Minute object has useful data beyond critical fields.
     * This prevents processing minutes that are complete but have no useful information.
     * 
     * @param minute The minute to validate
     * @return true if minute has at least one useful data field, false otherwise
     */
    private boolean hasUsefulData(Minute minute) {
        if (minute == null) {
            return false;
        }
        
        // Check if minute has at least one field with useful data
        boolean hasTopics = minute.topics() != null && !minute.topics().isEmpty();
        boolean hasDecisions = minute.decisions() != null && !minute.decisions().isEmpty();
        boolean hasSummary = minute.summary() != null && !minute.summary().trim().isEmpty();
        boolean hasAttendees = minute.attendees() != null && !minute.attendees().isEmpty();
        boolean hasAgenda = minute.agenda() != null && !minute.agenda().isEmpty();
        boolean hasMentionedEntities = minute.mentionedEntities() != null && !minute.mentionedEntities().isEmpty();
        
        boolean hasUseful = hasTopics || hasDecisions || hasSummary || hasAttendees || hasAgenda || hasMentionedEntities;
        
        if (!hasUseful) {
            log().info("Minute {} has no useful data fields, filtering out", minute.id());
        }
        
        return hasUseful;
    }

    /**
     * Filters relevant minutes based on NER or query relevance using EnhancedNERHandler.
     * Implements progressive fallback when filters are too strict.
     * 
     * SOLUTION 2.1: Reduced filtering layers and increased limits for better recall.
     */
    protected List<Minute> filterRelevantMinutes(String query, List<Minute> minutes, JSONObject ner) {
        if (minutes.isEmpty()) {
            return minutes;
        }
        
        log().info("Starting to filter {} minutes for query: {}", minutes.size(), query);
        
        // STEP 1: Pre-filter by date (if NER has date) - MORE FLEXIBLE
        List<Minute> preFiltered = minutes;
        if (ner != null && ner.has("date") && !ner.getJSONArray("date").isEmpty() && minutes.size() > 50) {
            preFiltered = preFilterMinutesFast(minutes, ner);
            log().info("Pre-filtered {} minutes to {} using flexible date matching", minutes.size(), preFiltered.size());
            
            // If pre-filtering removed too many, use original list
            if (preFiltered.size() < minutes.size() * 0.1) {
                log().warn("Pre-filtering removed too many minutes ({}%), using original list", 
                          (1.0 - (double)preFiltered.size() / minutes.size()) * 100);
                preFiltered = minutes;
            }
        }
        
        // STEP 2: Direct matching (mentionedEntities, agenda) - NO LLM
        List<Minute> directMatched = preFiltered;
        if (ner != null && !ner.isEmpty()) {
            directMatched = preFiltered.stream()
                    .filter(minute -> {
                        // Only filter if we have clear criteria
                        if (ner.has("mentionedEntities") && !ner.getJSONArray("mentionedEntities").isEmpty()) {
                            if (!matchesMentionedEntities(minute, ner)) {
                                return false;
                            }
                        }
                        if (ner.has("agenda") && !ner.getJSONArray("agenda").isEmpty()) {
                            if (!matchesAgendaItems(minute, ner)) {
                                return false;
                            }
                        }
                        return true;
                    })
                    .limit(50) // Increased from 25 to 50
                    .collect(Collectors.toList());
            
            log().info("Direct matching filtered {} minutes to {}", preFiltered.size(), directMatched.size());
        }
        
        // STEP 3: LLM-based filtering (reduced to single pass) - WITH FALLBACK
        List<Minute> filtered = directMatched;
        if (ner != null && !ner.isEmpty() && directMatched.size() > 10) {
            // Try NER matching first (less expensive)
            filtered = directMatched.stream()
                    .filter(minute -> {
                        try {
                            return nerHandler.matchesMinuteWithNER(minute, ner);
                        } catch (Exception e) {
                            log().warn("Error in NER matching for minute {}, defaulting to true", minute.id(), e);
                            return true; // Default to true on error
                        }
                    })
                    .limit(40) // Increased from 15 to 40
                    .collect(Collectors.toList());
            
            log().info("NER matching filtered {} minutes to {}", directMatched.size(), filtered.size());
            
            // If NER filtering removed too many, use direct matched
            if (filtered.isEmpty() && !directMatched.isEmpty()) {
                log().warn("NER filtering removed all minutes, using direct matched minutes");
                filtered = directMatched.stream().limit(40).collect(Collectors.toList());
            }
        }
        
        // STEP 4: Relevance filtering (only if we still have too many)
        if (filtered.size() > 30) {
            filtered = filtered.stream()
                    .filter(minute -> {
                        try {
                            return isRelevantToQueryCached(query, minute);
                        } catch (Exception e) {
                            log().warn("Error in relevance check for minute {}, defaulting to true", minute.id(), e);
                            return true; // Default to true on error
                        }
                    })
                    .limit(30) // Final limit
                    .collect(Collectors.toList());
            
            log().info("Relevance filtering reduced to {} minutes", filtered.size());
        }
        
        // Final fallback: if still empty, return at least some minutes (sorted by relevance)
        if (filtered.isEmpty() && !minutes.isEmpty()) {
            log().warn("All filtering failed, returning top {} minutes sorted by date as last resort", 
                      Math.min(10, minutes.size()));
            // Sort by date (most recent first) as a basic relevance indicator
            return minutes.stream()
                    .sorted((a, b) -> {
                        String dateA = a.date() != null ? a.date() : "";
                        String dateB = b.date() != null ? b.date() : "";
                        // Try to parse dates for proper sorting, fallback to string comparison
                        LocalDate parsedA = parseDateToLocalDate(dateA);
                        LocalDate parsedB = parseDateToLocalDate(dateB);
                        if (parsedA != null && parsedB != null) {
                            return parsedB.compareTo(parsedA); // Most recent first
                        }
                        return dateB.compareTo(dateA); // String comparison fallback
                    })
                    .limit(10)
                    .collect(Collectors.toList());
        }
        
        log().info("Final filtered {} relevant minutes from {} total", filtered.size(), minutes.size());
        return filtered;
    }
    
    /**
     * Fast pre-filtering without LLM to reduce the number of minutes before expensive filtering.
     * Uses improved date matching with LocalDate parsing for better accuracy.
     * 
     * SOLUTION 2.2: Implemented flexible date matching (same year/month, within 1-2 days).
     */
    private List<Minute> preFilterMinutesFast(List<Minute> minutes, JSONObject ner) {
        if (ner == null || ner.isEmpty() || !ner.has("date") || ner.getJSONArray("date").isEmpty()) {
            return minutes.stream().limit(50).collect(Collectors.toList()); // Increased from 30 to 50
        }
        
        org.json.JSONArray nerDates = ner.getJSONArray("date");
        List<String> nerDateStrings = new ArrayList<>();
        for (int i = 0; i < nerDates.length(); i++) {
            nerDateStrings.add(nerDates.getString(i));
        }
        
        return minutes.stream()
                .filter(minute -> {
                    String minuteDate = minute.date();
                    if (minuteDate == null || minuteDate.trim().isEmpty()) {
                        return true; // Keep if no date
                    }
                    
                    // Try flexible matching
                    for (String nerDate : nerDateStrings) {
                        if (datesMatchFlexibly(minuteDate, nerDate)) {
                            return true; // Match found
                        }
                    }
                    
                    // If no flexible match, still keep if dates are in same year
                    return datesInSameYear(minuteDate, nerDateStrings);
                })
                .limit(50) // Increased from 30 to 50
                .collect(Collectors.toList());
    }
    
    /**
     * Checks if two dates match flexibly (same year/month, or within 1-2 days).
     * SOLUTION 2.2: Flexible date matching to avoid rejecting relevant documents.
     */
    private boolean datesMatchFlexibly(String date1, String date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        
        // Try precise matching first
        if (datesMatchPrecisely(date1, date2)) {
            return true;
        }
        
        // Try parsing to LocalDate for flexible comparison
        LocalDate parsed1 = parseDateToLocalDate(date1);
        LocalDate parsed2 = parseDateToLocalDate(date2);
        
        if (parsed1 != null && parsed2 != null) {
            // Same year and month = relevant
            if (parsed1.getYear() == parsed2.getYear() && parsed1.getMonth() == parsed2.getMonth()) {
                return true;
            }
            
            // Within 1-2 days = relevant (for typos or similar dates)
            long daysDiff = Math.abs(java.time.temporal.ChronoUnit.DAYS.between(parsed1, parsed2));
            if (daysDiff <= 2) {
                return true;
            }
        }
        
        // Fallback: string matching (same year)
        return datesInSameYear(date1, List.of(date2));
    }
    
    /**
     * Checks if dates are in the same year.
     * SOLUTION 2.2: Helper for flexible date matching.
     */
    private boolean datesInSameYear(String date1, List<String> date2List) {
        LocalDate parsed1 = parseDateToLocalDate(date1);
        if (parsed1 == null) {
            return false;
        }
        
        int year1 = parsed1.getYear();
        
        for (String date2 : date2List) {
            LocalDate parsed2 = parseDateToLocalDate(date2);
            if (parsed2 != null && parsed2.getYear() == year1) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Checks if two dates match precisely using LocalDate parsing.
     */
    private boolean datesMatchPrecisely(String date1, String date2) {
        if (date1 == null || date2 == null) {
            return false;
        }
        
        try {
            LocalDate parsed1 = parseDateToLocalDate(date1);
            LocalDate parsed2 = parseDateToLocalDate(date2);
            
            if (parsed1 != null && parsed2 != null) {
                return parsed1.equals(parsed2);
            }
        } catch (Exception e) {
            log().info("Error parsing dates for comparison: {} vs {}", date1, date2);
        }
        
        return false;
    }
    
    /**
     * Parses a date string to LocalDate using multiple formatters.
     */
    private LocalDate parseDateToLocalDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        List<DateTimeFormatter> formatters = Arrays.asList(
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
            DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
            DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH)
        );
        
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(dateStr.trim(), formatter);
            } catch (DateTimeParseException ignored) {
                // Try next formatter
            }
        }
        
        return null;
    }
    
    /**
     * Filters minutes using NER entities intelligently
     */
    protected List<Minute> filterMinutesWithNER(String query, List<Minute> minutes, JSONObject ner) {
        log().info("Filtering {} minutes with NER entities", minutes.size());
        
        return minutes.parallelStream()
                .filter(minute -> matchesMinuteWithNERCached(minute, ner))
                .filter(minute -> isRelevantToQueryCached(query, minute))
                .collect(Collectors.toList());
    }

    /**
     * Cached NER matching evaluation
     */
    @Cacheable(value = "nerMatching", key = "#minute.hashCode() + '_' + #ner.hashCode()")
    protected boolean matchesMinuteWithNERCached(Minute minute, JSONObject ner) {
        return matchesMinuteWithNER(minute, ner);
    }

    /**
     * Filters minutes by query relevance using LLM
     */
    protected List<Minute> filterMinutesByQueryRelevance(String query, List<Minute> minutes) {
        log().info("Filtering {} minutes by query relevance", minutes.size());
        
        return minutes.parallelStream()
                .filter(minute -> isRelevantToQueryCached(query, minute))
                .collect(Collectors.toList());
    }

    /**
     * Cached query relevance evaluation
     */
    @Cacheable(value = "queryRelevance", key = "#query.hashCode() + '_' + #minute.hashCode()")
    protected boolean isRelevantToQueryCached(String query, Minute minute) {
        return isRelevantToQueryByLLM(query, minute);
    }

    /**
     * Determines if a minute is relevant to the query using LLM.
     * 
     * SOLUTION 3.1: Added robust validation and fallback to avoid false negatives.
     */
    protected boolean isRelevantToQueryByLLM(String query, Minute minute) {
        if (query == null || query.trim().isEmpty() || minute == null) {
            return false;
        }
        
        try {
            String prompt = generateRelevancePrompt(query, minute);
            String result = getLLMResponseCached(prompt);
            
            Boolean validated = validateLLMFilterResponse(result, "isRelevantToQueryByLLM");
            
            // If validation returns null (unknown), default to true (keep document) to avoid false negatives
            return validated != null ? validated : true;
        } catch (Exception e) {
            log().warn("Error in relevance check, defaulting to true (keep document)", e);
            return true; // Default to true on error to avoid false negatives
        }
    }

    /**
     * Generates adaptive relevance prompt based on query context.
     */
    protected String generateRelevancePrompt(String query, Minute minute) {
        String queryType = analyzeQueryType(query);
        
        return String.format("""
            You are a relevance analysis system. Analyze if meeting metadata is relevant to a user query.
            
            User query (may be in any language):
            "%s"
            
            Query type: %s
            
            Meeting metadata (values may be in any language):
            Date: %s
            Place: %s
            President: %s
            Secretary: %s
            Topics: %s
            Decisions: %s
            Summary: %s
            
            Task: Determine if this meeting contains information that directly answers or relates to the query.
            
            Relevance criteria:
            - Consider semantic meaning, not just exact matches
            - Check if the query can be answered using information from this meeting
            - Consider all metadata fields and their relevance to the query
            - Be inclusive: if the meeting might be relevant, answer YES
            
            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """,
            query,
            queryType,
            minute.date() != null ? minute.date() : "unknown",
            minute.place() != null ? minute.place() : "unknown",
            minute.president() != null ? minute.president() : "unknown",
            minute.secretary() != null ? minute.secretary() : "unknown",
            minute.topics() != null ? String.join(", ", minute.topics()) : "unknown",
            minute.decisions() != null ? String.join(", ", minute.decisions()) : "unknown",
            minute.summary() != null ? minute.summary() : "unknown"
        );
    }

    /**
     * Analyzes query type to generate more specific prompts
     */
    protected String analyzeQueryType(String query) {
        String queryLower = query.toLowerCase();
        
        if (queryLower.contains("decision") || queryLower.contains("decid") || queryLower.contains("acord")) {
            return "decision-focused";
        } else if (queryLower.contains("topic") || queryLower.contains("tema") || queryLower.contains("discut")) {
            return "topic-focused";
        } else if (queryLower.contains("person") || queryLower.contains("president") || queryLower.contains("secretary")) {
            return "person-focused";
        } else if (queryLower.contains("date") || queryLower.contains("fecha") || queryLower.contains("when")) {
            return "date-focused";
        } else if (queryLower.contains("place") || queryLower.contains("lugar") || queryLower.contains("where")) {
            return "location-focused";
        } else if (queryLower.contains("entity") || queryLower.contains("entidad") || queryLower.contains("persona")) {
            return "entity-focused";
        } else if (queryLower.contains("count") || queryLower.contains("cuántos") || queryLower.contains("cuantos")) {
            return "count-focused";
        } else if (queryLower.contains("compare") || queryLower.contains("comparar") || queryLower.contains("comparación")) {
            return "comparison-focused";
        } else {
            return "general";
        }
    }

    /**
     * Cached LLM response with error handling and validation.
     * Returns empty string if LLM call fails or response is empty.
     */
    @Cacheable(value = "llmResponses", key = "#prompt.hashCode()")
    protected String getLLMResponseCached(String prompt) {
        if (prompt == null || prompt.trim().isEmpty()) {
            log().warn("Empty prompt provided to getLLMResponseCached");
            return "";
        }
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in getLLMResponseCached");
                return "";
            }
            
            return response.strip();
        } catch (Exception e) {
            log().error("Error in getLLMResponseCached, returning empty string", e);
            return "";
        }
    }

    /**
     * Retrieves documents with intelligent metadata filtering using EnhancedNERHandler.
     * Filters documents that have relevant metadata fields (either complete Minute object or individual fields).
     * Implements robust fallback when metadata filtering removes all documents.
     */
    protected List<Document> retrieveDocumentsWithMetadataFilter(String query, String[] relevantFields) {
        List<Document> docs = retriever.retrieve(query);

        List<Document> metadataDocs = docs.stream()
                .filter(doc -> hasMetadataFields(doc, relevantFields))
                .collect(Collectors.toList());

        // FALLBACK 1: If metadata filtering removed all documents, use unfiltered documents
        if (metadataDocs.isEmpty() && !docs.isEmpty()) {
            log().warn("Metadata filtering removed all {} documents, using unfiltered documents as fallback", docs.size());
            metadataDocs = docs;
        } else if (metadataDocs.size() < docs.size() * 0.1 && docs.size() > 10) {
            // FALLBACK 2: If filtering removed more than 90%, use less strict filtering
            log().warn("Metadata filtering removed {}% of documents ({} of {}), using less strict filtering",
                    (1.0 - (double) metadataDocs.size() / docs.size()) * 100,
                    docs.size() - metadataDocs.size(), docs.size());
            // Accept documents with at least basic metadata fields
            metadataDocs = docs.stream()
                    .filter(this::hasBasicMetadata)
                    .collect(Collectors.toList());
        }

        // Deduplicate documents, selecting chunk with most complete metadata
        List<Document> deduplicatedDocs = deduplicateDocuments(metadataDocs);

        log().info("Retrieved {} unique documents from {} chunks ({} total retrieved, {} after metadata filter)",
                deduplicatedDocs.size(), metadataDocs.size(), docs.size(), metadataDocs.size());
        return deduplicatedDocs;
    }
    
    /**
     * Extracts the document_id from a document's metadata.
     * Falls back to the document's id if document_id is not present.
     */
    private String getDocumentId(Document doc) {
        if (doc == null) {
            return null;
        }
        
        Map<String, Object> metadata = doc.getMetadata();
        if (metadata == null) {
            return doc.getId();
        }
        
        // Try to get document_id first (new documents)
        Object docId = metadata.get("document_id");
        if (docId != null) {
            return docId.toString();
        }
        
        // Fallback: try to get id from metadata (should be the same as document_id)
        Object id = metadata.get("id");
        if (id != null) {
            return id.toString();
        }

        return doc.getId();
    }
    
    /**
     * Checks if a document has the necessary metadata fields.
     * Returns true if it has "minute" key OR has at least one of the relevant fields.
     * 
     * SOLUTION 1.2: Made more permissive - accepts documents with at least one relevant field OR basic metadata fields.
     */
    private boolean hasMetadataFields(Document doc, String[] relevantFields) {
        if (doc == null || doc.getMetadata() == null) {
            return false;
        }
        
        Map<String, Object> metadata = doc.getMetadata();
        
        // If it has the "minute" key, validate that it's not malformed
        if (metadata.containsKey("minute")) {
            Object minuteObj = metadata.get("minute");
            // Validate that minute is not null, empty string, or obviously invalid
            if (minuteObj != null && 
                !(minuteObj instanceof String && ((String) minuteObj).trim().isEmpty())) {
                return true;
            }
            // If minute is malformed, continue with validation of individual fields
            log().info("Document has 'minute' key but value is malformed, checking individual fields");
        }
        
        // Check if it has at least one relevant field (more permissive)
        int foundFields = 0;
        for (String field : relevantFields) {
            if (hasFieldOrDerived(metadata, field)) {
                foundFields++;
            }
        }
        
        // Accept if it has at least one relevant field OR basic metadata fields
        boolean hasRelevantField = foundFields > 0;
        boolean hasBasicFields = hasFieldOrDerived(metadata, "date") ||
                                metadata.containsKey("id") || 
                                metadata.containsKey("filename");
        
        return hasRelevantField || hasBasicFields;
    }
    
    /**
     * Checks if a document has basic metadata fields (date, id, or filename).
     * Used for less strict filtering when strict filtering removes too many documents.
     */
    private boolean hasBasicMetadata(Document doc) {
        if (doc == null || doc.getMetadata() == null) {
            return false;
        }
        
        Map<String, Object> metadata = doc.getMetadata();
        return hasFieldOrDerived(metadata, "date") || 
               metadata.containsKey("id") || 
               metadata.containsKey("filename") ||
               metadata.containsKey("minute");
    }

    /**
     * Checks presence of a field or its derived equivalents.
     */
    private boolean hasFieldOrDerived(Map<String, Object> metadata, String field) {
        if (metadata == null || field == null) {
            return false;
        }

        switch (field) {
            case "date":
                return metadata.containsKey("date") && metadata.get("date") != null
                        || metadata.containsKey("date_iso") && metadata.get("date_iso") != null
                        || metadata.containsKey("year") && metadata.get("year") != null
                        || metadata.containsKey("month") && metadata.get("month") != null;
            case "numberOfAttendees":
                return (metadata.containsKey("numberOfAttendees") && metadata.get("numberOfAttendees") != null)
                        || (metadata.containsKey("attendeesCount") && metadata.get("attendeesCount") != null);
            default:
                return metadata.containsKey(field) && metadata.get(field) != null;
        }
    }

    /**
     * Checks if document has metadata relevant to the query.
     * Uses semantic matching to determine relevance instead of literal string matching.
     */
    protected boolean hasRelevantMetadata(Document doc, String query, String[] relevantFields) {
        Map<String, Object> metadata = doc.getMetadata();
        
        // If document has no metadata at all, it's not relevant
        if (metadata.isEmpty()) {
            return false;
        }
        
        // Build a summary of metadata values for semantic matching
        StringBuilder metadataSummary = new StringBuilder();
        for (String field : relevantFields) {
            Object value = metadata.get(field);
            if (value != null) {
                metadataSummary.append(field).append(": ").append(value.toString()).append("; ");
            }
        }
        
        // If we have no relevant fields, check basic fields
        if (metadataSummary.length() == 0) {
            metadataSummary.append("date: ").append(metadata.getOrDefault("date", "")).append("; ");
            metadataSummary.append("topics: ").append(metadata.getOrDefault("topics", "")).append("; ");
        }
        
        // Use semantic matching to determine if metadata is relevant to query
        // This is less strict than exact matching but more accurate than always returning true
        return semanticallyMatchesMetadata(doc, query);
    }

    /**
     * Retrieves documents with intelligent fallback strategy.
     * Uses NER entities if available to filter at database level for better precision.
     * 
     * @param query The search query
     * @param relevantFields Metadata fields to filter by
     * @param ner NER entities extracted from query (optional, can be null)
     * @return List of retrieved documents
     */
    protected List<Document> retrieveDocumentsWithFallback(String query, String[] relevantFields, JSONObject ner) {
        // LEVEL 1: Try NER-based retrieval if NER is available and retriever supports it
        if (ner != null && !ner.isEmpty()) {
            // Validate that NER has at least one useful field before using it
            if (hasUsefulNERFields(ner)) {
                try {
                    List<Document> docs = retriever.retrieveWithMetadataFilters(query, ner);
                    List<Document> originalDocs = new ArrayList<>(docs); // Save for fallback

                    // Filter by relevantFields if necessary
                    if (relevantFields != null && relevantFields.length > 0 && !docs.isEmpty()) {
                        docs = docs.stream()
                                .filter(doc -> hasMetadataFields(doc, relevantFields))
                                .collect(Collectors.toList());

                        // FALLBACK: If filtering by relevantFields removed all documents, use unfiltered
                        if (docs.isEmpty() && !originalDocs.isEmpty()) {
                            log().warn("Filtering by relevantFields removed all documents, using unfiltered documents as fallback");
                            docs = originalDocs;
                        }
                    }

                    // Deduplicate documents (selecting chunk with most complete metadata)
                    docs = deduplicateDocuments(docs);

                    if (!docs.isEmpty()) {
                        log().info("Retrieved {} documents using NER-based retrieval with metadata filters", docs.size());
                        return docs;
                    } else {
                        log().info("NER-based retrieval returned no documents after filtering, trying fallback");
                    }
                } catch (Exception e) {
                    log().warn("Error using NER-based retrieval, falling back to basic retrieval: {}", e.getMessage());
                }
            } else {
                log().info("NER entities present but no useful fields, skipping NER-based retrieval");
            }
        }
        
        // LEVEL 2: Try metadata filtering without NER (original approach)
        List<Document> docs = retrieveDocumentsWithMetadataFilter(query, relevantFields);
        
        // Fallback 1: If empty, try basic retrieval
        if (docs.isEmpty()) {
            log().info("Metadata filtering returned no documents, trying basic retrieval");
            docs = retrieveDocuments(query);
        }
        
        // Fallback 2: If still empty, try with higher topK and lower threshold
        if (docs.isEmpty()) {
            log().info("Basic retrieval returned no documents after metadata filter");
        }
        
        return docs;
    }
    
    /**
     * Overloaded method for backward compatibility (without NER).
     * Delegates to the main method with null NER.
     */
    protected List<Document> retrieveDocumentsWithFallback(String query, String[] relevantFields) {
        return retrieveDocumentsWithFallback(query, relevantFields, null);
    }
    
    /**
     * Deduplicates documents by document_id, selecting the chunk with most complete metadata.
     * Improved selection based on metadata completeness.
     */
    private List<Document> deduplicateDocuments(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            return docs;
        }
        
        Map<String, Document> uniqueDocuments = docs.stream()
                .collect(Collectors.toMap(
                    doc -> getDocumentId(doc),
                    doc -> doc,
                    (existing, replacement) -> {
                        int existingFields = countMetadataFields(existing);
                        int replacementFields = countMetadataFields(replacement);
                        if (replacementFields > existingFields) {
                            log().info("Selecting replacement chunk with {} metadata fields (existing had {})", 
                                      replacementFields, existingFields);
                            return replacement;
                        }
                        return existing;
                    }
                ));
        
        List<Document> deduplicated = new ArrayList<>(uniqueDocuments.values());
        log().info("Deduplicated {} documents from {} chunks", deduplicated.size(), docs.size());
        return deduplicated;
    }
    
    /**
     * Counts non-null, non-empty metadata fields in a document.
     * Used to select the chunk with most complete metadata when deduplicating.
     */
    private int countMetadataFields(Document doc) {
        if (doc == null) {
            return 0;
        }
        
        Map<String, Object> metadata = doc.getMetadata();
        if (metadata == null) {
            return 0;
        }
        
        int count = 0;
        for (Object value : metadata.values()) {
            if (value != null) {
                if (value instanceof String && !((String) value).trim().isEmpty()) {
                    count++;
                } else if (value instanceof List && !((List<?>) value).isEmpty()) {
                    count++;
                } else if (value instanceof Map && !((Map<?, ?>) value).isEmpty()) {
                    count++;
                } else if (!(value instanceof String) && !(value instanceof List) && !(value instanceof Map)) {
                    count++;
                }
            }
        }
        return count;
    }
    
    /**
     * Validates that NER has at least one useful field before using it for retrieval.
     * Prevents unnecessary calls with invalid or empty NER.
     */
    private boolean hasUsefulNERFields(JSONObject ner) {
        if (ner == null || ner.isEmpty()) {
            return false;
        }
        
        // Check if NER has at least one non-empty field
        String[] usefulFields = {"date", "mentionedEntities", "agenda", "place", "person"};
        for (String field : usefulFields) {
            if (ner.has(field)) {
                Object value = ner.get(field);
                if (value instanceof org.json.JSONArray && ((org.json.JSONArray) value).length() > 0) {
                    return true;
                } else if (value instanceof String && !((String) value).trim().isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }
    
    /**
     * Generates not found message using LLM.
     * Ensures response language matches query language.
     */
    protected String generateNotFoundMessage(String query) {
        String prompt = String.format("""
            You are a helpful assistant. The user asked the following question (in any language):
            "%s"
            
            IMPORTANT: You must respond in the EXACT SAME LANGUAGE as the user's question.
            - If the question is in Spanish, respond in Spanish
            - If the question is in English, respond in English
            - Match the language exactly
            
            Write a short, polite message indicating that no relevant meeting minutes were found for this query.
            Be concise and helpful.
            """, query);
        
        try {
            return getLLMResponseCached(prompt);
        } catch (Exception e) {
            log().warn("Error generating not found message, using fallback", e);
            return generateFallbackNotFoundMessage(query);
        }
    }
    
    /**
     * Generates a fallback "not found" message when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackNotFoundMessage(String query) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*") || 
                           queryLower.contains("quién") || queryLower.contains("qué") || 
                           queryLower.contains("cuándo") || queryLower.contains("dónde") ||
                           queryLower.contains("cuántos") || queryLower.contains("cómo");
        
        if (isSpanish) {
            return "Lo siento, no se encontraron actas de reunión relevantes para esta consulta.";
        } else {
            return "I'm sorry, no relevant meeting minutes were found for this query.";
        }
    }

    /**
     * Generates no data message using LLM.
     * Uses English for internal processing, but response matches query language.
     */
    protected String generateNoDataMessage(String query) {
        if (query == null || query.trim().isEmpty()) {
            return generateFallbackNotFoundMessage("");
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Write a short message indicating that no relevant data was found for the query, 
            in the same language as the query.
            """, query);
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateNoDataMessage, using fallback");
                return generateFallbackNotFoundMessage(query);
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error generating no data message, using fallback", e);
            return generateFallbackNotFoundMessage(query);
        }
    }

    /**
     * Calculates relevance score using LLM.
     * Uses English for internal processing, but preserves original language in query and content.
     */
    protected double calculateRelevanceScore(String query, String itemContent, String context) {
        if (query == null || query.trim().isEmpty()) {
            return 0.5; // Default score
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Item content (may be in any language): %s
            Context (may be in any language): %s
            
            Rate the relevance of this item to the query on a scale of 0.0 to 1.0.
            Consider: direct relevance, completeness, clarity, and usefulness.
            
            Respond with ONLY a number between 0.0 and 1.0.
            Do not include any explanation or additional text.
            """, query, itemContent != null ? itemContent : "", context != null ? context : "");
        
        try {
            String result = getLLMResponseCached(prompt);
            
            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in calculateRelevanceScore, defaulting to 0.5");
                return 0.5;
            }
            
            String cleaned = result.strip();
            // Extract first number from response
            String numberStr = cleaned.replaceAll("[^0-9.]", "").split("\\s+")[0];
            if (numberStr.isEmpty()) {
                return 0.5;
            }
            
            return Double.parseDouble(numberStr);
        } catch (NumberFormatException e) {
            log().warn("Error parsing relevance score, defaulting to 0.5", e);
            return 0.5; // Default score if parsing fails
        } catch (Exception e) {
            log().error("Error calculating relevance score, defaulting to 0.5", e);
            return 0.5;
        }
    }

    /**
     * Checks if two items are similar based on content overlap
     */
    protected boolean isSimilarToCluster(String itemContent, String clusterContent, double threshold) {
        String itemLower = itemContent.toLowerCase();
        String clusterLower = clusterContent.toLowerCase();
        
        Set<String> itemWords = Set.of(itemLower.split("\\s+"));
        Set<String> clusterWords = Set.of(clusterLower.split("\\s+"));
        
        long commonWords = itemWords.stream()
                .filter(clusterWords::contains)
                .count();
        
        double similarity = (double) commonWords / Math.max(itemWords.size(), clusterWords.size());
        
        return similarity > threshold;
    }

    /**
     * Formats cluster summary for LLM prompt
     */
    protected String formatClusterSummary(List<?> clusters, String clusterType) {
        StringBuilder summary = new StringBuilder();
        
        for (int i = 0; i < clusters.size(); i++) {
            summary.append(String.format("Cluster %d (%s):\n", i + 1, clusterType));
            summary.append(clusters.get(i).toString());
            summary.append("\n\n");
        }
        
        return summary.toString();
    }

    /**
     * Formats cluster analysis for LLM prompt
     */
    protected String formatClusterAnalysis(List<?> clusters, String clusterType) {
        if (clusters.isEmpty()) {
            return "No clusters found.";
        }
        
        StringBuilder analysis = new StringBuilder();
        analysis.append(String.format("Total %s clusters: %d\n", clusterType, clusters.size()));
        
        for (int i = 0; i < clusters.size(); i++) {
            analysis.append(String.format("- Cluster %d: %s\n", i + 1, clusterType));
        }
        
        return analysis.toString();
    }

    /**
     * Trunca el contenido antes de enviarlo a un prompt LLM para evitar desbordar
     * el contexto. Conserva cabecera y pie para mantener señal relevante.
     */
    protected String truncateForPrompt(String content, int maxChars) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }

        int head = (int) (maxChars * 0.65); // mantener más cabecera
        int tail = maxChars - head;
        String truncated = trimmed.substring(0, head) + "\n...\n" + trimmed.substring(trimmed.length() - tail);
        log().info("Prompt content truncated from {} to {} characters", trimmed.length(), truncated.length());
        return truncated;
    }
}


