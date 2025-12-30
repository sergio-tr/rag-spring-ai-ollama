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
                            .strip();

                    // Use validateLLMFilterResponse for consistent validation
                    Boolean validated = validateLLMFilterResponse(result, "NER-based metadata matching");
                    if (validated != null && validated) {
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
                    .strip();

            // Use validateLLMFilterResponse for consistent validation
            Boolean validated = validateLLMFilterResponse(result, "semantic content matching");
            return validated != null && validated;
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
     * Validates LLM filter response (yes/no/unknown) using another LLM call.
     * On error or invalid response, returns null to indicate "unknown" (should not filter).
     * 
     */
    private Boolean validateLLMFilterResponse(String response, String context) {
        if (response == null || response.trim().isEmpty()) {
            log().warn("Empty LLM response in {}", context);
            return null; // Unknown, don't filter
        }
        
        // Use LLM to interpret the response as yes/no/unknown
        String prompt = String.format("""
            Context: %s
            
            The LLM generated this response: "%s"
            
            Task: Interpret this response as a boolean answer.
            - If it means YES/TRUE/POSITIVE/MATCH/RELEVANT, respond with: YES
            - If it means NO/FALSE/NEGATIVE/NO_MATCH/IRRELEVANT, respond with: NO
            - If it's UNCLEAR/AMBIGUOUS/UNCERTAIN, respond with: UNKNOWN
            
            Consider semantic meaning, not just exact words.
            For ambiguous responses (probably, maybe, possibly), respond with: UNKNOWN
            
            Respond with ONLY one word: YES, NO, or UNKNOWN.
            """, context, response);
        
        try {
            String interpretation = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip()
                    .toUpperCase();
            
            if (interpretation.contains("YES")) {
                return true;
            } else if (interpretation.contains("NO")) {
                return false;
            } else {
                // UNKNOWN or unclear - don't filter (default to keeping the document)
                log().debug("LLM interpreted response in {} as UNKNOWN: '{}'", context, response);
                return null;
            }
        } catch (Exception e) {
            log().warn("Error validating LLM filter response in {}, defaulting to keep document", context, e);
            return null; // Unknown, don't filter
        }
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
            return validated != null ? validated : true;
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
                    .strip();
            
            // Use validateLLMFilterResponse for consistent validation
            Boolean validated = validateLLMFilterResponse(result, "minute matching with NER");
            return validated != null ? validated : true; // Default to true to avoid false negatives
        } catch (Exception e) {
            log().warn("Error matching minute with NER, defaulting to true to avoid false negatives", e);
            return true; // Avoid false negatives (consistent with EnhancedNERHandler)
        }
    }
    
    /**
     * Checks if minute's agenda items match NER agenda items using LLM.
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
            
            // Build agenda items list from minute
            List<String> minuteAgendaItems = minute.agenda().values().stream()
                    .filter(item -> item != null && !item.trim().isEmpty())
                    .collect(Collectors.toList());
            
            if (minuteAgendaItems.isEmpty()) {
                return true; // No agenda content to search
            }
            
            // Build NER agenda items list
            List<String> nerAgendaItems = new ArrayList<>();
            for (int i = 0; i < nerAgenda.length(); i++) {
                String item = nerAgenda.getString(i).trim();
                if (!item.isEmpty()) {
                    nerAgendaItems.add(item);
                }
            }
            
            if (nerAgendaItems.isEmpty()) {
                return true; // No NER agenda items to match
            }
            
            // Use LLM to check if any NER agenda item semantically matches any minute agenda item
            String prompt = String.format("""
                Task: Check if any of the NER agenda items semantically match any of the minute agenda items.
                
                NER agenda items (extracted from query):
                %s
                
                Minute agenda items:
                %s
                
                Consider semantic meaning, synonyms, and related concepts, not just exact word matches.
                
                Respond with ONLY one word: YES if at least one match is found, NO otherwise.
                """, String.join(", ", nerAgendaItems), String.join(", ", minuteAgendaItems));
            
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip();
            
            // Use validateLLMFilterResponse for consistent validation
            Boolean validated = validateLLMFilterResponse(result, "agenda items matching");
            boolean matches = validated != null ? validated : true; // Default to true to avoid false negatives
            
            if (matches) {
                log().info("Found matching agenda items. NER agenda: {}, Minute agenda: {}", 
                           nerAgendaItems, minuteAgendaItems);
            } else {
                log().info("No matching agenda items found. NER agenda: {}, Minute agenda: {}", 
                           nerAgendaItems, minuteAgendaItems);
            }
            
            return matches;
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
            
            // Build entity lists
            List<String> nerEntityList = new ArrayList<>();
            for (int i = 0; i < nerEntities.length(); i++) {
                String entity = nerEntities.getString(i).trim();
                if (!entity.isEmpty()) {
                    nerEntityList.add(entity);
                }
            }
            
            if (nerEntityList.isEmpty()) {
                return true; // No NER entities to match
            }
            
            List<String> minuteEntityList = minute.mentionedEntities().stream()
                    .filter(e -> e != null && !e.trim().isEmpty())
                    .collect(Collectors.toList());
            
            if (minuteEntityList.isEmpty()) {
                return false; // No minute entities to match against
            }
            
            // Use LLM to check if any NER entity semantically matches any minute entity
            String prompt = String.format("""
                Task: Check if any of the NER entities semantically match any of the minute entities.
                
                NER entities (extracted from query):
                %s
                
                Minute entities:
                %s
                
                Consider semantic meaning, synonyms, variations, and related names, not just exact word matches.
                For example, "Juan Pérez" should match "Juan Pérez Gutiérrez", and vice versa.
                
                Respond with ONLY one word: YES if at least one match is found, NO otherwise.
                """, String.join(", ", nerEntityList), String.join(", ", minuteEntityList));
            
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip();
            
            // Use validateLLMFilterResponse for consistent validation
            Boolean validated = validateLLMFilterResponse(result, "entities matching");
            boolean matches = validated != null ? validated : false; // Default to false for entity matching
            
            if (matches) {
                log().info("Found matching entities. NER entities: {}, Minute entities: {}", 
                           nerEntityList, minuteEntityList);
            } else {
                log().info("No matching entities found. NER entities: {}, Minute entities: {}", 
                           nerEntityList, minuteEntityList);
            }
            
            return matches;
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
     * Field name synonyms mapping for flexible field search.
     */
    private static final Map<String, String> FIELD_SYNONYMS = Map.ofEntries(
        Map.entry("secretaria", "secretary"),
        Map.entry("secretario", "secretary"),
        Map.entry("orden del día", "agenda"),
        Map.entry("orden_del_dia", "agenda"),
        Map.entry("order_of_day", "agenda"),
        Map.entry("puntos del día", "agenda"),
        Map.entry("puntos_del_dia", "agenda"),
        Map.entry("fecha", "date"),
        Map.entry("lugar", "place"),
        Map.entry("ubicación", "place"),
        Map.entry("presidente", "president"),
        Map.entry("hora_inicio", "startTime"),
        Map.entry("hora de inicio", "startTime"),
        Map.entry("hora_fin", "endTime"),
        Map.entry("hora de fin", "endTime"),
        Map.entry("temas", "topics"),
        Map.entry("decisiones", "decisions"),
        Map.entry("acuerdos", "decisions"),
        Map.entry("resumen", "summary"),
        Map.entry("asistentes", "attendees"),
        Map.entry("participantes", "attendees")
    );

    /**
     * Normalizes field name using synonyms mapping and returns canonical field name.
     * 
     * @param field Field name (may be in any language or format)
     * @return Canonical field name or original if no mapping found
     */
    private String normalizeFieldName(String field) {
        if (field == null || field.trim().isEmpty()) {
            return field;
        }
        
        String fieldLower = field.toLowerCase().trim();
        
        // Check direct mapping
        if (FIELD_SYNONYMS.containsKey(fieldLower)) {
            String canonical = FIELD_SYNONYMS.get(fieldLower);
            log().debug("Mapped field synonym '{}' to canonical '{}'", field, canonical);
            return canonical;
        }
        
        // Check if field contains any synonym as substring
        for (Map.Entry<String, String> entry : FIELD_SYNONYMS.entrySet()) {
            if (fieldLower.contains(entry.getKey())) {
                String canonical = entry.getValue();
                log().debug("Mapped field '{}' containing synonym '{}' to canonical '{}'", field, entry.getKey(), canonical);
                return canonical;
            }
        }
        
        return fieldLower;
    }

    /**
     * Gets field value from Minute object, searching in multiple possible field names.
     * Uses synonym mapping and tries multiple variations.
     * 
     * @param minute Minute object
     * @param field Field name (may be in any language or format)
     * @return Field value or null if not found
     */
    private Object getMinuteFieldValue(Minute minute, String field) {
        if (minute == null || field == null || field.trim().isEmpty()) {
            return null;
        }
        
        // Normalize field name using synonyms
        String normalizedField = normalizeFieldName(field);
        
        // Try to get value using normalized field name
        Object value = getFieldValueByCanonicalName(minute, normalizedField);
        if (value != null) {
            log().debug("Found field '{}' (normalized: '{}') in minute", field, normalizedField);
            return value;
        }
        
        // Try original field name as fallback
        value = getFieldValueByCanonicalName(minute, field.toLowerCase());
        if (value != null) {
            log().debug("Found field '{}' using original name in minute", field);
            return value;
        }
        
        log().debug("Field '{}' (normalized: '{}') not found in minute", field, normalizedField);
        return null;
    }

    /**
     * Gets field value by canonical field name.
     * 
     * @param minute Minute object
     * @param canonicalField Canonical field name (lowercase, normalized)
     * @return Field value or null if not found
     */
    private Object getFieldValueByCanonicalName(Minute minute, String canonicalField) {
        return switch (canonicalField) {
            case "date" -> minute.date();
            case "place" -> minute.place();
            case "president" -> minute.president();
            case "secretary" -> minute.secretary();
            case "starttime" -> minute.startTime();
            case "endtime" -> minute.endTime();
            case "topics" -> minute.topics();
            case "decisions" -> minute.decisions();
            case "summary" -> minute.summary();
            case "agenda", "orden_del_dia", "order_of_day" -> {
                // Agenda is a Map<String, String>, convert to readable format
                Map<String, String> agenda = minute.agenda();
                if (agenda == null || agenda.isEmpty()) {
                    yield null;
                }
                // Convert map to list of "key: value" strings for better readability
                yield agenda.entrySet().stream()
                        .map(e -> e.getKey() + ": " + e.getValue())
                        .collect(Collectors.joining("; "));
            }
            case "attendees" -> minute.attendees();
            case "numberofattendees", "attendeescount" -> minute.numberOfAttendees();
            case "durationminutes", "duration" -> {
                int duration = calculateDurationFromMinute(minute);
                yield duration > 0 ? duration : null;
            }
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
     * Enhanced to match parseDateFlexible for consistency across the system.
     * Always tries ISO format first for better performance.
     * Note: This method is kept for backward compatibility but parseDateFlexible should be preferred.
     */
    private LocalDate parseDateToLocalDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        
        // Normalize to lowercase to handle case variations (e.g., "Agosto" vs "agosto")
        String v = dateStr.trim().toLowerCase();
        
        // Try ISO format first (most common after normalization)
        try {
            return LocalDate.parse(v, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
        }
        
        // Try Spanish formats with quotes
        List<DateTimeFormatter> formatters = Arrays.asList(
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
            DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
            // Spanish formats without quotes
            DateTimeFormatter.ofPattern("d de MMMM de yyyy", Locale.forLanguageTag("es")),
            DateTimeFormatter.ofPattern("dd de MMMM de yyyy", Locale.forLanguageTag("es")),
            // Abbreviated month names
            DateTimeFormatter.ofPattern("d 'de' MMM 'de' yyyy", Locale.forLanguageTag("es")),
            DateTimeFormatter.ofPattern("dd 'de' MMM 'de' yyyy", Locale.forLanguageTag("es")),
            DateTimeFormatter.ofPattern("d de MMM de yyyy", Locale.forLanguageTag("es")),
            DateTimeFormatter.ofPattern("dd de MMM de yyyy", Locale.forLanguageTag("es")),
            // Without "de" between day and month
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("es")),
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.forLanguageTag("es")),
            // Numeric formats
            DateTimeFormatter.ofPattern("d/M/yyyy"),
            DateTimeFormatter.ofPattern("dd/MM/yyyy"),
            DateTimeFormatter.ofPattern("d-M-yyyy"),
            DateTimeFormatter.ofPattern("dd-MM-yyyy"),
            DateTimeFormatter.ofPattern("yyyy/MM/dd"),
            DateTimeFormatter.ofPattern("yyyy.MM.dd"),
            // With day of the week
            DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
            DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy", Locale.ENGLISH)
        );
        
        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDate.parse(v, formatter);
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
     * Analyzes query type to generate more specific prompts using LLM.
     */
    protected String analyzeQueryType(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "general";
        }
        
        String prompt = String.format("""
            Analyze this user query and determine its primary focus:
            
            Query: "%s"
            
            Possible query types:
            - decision-focused: about decisions, agreements, approvals
            - topic-focused: about topics, discussions, subjects
            - person-focused: about people, president, secretary, attendees
            - date-focused: about dates, when something happened
            - location-focused: about places, where something happened
            - entity-focused: about entities, persons, organizations
            - count-focused: about counting, how many
            - comparison-focused: about comparing, differences
            - general: general query without specific focus
            
            Respond with ONLY the query type (e.g., "decision-focused", "topic-focused", etc.).
            If unsure, respond with "general".
            """, query);
        
        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip()
                    .toLowerCase();
            
            // Validate and return the result
            if (result.contains("decision")) {
                return "decision-focused";
            } else if (result.contains("topic")) {
                return "topic-focused";
            } else if (result.contains("person")) {
                return "person-focused";
            } else if (result.contains("date")) {
                return "date-focused";
            } else if (result.contains("location") || result.contains("place")) {
                return "location-focused";
            } else if (result.contains("entity")) {
                return "entity-focused";
            } else if (result.contains("count")) {
                return "count-focused";
            } else if (result.contains("comparison") || result.contains("compare")) {
                return "comparison-focused";
            } else {
                return "general";
            }
        } catch (Exception e) {
            log().warn("Error analyzing query type, defaulting to general", e);
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
        
        // Retry logic for transient errors
        int maxRetries = 2;
        Exception lastException = null;
        
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    log().debug("Retry attempt {} for LLM call", attempt);
                    Thread.sleep(500 * attempt); // Exponential backoff
                }
                
                String response = chatClient
                        .prompt()
                        .user(prompt)
                        .call()
                        .content();
                
                if (response == null || response.trim().isEmpty()) {
                    log().warn("Empty response from LLM in getLLMResponseCached (attempt {})", attempt + 1);
                    if (attempt < maxRetries) {
                        continue; // Retry on empty response
                    }
                    return "";
                }
                
                return response.strip();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log().error("Thread interrupted in getLLMResponseCached", e);
                return "";
            } catch (NullPointerException e) {
                lastException = e;
                log().error("NullPointerException in getLLMResponseCached (attempt {}): {}", attempt + 1, e.getMessage(), e);
                // Don't retry on NPE, likely a code issue
                return "";
            } catch (IllegalArgumentException e) {
                lastException = e;
                log().error("IllegalArgumentException in getLLMResponseCached (attempt {}): {}", attempt + 1, e.getMessage(), e);
                // Don't retry on illegal argument, likely a code issue
                return "";
            } catch (Exception e) {
                lastException = e;
                String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
                String className = e.getClass().getName();
                
                // Check if it's a timeout-related error (even if not explicitly TimeoutException)
                boolean isTimeout = errorMsg.contains("timeout") || 
                                  errorMsg.contains("timed out") ||
                                  className.contains("Timeout");
                
                // Check if it's a network-related error
                boolean isNetworkError = errorMsg.contains("connection") ||
                                       errorMsg.contains("network") ||
                                       errorMsg.contains("socket") ||
                                       className.contains("Connection") ||
                                       className.contains("Network");
                
                if (isTimeout || isNetworkError) {
                    log().warn("Timeout/network error in getLLMResponseCached (attempt {}): {}", attempt + 1, e.getMessage());
                } else {
                    log().error("Error in getLLMResponseCached (attempt {}): {}", attempt + 1, e.getMessage(), e);
                }
                
                // Retry on timeout/network errors or other retryable exceptions
                if (attempt < maxRetries && (isTimeout || isNetworkError || isRetryableException(e))) {
                    continue;
                }
            }
        }
        
        // All retries failed
        if (lastException != null) {
            log().error("Failed to get LLM response after {} attempts. Last error: {}", 
                       maxRetries + 1, lastException.getMessage(), lastException);
        }
        return "";
    }
    
    /**
     * Determines if an exception is retryable.
     * PHASE 10: Helper method to identify retryable exceptions.
     */
    private boolean isRetryableException(Throwable e) {
        if (e == null) {
            return false;
        }
        
        String errorMsg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        String className = e.getClass().getName().toLowerCase();
        
        // Retry on network-related errors
        if (className.contains("timeout") || 
            className.contains("connection") ||
            className.contains("network") ||
            errorMsg.contains("timeout") ||
            errorMsg.contains("connection") ||
            errorMsg.contains("network")) {
            return true;
        }
        
        // Don't retry on programming errors
        if (e instanceof NullPointerException ||
            e instanceof IllegalArgumentException ||
            e instanceof IllegalStateException) {
            return false;
        }
        
        // Default: retry on other exceptions (conservative approach)
        return true;
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

        // Documents are already grouped and combined by the retriever
        log().info("Retrieved {} documents ({} total retrieved, {} after metadata filter)",
                metadataDocs.size(), docs.size(), metadataDocs.size());
        return metadataDocs;
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

                    // Documents are already grouped and combined by the retriever

                    if (!docs.isEmpty()) {
                        log().info("Retrieved {} documents using NER-based retrieval with metadata filters", docs.size());
                        // Validate date match if date is present in query
                        docs = validateDateMatchIfPresent(docs, query, ner);
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
        
        // Fallback 2: If still empty, log and continue (will return empty list, not null)
        if (docs.isEmpty()) {
            log().info("Basic retrieval returned no documents after metadata filter for query: {}", query);
        }
        
        // Validate date match if date is present in query
        docs = validateDateMatchIfPresent(docs, query, ner);
        
        // Always return a list (never null), even if empty
        return docs != null ? docs : new ArrayList<>();
    }

    /**
     * Filters documents by topic/keyword using LLM semantic matching.
     * Searches for the topic in metadata fields (topics, summary, decisions) and content.
     * 
     * @param docs List of documents to filter
     * @param topic Topic or keyword to search for
     * @return Filtered list of documents that mention the topic
     */
    protected List<Document> filterDocumentsByTopic(List<Document> docs, String topic) {
        if (docs == null || docs.isEmpty() || topic == null || topic.trim().isEmpty()) {
            return docs != null ? docs : new ArrayList<>();
        }
        
        log().info("Filtering {} documents by topic: {}", docs.size(), topic);
        
        List<Document> filtered = new ArrayList<>();
        
        for (Document doc : docs) {
            if (doc == null) continue;
            
            // Build context from metadata and content
            StringBuilder context = new StringBuilder();
            
            // Add metadata fields
            Map<String, Object> metadata = doc.getMetadata();
            if (metadata != null) {
                if (metadata.containsKey("topics")) {
                    Object topicsObj = metadata.get("topics");
                    if (topicsObj instanceof List) {
                        context.append("Topics: ").append(String.join(", ", (List<String>) topicsObj)).append("\n");
                    } else if (topicsObj instanceof String) {
                        context.append("Topics: ").append(topicsObj).append("\n");
                    }
                }
                if (metadata.containsKey("summary")) {
                    Object summaryObj = metadata.get("summary");
                    if (summaryObj != null) {
                        context.append("Summary: ").append(summaryObj.toString()).append("\n");
                    }
                }
                if (metadata.containsKey("decisions")) {
                    Object decisionsObj = metadata.get("decisions");
                    if (decisionsObj instanceof List) {
                        context.append("Decisions: ").append(String.join(", ", (List<String>) decisionsObj)).append("\n");
                    } else if (decisionsObj instanceof String) {
                        context.append("Decisions: ").append(decisionsObj).append("\n");
                    }
                }
            }
            
            // Add content (truncated for efficiency)
            String content = doc.getContent();
            if (content != null && !content.trim().isEmpty()) {
                String truncatedContent = content.length() > 1000 ? content.substring(0, 1000) + "..." : content;
                context.append("Content: ").append(truncatedContent);
            }
            
            if (context.length() == 0) {
                continue; // Skip documents with no context
            }
            
            // Use LLM to check if document mentions the topic
            String prompt = String.format("""
                Task: Determine if the following document mentions or discusses the topic/keyword.
                
                Topic/Keyword to search for: "%s"
                
                Document information:
                %s
                
                Consider semantic meaning, synonyms, and related concepts, not just exact word matches.
                
                Respond with ONLY one word: YES if the document mentions or discusses the topic, NO otherwise.
                """, topic, context.toString());
            
            try {
                String result = chatClient
                        .prompt()
                        .user(prompt)
                        .call()
                        .content()
                        .strip();
                
                Boolean validated = validateLLMFilterResponse(result, "filterDocumentsByTopic");
                if (validated != null && validated) {
                    filtered.add(doc);
                }
            } catch (Exception e) {
                log().warn("Error filtering document by topic '{}', including document to avoid false negatives: {}", topic, e.getMessage());
                // Include document on error to avoid false negatives
                filtered.add(doc);
            }
        }
        
        log().info("Filtered {} documents by topic '{}': {} documents match", docs.size(), topic, filtered.size());
        
        // Detailed logging for debugging
        if (filtered.isEmpty() && !docs.isEmpty()) {
            log().warn("Topic filtering failed: Topic '{}' not found in any of {} documents", topic, docs.size());
        } else if (!filtered.isEmpty()) {
            log().debug("Topic filtering succeeded: Topic '{}' found in {} documents", topic, filtered.size());
        }
        
        return filtered;
    }

    /**
     * Extracts topic/keyword from query using NER and LLM.
     * 
     * @param query User query
     * @param ner NER entities (optional)
     * @return Extracted topic/keyword or null if not found
     */
    protected String extractTopicFromQuery(String query, JSONObject ner) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }
        
        // Try to extract from NER first
        if (ner != null && !ner.isEmpty()) {
            // Check for topics in NER
            if (ner.has("topics") && !ner.isNull("topics")) {
                try {
                    org.json.JSONArray topics = ner.getJSONArray("topics");
                    if (topics.length() > 0) {
                        String topic = topics.getString(0).trim();
                        if (!topic.isEmpty()) {
                            log().info("Extracted topic from NER: {}", topic);
                            return topic;
                        }
                    }
                } catch (Exception e) {
                    log().debug("Error extracting topic from NER: {}", e.getMessage());
                }
            }
            
            // Check for mentionedEntities that might be topics
            if (ner.has("mentionedEntities") && !ner.isNull("mentionedEntities")) {
                try {
                    org.json.JSONArray entities = ner.getJSONArray("mentionedEntities");
                    if (entities.length() > 0) {
                        // Use first entity as potential topic
                        String entity = entities.getString(0).trim();
                        if (!entity.isEmpty()) {
                            log().info("Extracted potential topic from NER entities: {}", entity);
                            return entity;
                        }
                    }
                } catch (Exception e) {
                    log().debug("Error extracting topic from NER entities: {}", e.getMessage());
                }
            }
        }
        
        // Use LLM to extract topic/keyword from query
        String prompt = String.format("""
            Task: Extract the main topic or keyword from the following question about meeting minutes.
            
            Question (may be in any language): "%s"
            
            Extract the main topic, keyword, or subject that the question is asking about.
            Examples:
            - "How many meetings discussed the elevator?" → topic: "elevator"
            - "¿Cuántas actas hay sobre el ascensor?" → topic: "ascensor" or "elevator"
            - "How many meetings mentioned the budget?" → topic: "budget"
            - "¿En cuántas reuniones se habló del presupuesto?" → topic: "presupuesto" or "budget"
            
            If the question is asking about a count without a specific topic (e.g., "How many meetings were there?"),
            respond with "NONE".
            
            Return ONLY the topic/keyword, or "NONE" if no specific topic is mentioned.
            Do not include explanations or additional text.
            """, query);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip();
            
            if (response != null && !response.trim().isEmpty() && !response.trim().equalsIgnoreCase("NONE")) {
                String topic = response.trim();
                log().info("Extracted topic from query using LLM: {}", topic);
                return topic;
            }
        } catch (Exception e) {
            log().warn("Error extracting topic from query using LLM: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * Validates that a keyword actually exists in the documents using LLM semantic matching.
     * 
     * @param docs List of documents to search
     * @param keyword Keyword to validate
     * @return true if keyword is found in at least one document, false otherwise
     */
    protected boolean validateKeywordExists(List<Document> docs, String keyword) {
        if (docs == null || docs.isEmpty() || keyword == null || keyword.trim().isEmpty()) {
            return false;
        }
        
        log().info("Validating keyword '{}' exists in {} documents", keyword, docs.size());
        
        // Check a sample of documents (first 5) to avoid too many LLM calls
        int sampleSize = Math.min(5, docs.size());
        List<Document> sampleDocs = docs.subList(0, sampleSize);
        
        for (Document doc : sampleDocs) {
            if (doc == null) continue;
            
            // Build context from metadata and content
            StringBuilder context = new StringBuilder();
            
            Map<String, Object> metadata = doc.getMetadata();
            if (metadata != null) {
                if (metadata.containsKey("topics")) {
                    Object topicsObj = metadata.get("topics");
                    if (topicsObj instanceof List) {
                        context.append("Topics: ").append(String.join(", ", (List<String>) topicsObj)).append("\n");
                    } else if (topicsObj instanceof String) {
                        context.append("Topics: ").append(topicsObj).append("\n");
                    }
                }
                if (metadata.containsKey("summary")) {
                    Object summaryObj = metadata.get("summary");
                    if (summaryObj != null) {
                        context.append("Summary: ").append(summaryObj.toString()).append("\n");
                    }
                }
                if (metadata.containsKey("decisions")) {
                    Object decisionsObj = metadata.get("decisions");
                    if (decisionsObj instanceof List) {
                        context.append("Decisions: ").append(String.join(", ", (List<String>) decisionsObj)).append("\n");
                    } else if (decisionsObj instanceof String) {
                        context.append("Decisions: ").append(decisionsObj).append("\n");
                    }
                }
            }
            
            String content = doc.getContent();
            if (content != null && !content.trim().isEmpty()) {
                String truncatedContent = content.length() > 500 ? content.substring(0, 500) + "..." : content;
                context.append("Content: ").append(truncatedContent);
            }
            
            if (context.length() == 0) {
                continue;
            }
            
            // Use LLM to check if keyword exists
            String prompt = String.format("""
                Task: Determine if the following document mentions or contains the keyword.
                
                Keyword to search for: "%s"
                
                Document information:
                %s
                
                Consider semantic meaning, synonyms, and related concepts, not just exact word matches.
                
                Respond with ONLY one word: YES if the keyword is mentioned or discussed, NO otherwise.
                """, keyword, context.toString());
            
            try {
                String result = chatClient
                        .prompt()
                        .user(prompt)
                        .call()
                        .content()
                        .strip();
                
                Boolean validated = validateLLMFilterResponse(result, "validateKeywordExists");
                if (validated != null && validated) {
                    log().info("Keyword '{}' found in document", keyword);
                    return true;
                }
            } catch (Exception e) {
                log().warn("Error validating keyword '{}' in document: {}", keyword, e.getMessage());
            }
        }
        
        log().info("Keyword '{}' not found in sampled documents (checked {} of {} documents)", keyword, sampleSize, docs.size());
        return false;
    }

    /**
     * Extracts year from query using NER or LLM.
     * 
     * @param query User query
     * @param ner NER entities (optional)
     * @return Extracted year as string (e.g., "2025") or null if not found
     */
    protected String extractYearFromQuery(String query, JSONObject ner) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }
        
        // Try to extract from NER first
        if (ner != null && !ner.isEmpty()) {
            try {
                if (ner.has("filters") && !ner.isNull("filters")) {
                    JSONObject filters = ner.getJSONObject("filters");
                    if (filters.has("date") && !filters.isNull("date")) {
                        org.json.JSONArray dates = filters.getJSONArray("date");
                        for (int i = 0; i < dates.length(); i++) {
                            String dateStr = dates.getString(i);
                            // Try to extract year from date string
                            java.util.regex.Pattern yearPattern = java.util.regex.Pattern.compile("\\b(20\\d{2})\\b");
                            java.util.regex.Matcher matcher = yearPattern.matcher(dateStr);
                            if (matcher.find()) {
                                String year = matcher.group(1);
                                log().info("Extracted year from NER: {}", year);
                                return year;
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log().debug("Error extracting year from NER: {}", e.getMessage());
            }
        }
        
        // Use LLM to extract year
        String prompt = String.format("""
            Task: Extract the year from the following question about meeting minutes.
            
            Question (may be in any language): "%s"
            
            Extract the year (e.g., 2025, 2026) if mentioned in the question.
            If no year is mentioned, respond with "NONE".
            
            Return ONLY the year (e.g., "2025") or "NONE".
            Do not include explanations or additional text.
            """, query);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip();
            
            if (response != null && !response.trim().isEmpty() && !response.trim().equalsIgnoreCase("NONE")) {
                // Validate it's a year (4 digits starting with 20)
                if (response.matches("20\\d{2}")) {
                    log().info("Extracted year from query using LLM: {}", response);
                    return response;
                }
            }
        } catch (Exception e) {
            log().warn("Error extracting year from query using LLM: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * Filters documents by year using date metadata.
     * 
     * @param docs List of documents to filter
     * @param year Year to filter by (e.g., "2025")
     * @return Filtered list of documents from the specified year
     */
    protected List<Document> filterDocumentsByYear(List<Document> docs, String year) {
        if (docs == null || docs.isEmpty() || year == null || year.trim().isEmpty()) {
            return docs != null ? docs : new ArrayList<>();
        }
        
        log().info("Filtering {} documents by year: {}", docs.size(), year);
        
        List<Document> filtered = new ArrayList<>();
        
        for (Document doc : docs) {
            if (doc == null) continue;
            
            String docDate = getDocumentDate(doc);
            if (docDate != null) {
                // Check if document date contains the year
                if (docDate.contains(year)) {
                    filtered.add(doc);
                } else {
                    // Try parsing the date to extract year
                    try {
                        java.time.LocalDate parsedDate = parseDateFlexible(docDate);
                        if (parsedDate != null) {
                            String docYear = String.valueOf(parsedDate.getYear());
                            if (docYear.equals(year)) {
                                filtered.add(doc);
                            }
                        }
                    } catch (Exception e) {
                        log().debug("Error parsing date '{}' for year filtering: {}", docDate, e.getMessage());
                    }
                }
            }
        }
        
        log().info("Filtered {} documents by year '{}': {} documents match", docs.size(), year, filtered.size());
        
        // Detailed logging for debugging
        if (filtered.isEmpty() && !docs.isEmpty()) {
            log().warn("Year filtering failed: Year '{}' not found in any of {} documents. Sample document dates: {}", 
                      year, docs.size(),
                      docs.stream()
                          .limit(3)
                          .map(this::getDocumentDate)
                          .filter(Objects::nonNull)
                          .collect(Collectors.joining(", ")));
        } else if (!filtered.isEmpty()) {
            log().debug("Year filtering succeeded: Year '{}' found in {} documents", year, filtered.size());
        }
        
        return filtered;
    }

    /**
     * Validates date match if a date is present in the query.
     * Returns filtered documents or empty list if no documents match the date.
     */
    private List<Document> validateDateMatchIfPresent(List<Document> docs, String query, JSONObject ner) {
        if (docs == null || docs.isEmpty()) {
            return docs != null ? docs : new ArrayList<>();
        }

        // Extract date from query
        String requestedDate = extractDateFromQuery(query, ner);
        if (requestedDate == null) {
            // No date in query, return all documents
            return docs;
        }

        // Validate date match
        List<Document> filtered = validateDateMatch(docs, requestedDate);
        
        if (filtered.isEmpty() && !docs.isEmpty()) {
            log().warn("Date validation filtered out all {} documents for requested date: {}", 
                      docs.size(), requestedDate);
        }

        return filtered;
    }
    
    /**
     * Overloaded method for backward compatibility (without NER).
     * Delegates to the main method with null NER.
     */
    protected List<Document> retrieveDocumentsWithFallback(String query, String[] relevantFields) {
        return retrieveDocumentsWithFallback(query, relevantFields, null);
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
     * Uses a simple prompt to LLM to generate message in correct language.
     */
    private String generateFallbackNotFoundMessage(String query) {
        if (query == null || query.trim().isEmpty()) {
            return "No relevant meeting minutes were found for this query.";
        }
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question, 
            indicating that no relevant meeting minutes were found.
            Be concise and polite.
            """, query);
        
        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating fallback not found message with LLM", e);
        }
        
        // Ultimate fallback - generic message
        return "No relevant meeting minutes were found for this query.";
    }

    /**
     * Generates a specific error message with contextual information.
     * PHASE 9: Enhanced error messages with field, date, and document count information.
     * 
     * @param query The user query
     * @param field The field that was requested (e.g., "president", "agenda", "duration")
     * @param date The date that was searched (if applicable, can be null)
     * @param documentsReviewed Number of documents that were reviewed
     * @param reason The reason for the error (e.g., "field_not_found", "date_not_found", "no_matching_documents")
     * @return A specific error message in the same language as the query
     */
    protected String generateSpecificErrorMessage(String query, String field, String date, int documentsReviewed, String reason) {
        if (query == null || query.trim().isEmpty()) {
            return generateFallbackNotFoundMessage("");
        }
        
        StringBuilder contextInfo = new StringBuilder();
        if (field != null && !field.trim().isEmpty()) {
            contextInfo.append("Field requested: ").append(field).append(". ");
        }
        if (date != null && !date.trim().isEmpty()) {
            contextInfo.append("Date searched: ").append(date).append(". ");
        }
        if (documentsReviewed > 0) {
            contextInfo.append("Documents reviewed: ").append(documentsReviewed).append(". ");
        }
        if (reason != null && !reason.trim().isEmpty()) {
            contextInfo.append("Reason: ").append(reason).append(".");
        }
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Context information:
            %s
            
            Write a short, helpful error message in the same language as the query that explains:
            - What was searched for
            - Why it wasn't found (if applicable)
            - Be specific but concise
            - Do not include technical details like "documents reviewed" or "field requested"
            - Focus on what the user asked for and why it couldn't be found
            
            Examples:
            - If field is "president" and date is provided: "No se encontró información sobre el presidente para la fecha [date]" (Spanish) or "No president information found for the date [date]" (English)
            - If field is "agenda": "No se encontró el orden del día para la fecha especificada" (Spanish) or "No agenda found for the specified date" (English)
            - If date is not found: "No se encontraron actas para la fecha [date]" (Spanish) or "No meeting minutes found for the date [date]" (English)
            """, query, contextInfo.toString());
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateSpecificErrorMessage, using fallback");
                return generateFallbackSpecificErrorMessage(query, field, date);
            }
            
            return response.trim();
        } catch (Exception e) {
            log().error("Error generating specific error message, using fallback", e);
            return generateFallbackSpecificErrorMessage(query, field, date);
        }
    }
    
    /**
     * Generates a fallback specific error message when LLM fails.
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackSpecificErrorMessage(String query, String field, String date) {
        if (query == null || query.trim().isEmpty()) {
            return "No relevant meeting minutes were found for this query.";
        }
        
        StringBuilder context = new StringBuilder();
        if (field != null && !field.trim().isEmpty()) {
            context.append("Field: ").append(field).append(". ");
        }
        if (date != null && !date.trim().isEmpty()) {
            context.append("Date: ").append(date).append(". ");
        }
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Context: %s
            
            Respond with a short error message in the EXACT SAME LANGUAGE as the question,
            explaining that the requested information was not found.
            Be concise and helpful.
            """, query, context.toString());
        
        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating fallback specific error message with LLM", e);
        }
        
        // Ultimate fallback - generic message
        return "No relevant meeting minutes were found for this query.";
    }

    /**
     * Generates a date not found error message using LLM.
     * Ensures response language matches query language.
     */
    protected String generateDateNotFoundMessage(String query, String date) {
        if (query == null || query.trim().isEmpty()) {
            return "No meeting minutes found for the specified date.";
        }
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            The requested date is: %s
            
            Respond with a short, clear message in the EXACT SAME LANGUAGE as the question,
            indicating that no meeting minutes were found for this date.
            Be concise and helpful.
            Do not repeat the question.
            """, query, date != null ? date : "unknown");
        
        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating date not found message with LLM, using fallback", e);
        }
        
        // Fallback
        return generateFallbackNotFoundMessage(query);
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

    /**
     * Parses a date string flexibly, supporting multiple formats including full Spanish dates.
     * This is an improved version that handles more date formats than parseDateToLocalDate.
     * 
     */
    protected LocalDate parseDateFlexible(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }
        // Normalize to lowercase to handle case variations (e.g., "Agosto" vs "agosto")
        String v = dateStr.trim().toLowerCase();

        // Try ISO first (most common after LLM normalization)
        try {
            return LocalDate.parse(v, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
        }

        // Try existing parseDateToLocalDate formatters
        LocalDate result = parseDateToLocalDate(v);
        if (result != null) {
            return result;
        }

        // Enhanced Spanish formats with different capitalization and variations
        List<DateTimeFormatter> spanishFormatters = Arrays.asList(
            // Full month names
            DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
            DateTimeFormatter.ofPattern("dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
            DateTimeFormatter.ofPattern("d de MMMM de yyyy", Locale.forLanguageTag("es")), // Without quotes
            DateTimeFormatter.ofPattern("dd de MMMM de yyyy", Locale.forLanguageTag("es")), // Without quotes
            // Abbreviated month names
            DateTimeFormatter.ofPattern("d 'de' MMM 'de' yyyy", Locale.forLanguageTag("es")),
            DateTimeFormatter.ofPattern("dd 'de' MMM 'de' yyyy", Locale.forLanguageTag("es")),
            DateTimeFormatter.ofPattern("d de MMM de yyyy", Locale.forLanguageTag("es")), // Without quotes
            DateTimeFormatter.ofPattern("dd de MMM de yyyy", Locale.forLanguageTag("es")), // Without quotes
            // Without "de" between day and month
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.forLanguageTag("es")),
            DateTimeFormatter.ofPattern("dd MMMM yyyy", Locale.forLanguageTag("es")),
            // With comma (e.g., "Lunes, 25 de agosto de 2025")
            DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
            DateTimeFormatter.ofPattern("EEEE, dd 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es"))
        );

        for (DateTimeFormatter formatter : spanishFormatters) {
            try {
                return LocalDate.parse(v, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        // Additional formats: dd-MM-yyyy, dd.MM.yyyy, dd/MM/yyyy (already in parseDateToLocalDate but try again with different separators)
        List<DateTimeFormatter> additionalFormatters = Arrays.asList(
            DateTimeFormatter.ofPattern("d-M-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d.M.yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd.MM.yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("d/M/yyyy", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH),
            // Year-month-day variations
            DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH),
            DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.ENGLISH)
        );

        for (DateTimeFormatter formatter : additionalFormatters) {
            try {
                return LocalDate.parse(v, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        // If all parsing fails, log for debugging
        log().debug("Could not parse date: {}", dateStr);
        return null;
    }

    /**
     * Extracts date candidates from query and NER entities.
     * Supports multiple date formats and uses LLM for normalization when needed.
     */
    protected List<String> extractDateCandidates(String query, JSONObject ner) {
        List<String> out = new ArrayList<>();

        // From NER (highest priority - most accurate)
        if (ner != null && ner.has("date")) {
            try {
                org.json.JSONArray arr = ner.getJSONArray("date");
                for (int i = 0; i < arr.length(); i++) {
                    String s = arr.optString(i, "").trim();
                    if (!s.isBlank()) {
                        out.add(s);
                        log().debug("Extracted date from NER: {}", s);
                    }
                }
            } catch (Exception e) {
                log().warn("Error extracting dates from NER: {}", e.getMessage());
            }
        }

        // From query (regex patterns) - enhanced with more patterns
        if (query != null) {
            // ISO format: yyyy-MM-dd
            java.util.regex.Matcher m1 = java.util.regex.Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(query);
            while (m1.find()) {
                out.add(m1.group(1));
                log().debug("Extracted ISO date from query: {}", m1.group(1));
            }

            // Slash format: dd/MM/yyyy or d/M/yyyy
            java.util.regex.Matcher m2 = java.util.regex.Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{4})").matcher(query);
            while (m2.find()) {
                out.add(m2.group(1));
                log().debug("Extracted slash date from query: {}", m2.group(1));
            }

            // Dash format: dd-MM-yyyy or d-M-yyyy
            java.util.regex.Matcher m3 = java.util.regex.Pattern.compile("(\\d{1,2}-\\d{1,2}-\\d{4})").matcher(query);
            while (m3.find()) {
                out.add(m3.group(1));
                log().debug("Extracted dash date from query: {}", m3.group(1));
            }

            // Dot format: dd.MM.yyyy or d.M.yyyy
            java.util.regex.Matcher m4 = java.util.regex.Pattern.compile("(\\d{1,2}\\.\\d{1,2}\\.\\d{4})").matcher(query);
            while (m4.find()) {
                out.add(m4.group(1));
                log().debug("Extracted dot date from query: {}", m4.group(1));
            }

            // Spanish format: "d de mes de yyyy" or "dd de mes de yyyy" (case insensitive)
            java.util.regex.Matcher m5 = java.util.regex.Pattern.compile(
                "(\\d{1,2}\\s+de\\s+\\p{L}+\\s+de\\s+\\d{4})", 
                java.util.regex.Pattern.CASE_INSENSITIVE
            ).matcher(query);
            while (m5.find()) {
                out.add(m5.group(1));
                log().debug("Extracted Spanish date from query: {}", m5.group(1));
            }

            // Spanish format without "de" between day and month: "d mes yyyy"
            java.util.regex.Matcher m6 = java.util.regex.Pattern.compile(
                "(\\d{1,2}\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)\\s+\\d{4})",
                java.util.regex.Pattern.CASE_INSENSITIVE
            ).matcher(query);
            while (m6.find()) {
                out.add(m6.group(1));
                log().debug("Extracted Spanish date (no 'de') from query: {}", m6.group(1));
            }
        }

        // If we still have no candidates, try LLM-based normalization (one call)
        if (out.isEmpty() && looksLikeContainsDate(query)) {
            log().debug("No dates found with regex/NER, trying LLM extraction for query: {}", query);
            List<String> llmDates = extractIsoDatesWithLLM(query);
            out.addAll(llmDates);
            if (!llmDates.isEmpty()) {
                log().debug("LLM extracted {} dates: {}", llmDates.size(), llmDates);
            }
        }

        List<String> distinct = out.stream().distinct().collect(Collectors.toList());
        log().info("Extracted {} unique date candidates from query/NER: {}", distinct.size(), distinct);
        return distinct;
    }

    /**
     * Quick heuristic: only call LLM if the query plausibly contains a date.
     */
    private boolean looksLikeContainsDate(String query) {
        if (query == null) return false;
        String q = query.toLowerCase();
        // digits like 2025 or separators
        if (q.matches(".*\\d{4}.*")) return true;
        if (q.contains("/") || q.contains("-")) return true;
        // Spanish month clue
        return q.matches(".*\\b(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)\\b.*");
    }

    /**
     * Uses LLM to extract/normalize dates present in the query to ISO (yyyy-MM-dd).
     * One-shot, strict JSON output.
     */
    private List<String> extractIsoDatesWithLLM(String query) {
        if (query == null || query.trim().isEmpty()) {
            return Collections.emptyList();
        }

        String prompt = String.format("""
            Extract all dates explicitly mentioned in the following text and normalize them to ISO format (yyyy-MM-dd).
            The text may be in Spanish or English and may contain dates in many possible formats.

            Text:
            "%s"

            Output rules:
            - Return ONLY a JSON object with this schema: {"dates":["yyyy-MM-dd", ...]}
            - If no date is present, return {"dates":[]}
            - Do not include any extra keys, explanations, markdown, or surrounding text.
            """, query);

        try {
            String raw = getLLMResponseCached(prompt);
            if (raw == null || raw.trim().isEmpty()) {
                return Collections.emptyList();
            }

            // Try to locate JSON object inside the response
            int start = raw.indexOf('{');
            int end = raw.lastIndexOf('}');
            if (start < 0 || end <= start) {
                return Collections.emptyList();
            }
            String jsonStr = raw.substring(start, end + 1).trim();

            org.json.JSONObject obj = new org.json.JSONObject(jsonStr);
            org.json.JSONArray arr = obj.optJSONArray("dates");
            if (arr == null) return Collections.emptyList();

            List<String> dates = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                String d = arr.optString(i, "").trim();
                if (!d.isBlank()) dates.add(d);
            }

            // Keep only parseable ISO dates
            return dates.stream()
                    .filter(s -> {
                        try {
                            LocalDate.parse(s, DateTimeFormatter.ISO_LOCAL_DATE);
                            return true;
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .distinct()
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log().warn("Failed to extract ISO dates with LLM, falling back to non-LLM parsing: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Filters minutes by date extracted from query/NER.
     * Supports multiple date formats and returns all minutes if no date is found.
     * 
     */
    protected List<Minute> filterMinutesByDate(String query, JSONObject ner, List<Minute> minutes) {
        if (minutes.isEmpty()) {
            return minutes;
        }

        List<String> dateCandidates = extractDateCandidates(query, ner);
        if (dateCandidates.isEmpty()) {
            log().debug("No date candidates found in query/NER, returning all {} minutes", minutes.size());
            return minutes; // No date in query, return all
        }

        // Normalize dates to LocalDate
        List<LocalDate> targetDates = dateCandidates.stream()
                .map(this::parseDateFlexible)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        if (targetDates.isEmpty()) {
            log().warn("Could not parse any date candidates: {}. Returning all minutes.", dateCandidates);
            return minutes; // Can't parse dates, return all
        }

        log().info("Filtering {} minutes by target dates: {}", minutes.size(), targetDates);

        // Filter minutes by date - parse date() field with flexible parsing
        List<Minute> filtered = minutes.stream()
                .filter(minute -> {
                    if (minute.date() == null || minute.date().trim().isEmpty()) {
                        log().debug("Minute {} has no date, skipping date filter", minute.id());
                        return false; // Skip minutes without date when filtering by date
                    }

                    // Try ISO format first (if date is already in ISO format)
                    LocalDate minuteDate = null;
                    try {
                        minuteDate = LocalDate.parse(minute.date(), DateTimeFormatter.ISO_LOCAL_DATE);
                    } catch (DateTimeParseException ignored) {
                        // Not ISO format, try flexible parsing
                    }

                    // If not ISO, use flexible parsing
                    if (minuteDate == null) {
                        minuteDate = parseDateFlexible(minute.date());
                    }

                    if (minuteDate == null) {
                        log().debug("Could not parse date '{}' for minute {}", minute.date(), minute.id());
                        return false; // Can't parse, exclude from results when filtering by date
                    }

                    // Exact match
                    if (targetDates.contains(minuteDate)) {
                        log().debug("Minute {} matched by exact date: {} ({})", 
                                minute.id(), minute.date(), minuteDate);
                        return true;
                    }

                    // Flexible matching: same year and month
                    for (LocalDate targetDate : targetDates) {
                        if (minuteDate.getYear() == targetDate.getYear() && 
                            minuteDate.getMonth() == targetDate.getMonth()) {
                            log().debug("Minute {} matched by year/month: {} ({}) vs {}", 
                                    minute.id(), minute.date(), minuteDate, targetDate);
                            return true;
                        }
                    }

                    return false;
                })
                .collect(Collectors.toList());

        log().info("Filtered {} minutes to {} by date (target dates: {})", 
                minutes.size(), filtered.size(), targetDates);

        // Fallback: if filtering removed all minutes, return original list with warning
        if (filtered.isEmpty() && !minutes.isEmpty()) {
            log().warn("Date filtering removed all minutes! This might indicate a parsing issue. " +
                      "Returning original {} minutes. Query: {}, Target dates: {}", 
                      minutes.size(), query, targetDates);
            return minutes;
        }

        return filtered;
    }

    /**
     * Builds complete minute context for LLM evaluation.
     * Includes all relevant metadata fields in a structured format.
     */
    protected String buildMinuteContext(Minute minute) {
        if (minute == null) {
            return "No meeting minute information available.";
        }

        StringBuilder ctx = new StringBuilder();
        if (minute.date() != null) ctx.append("- Date: ").append(minute.date()).append("\n");
        if (minute.place() != null) ctx.append("- Place: ").append(minute.place()).append("\n");
        if (minute.startTime() != null) ctx.append("- Start time: ").append(minute.startTime()).append("\n");
        if (minute.endTime() != null) ctx.append("- End time: ").append(minute.endTime()).append("\n");
        if (minute.president() != null) ctx.append("- President: ").append(minute.president()).append("\n");
        if (minute.secretary() != null) ctx.append("- Secretary: ").append(minute.secretary()).append("\n");
        if (minute.attendees() != null && !minute.attendees().isEmpty()) {
            ctx.append("- Attendees: ").append(String.join(", ", minute.attendees())).append("\n");
        }
        if (minute.topics() != null && !minute.topics().isEmpty()) {
            ctx.append("- Topics: ").append(String.join(", ", minute.topics())).append("\n");
        }
        if (minute.decisions() != null && !minute.decisions().isEmpty()) {
            ctx.append("- Decisions: ").append(String.join("; ", minute.decisions())).append("\n");
        }
        if (minute.summary() != null && !minute.summary().isBlank()) {
            ctx.append("- Summary: ").append(truncateForPrompt(minute.summary(), 500)).append("\n");
        }
        if (minute.agenda() != null && !minute.agenda().isEmpty()) {
            ctx.append("- Agenda: ").append(minute.agenda().toString()).append("\n");
        }
        return ctx.toString();
    }

    /**
     * Evaluates if a minute contains the information requested in the query.
     * Uses LLM to validate complex conditions before extracting/computing.
     * Useful for validating that a minute matches the query requirements.
     */
    /**
     * Evaluates if a minute contains the requested information.
     * 
     */
    protected boolean evaluateMinuteContainsRequestedInfo(String query, Minute minute) {
        if (query == null || query.trim().isEmpty() || minute == null) {
            return false;
        }

        String minuteContext = buildMinuteContext(minute);

        String prompt = String.format("""
            User query: "%s"
            
            Meeting minute information:
            %s
            
            Does this meeting minute contain the information requested in the user query?
            Specifically, can you extract/answer what the user is asking for from this minute?
            
            IMPORTANT: Be conservative. If you're unsure or if the information might be present, respond YES.
            Only respond NO if you're certain the information is NOT present.
            
            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """, query, minuteContext);

        try {
            String response = getLLMResponseCached(prompt);
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty LLM response in evaluateMinuteContainsRequestedInfo, defaulting to true (less strict)");
                return true; // Less strict: default to true on error
            }
            
            // Use enhanced validation
            Boolean validated = validateLLMFilterResponse(response, "evaluateMinuteContainsRequestedInfo");
            
            // If validation returns null (unknown), default to true (keep document) to avoid false negatives
            if (validated != null) {
                return validated;
            }
            
            // Fallback: default to true (less strict) if validation returned null
            return true;
        } catch (Exception e) {
            log().warn("Error evaluating minute with LLM, defaulting to true (less strict) to avoid false negatives", e);
            return true; // Less strict: default to true on error
        }
    }

    /**
     * Extracts date from query using NER or parsing.
     * Returns normalized date string (ISO format) or null if no date found.
     * This method is used to validate that documents match the requested date.
     */
    protected String extractDateFromQuery(String query, JSONObject nerEntities) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }

        // Try to extract from NER first (most accurate)
        List<String> dateCandidates = extractDateCandidates(query, nerEntities);
        if (!dateCandidates.isEmpty()) {
            // Try each candidate until we find one that parses successfully
            for (String candidate : dateCandidates) {
                LocalDate parsed = parseDateFlexible(candidate);
                if (parsed != null) {
                    // Normalize to ISO format for comparison
                    String normalized = parsed.format(DateTimeFormatter.ISO_LOCAL_DATE);
                    log().debug("Extracted and normalized date from query: {} -> {}", candidate, normalized);
                    return normalized;
                }
            }
        }

        log().debug("No valid date found in query: {}", query);
        return null;
    }

    /**
     * Gets document date from metadata.
     * Tries multiple metadata field names and returns normalized date string or null.
     */
    protected String getDocumentDate(Document doc) {
        if (doc == null || doc.getMetadata() == null) {
            return null;
        }

        Map<String, Object> metadata = doc.getMetadata();

        // PRIORITY 1: Try date_iso first (most reliable - already in ISO format)
        Object dateIsoValue = metadata.get("date_iso");
        if (dateIsoValue != null) {
            String dateIsoStr = dateIsoValue.toString().trim();
            if (!dateIsoStr.isEmpty()) {
                // Validate it's actually ISO format
                try {
                    LocalDate.parse(dateIsoStr, DateTimeFormatter.ISO_LOCAL_DATE);
                    log().debug("Using date_iso field for document {}: {}", doc.getId(), dateIsoStr);
                    return dateIsoStr;
                } catch (DateTimeParseException e) {
                    log().warn("date_iso field '{}' for document {} is not valid ISO format, will try other fields", 
                              dateIsoStr, doc.getId());
                }
            }
        }

        // PRIORITY 2: Try date field (may be in Spanish format, needs parsing)
        Object dateValue = metadata.get("date");
        if (dateValue != null) {
            String dateStr = dateValue.toString().trim();
            if (!dateStr.isEmpty()) {
                // Try to parse and normalize to ISO (parseDateFlexible handles case normalization)
                LocalDate parsed = parseDateFlexible(dateStr);
                if (parsed != null) {
                    String normalized = parsed.format(DateTimeFormatter.ISO_LOCAL_DATE);
                    log().debug("Parsed and normalized date field for document {}: {} -> {}", 
                              doc.getId(), dateStr, normalized);
                    return normalized;
                }
                // Check if already in ISO format
                try {
                    LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
                    log().debug("Date field for document {} is already in ISO format: {}", doc.getId(), dateStr);
                    return dateStr;
                } catch (DateTimeParseException ignored) {
                    log().warn("Could not parse date '{}' from 'date' field for document {}, trying other fields", 
                              dateStr, doc.getId());
                }
            }
        }

        // PRIORITY 3: Try other possible field names
        String[] otherDateFields = {"fecha", "meeting_date", "document_date"};
        for (String field : otherDateFields) {
            Object fieldValue = metadata.get(field);
            if (fieldValue != null) {
                String dateStr = fieldValue.toString().trim();
                if (!dateStr.isEmpty()) {
                    LocalDate parsed = parseDateFlexible(dateStr);
                    if (parsed != null) {
                        String normalized = parsed.format(DateTimeFormatter.ISO_LOCAL_DATE);
                        log().debug("Parsed and normalized date from field '{}' for document {}: {} -> {}", 
                                  field, doc.getId(), dateStr, normalized);
                        return normalized;
                    }
                }
            }
        }

        // Try to get date from Minute object if available
        try {
            Minute minute = getMinuteFromMetadata(doc);
            if (minute != null && minute.date() != null && !minute.date().trim().isEmpty()) {
                LocalDate parsed = parseDateFlexible(minute.date());
                if (parsed != null) {
                    return parsed.format(DateTimeFormatter.ISO_LOCAL_DATE);
                }
                return minute.date();
            }
        } catch (Exception e) {
            log().debug("Could not extract date from Minute object for document: {}", doc.getId());
        }

        return null;
    }

    /**
     * Validates that documents match the requested date.
     * Filters documents to only those that match the requested date.
     * Returns empty list if no documents match.
     */
    protected List<Document> validateDateMatch(List<Document> docs, String requestedDate) {
        if (docs == null || docs.isEmpty() || requestedDate == null || requestedDate.trim().isEmpty()) {
            return docs != null ? docs : new ArrayList<>();
        }

        // Parse requested date
        LocalDate requestedLocalDate = parseDateFlexible(requestedDate);
        if (requestedLocalDate == null) {
            log().warn("Could not parse requested date: {}, returning all documents", requestedDate);
            return docs; // Can't parse, return all (don't filter)
        }

        String requestedNormalized = requestedLocalDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
        log().debug("Validating {} documents against requested date: {} (normalized: {})", 
                   docs.size(), requestedDate, requestedNormalized);

        // Filter documents by date
        List<Document> filtered = docs.stream()
                .filter(doc -> {
                    String docDate = getDocumentDate(doc);
                    if (docDate == null) {
                        log().debug("Document {} has no date, excluding from date-filtered results", doc.getId());
                        return false; // Exclude documents without date when filtering by date
                    }

                    // getDocumentDate should return ISO format, but parse again to be safe
                    // First check if already in ISO format
                    LocalDate docLocalDate = null;
                    try {
                        docLocalDate = LocalDate.parse(docDate, DateTimeFormatter.ISO_LOCAL_DATE);
                    } catch (DateTimeParseException ignored) {
                        // Not ISO format, try flexible parsing
                        docLocalDate = parseDateFlexible(docDate);
                    }
                    
                    if (docLocalDate == null) {
                        log().warn("Could not parse document date '{}' for document {}, excluding. Requested date: {}", 
                                  docDate, doc.getId(), requestedNormalized);
                        return false; // Can't parse, exclude
                    }

                    String docNormalized = docLocalDate.format(DateTimeFormatter.ISO_LOCAL_DATE);
                    boolean matches = docNormalized.equals(requestedNormalized);
                    
                    if (matches) {
                        log().debug("Document {} matches requested date: {} ({})", 
                                   doc.getId(), docDate, docNormalized);
                    } else {
                        log().debug("Document {} date mismatch: {} ({}) vs requested {} ({})", 
                                   doc.getId(), docDate, docNormalized, requestedDate, requestedNormalized);
                    }
                    
                    return matches;
                })
                .collect(Collectors.toList());

        log().info("Date validation: {} documents matched date {} (normalized: {}) out of {} total documents", 
                  filtered.size(), requestedDate, requestedNormalized, docs.size());
        
        // Enhanced logging: show sample document dates when no matches found
        if (filtered.isEmpty() && !docs.isEmpty()) {
            List<String> sampleDates = docs.stream()
                    .limit(5)
                    .map(doc -> {
                        String date = getDocumentDate(doc);
                        if (date != null) {
                            try {
                                LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
                                return date + " (ISO)";
                            } catch (DateTimeParseException e) {
                                LocalDate parsed = parseDateFlexible(date);
                                return date + (parsed != null ? " (parsed: " + parsed.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")" : " (parse failed)");
                            }
                        }
                        return "no date";
                    })
                    .collect(Collectors.toList());
            log().warn("Date validation failed: Requested date {} (normalized: {}) not found. Sample document dates: {}", 
                      requestedDate, requestedNormalized, sampleDates);
        }
        
        // Enhanced logging: show sample document dates when no matches found
        if (filtered.isEmpty() && !docs.isEmpty()) {
            List<String> sampleDates = docs.stream()
                    .limit(5)
                    .map(doc -> {
                        String date = getDocumentDate(doc);
                        if (date != null) {
                            LocalDate parsed = parseDateFlexible(date);
                            return date + (parsed != null ? " (parsed: " + parsed.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")" : " (parse failed)");
                        }
                        return "no date";
                    })
                    .collect(Collectors.toList());
            log().warn("Date validation failed: Requested date {} (normalized: {}) not found. Sample document dates: {}", 
                      requestedDate, requestedNormalized, sampleDates);
        }

        return filtered;
    }

    /**
     * Extracts attendees from document metadata.
     * Prioritizes metadata over content extraction.
     * 
     * @param doc Document to extract attendees from
     * @return List of attendee names
     */
    protected List<String> extractAttendeesFromMetadata(Document doc) {
        if (doc == null) {
            return new ArrayList<>();
        }
        
        List<String> attendees = new ArrayList<>();
        Map<String, Object> metadata = doc.getMetadata();
        
        if (metadata == null) {
            return attendees;
        }
        
        // Try to get attendees from metadata
        Object attendeesObj = metadata.get("attendees");
        if (attendeesObj != null) {
            if (attendeesObj instanceof List) {
                List<?> attendeesList = (List<?>) attendeesObj;
                for (Object item : attendeesList) {
                    if (item != null) {
                        String attendee = item.toString().trim();
                        if (!attendee.isEmpty()) {
                            attendees.add(attendee);
                        }
                    }
                }
            } else if (attendeesObj instanceof String) {
                String attendeesStr = (String) attendeesObj;
                // Try to parse comma-separated or newline-separated list
                String[] parts = attendeesStr.split("[,\n•]");
                for (String part : parts) {
                    String attendee = part.trim();
                    if (!attendee.isEmpty()) {
                        // Remove role indicators like "(Secretario)", "(Presidente)", etc.
                        attendee = attendee.replaceAll("\\([^)]*\\)", "").trim();
                        if (!attendee.isEmpty()) {
                            attendees.add(attendee);
                        }
                    }
                }
            }
        }
        
        // Also try to get from minute object if available
        try {
            Minute minute = getMinuteFromMetadata(doc);
            if (minute != null && minute.attendees() != null) {
                for (String attendee : minute.attendees()) {
                    if (attendee != null && !attendee.isBlank() && !attendees.contains(attendee)) {
                        attendees.add(attendee);
                    }
                }
            }
        } catch (Exception e) {
            log().debug("Error extracting attendees from minute object: {}", e.getMessage());
        }
        
        log().info("Extracted {} attendees from document metadata", attendees.size());
        return attendees;
    }
}


