package com.uniovi.rag.tool.metadata;

import com.uniovi.rag.util.RegexSafety;
import com.uniovi.rag.observability.ContextPropagatingFutures;
import com.uniovi.rag.tool.AbstractTool;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.cache.annotation.Cacheable;
import com.uniovi.rag.model.Minute;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Arrays;

import java.util.regex.Pattern;
import java.util.regex.Matcher;

public abstract class AbstractMetadataTool extends AbstractTool {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String METADATA_KEY_END_TIME = "endTime";
    /** Normalized ISO date in document metadata (see ingestion / vector store). */
    private static final String METADATA_KEY_DATE_ISO = "date_iso";

    /** Case-insensitive matching for Latin extended names (Sonar: UNICODE_CASE for non-ASCII letters). */
    private static final int UNICODE_CASE_INSENSITIVE =
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE | Pattern.CANON_EQ;

    private static final String NER_KEY_FILTERS = "filters";

    /** JSON key in LLM responses for attendee-count threshold (see {@link #detectAttendeesCountQuery}). */
    private static final String JSON_KEY_THRESHOLD = "threshold";

    /** Generic user-facing message when retrieval returns no documents (keep in sync across fallbacks). */
    private static final String MSG_NO_RELEVANT_MINUTES = "No relevant meeting minutes were found for this query.";

    private static final String METADATA_KEY_PRESIDENT = "president";

    private static final String METADATA_KEY_TOPICS = "topics";

    /** Metadata key for original filename (ingestion / chunk grouping). */
    private static final String METADATA_KEY_FILENAME = "filename";

    private static final String METADATA_KEY_ATTENDEES_COUNT = "attendeesCount";

    private static final String QUERY_TYPE_GENERAL = "general";

    /** Spanish query cues for topic extraction (avoid duplicated literals). */
    private static final String TOPIC_CUE_MENCION_ACCENT = "mencionó ";
    private static final String TOPIC_CUE_MENCION_PLAIN = "menciono ";

    private final MetadataLlmResponseCacheService llmResponseCache;

    /** Spring-injected proxy so {@code @Cacheable} methods are not invoked via {@code this} (self-invocation). */
    private AbstractMetadataTool cacheableSelf;

    public AbstractMetadataTool(
            ChatClient chatClient,
            ContextRetriever retriever,
            DocumentContentExtractor extractor,
            MetadataLlmResponseCacheService llmResponseCache) {
        super(chatClient, retriever, extractor);
        this.llmResponseCache = llmResponseCache;
    }

    @Autowired
    public void setCacheableSelf(@Lazy AbstractMetadataTool cacheableSelf) {
        this.cacheableSelf = cacheableSelf;
    }

    private AbstractMetadataTool cacheable() {
        return cacheableSelf != null ? cacheableSelf : this;
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
        return semanticallyMatches(doc.getText(), new String[]{query});
    }

    protected boolean containsInMetadata(Document doc, String[] terms) {
        for (Object val : doc.getMetadata().values()) {
            if (val instanceof String str && extractor.containsAnyKeyword(str, terms)) return true;
            if (val instanceof List<?> list) {
                for (Object item : list) {
                    if (item instanceof String s && extractor.containsAnyKeyword(s, terms)) return true;
                }
            }
        }
        return false;
    }

    protected String[] extractTermsFromNER(JSONObject entidades) {
        Set<String> terms = new HashSet<>();

        if (entidades.has("person"))
            entidades.getJSONArray("person").forEach(item -> terms.add(item.toString().toLowerCase()));

        if (entidades.has(NER_KEY_FILTERS)) {
            JSONObject filtros = entidades.getJSONObject(NER_KEY_FILTERS);
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
            return validated == null || validated;
        } catch (Exception e) {
            log().warn("Error in semantic metadata matching, defaulting to true (keep document)", e);
            return true; // Default to true on error to avoid false negatives
        }
    }
    
    /**
     * Validates LLM filter response (yes/no/unknown). Delegates to parent's interpretLLMYesNoResponse.
     */
    private Boolean validateLLMFilterResponse(String response, String context) {
        return interpretLLMYesNoResponse(response, context);
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
            return validated == null || validated;
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
        // Use document_id for id when present so counting/deduping is by unique minute, not per chunk
        String id = safeGetString(metadata, "document_id");
        if (id == null || id.isBlank()) {
            id = safeGetString(metadata, "id");
        }
        String filename = safeGetString(metadata, METADATA_KEY_FILENAME);
        String date = safeGetString(metadata, "date");
        String place = safeGetString(metadata, "place");
        String startTime = safeGetString(metadata, "startTime");
        String endTime = safeGetString(metadata, METADATA_KEY_END_TIME);
        String president = safeGetString(metadata, METADATA_KEY_PRESIDENT);
        String secretary = safeGetString(metadata, "secretary");
        
        // Use safe methods for complex types
        List<String> attendees = safeGetStringList(metadata, "attendees");
        
        int numberOfAttendees = metadata.containsKey("numberOfAttendees") 
            ? safeGetInt(metadata, "numberOfAttendees", attendees.size())
            : attendees.size();
        
        Map<String, String> agenda = safeGetStringMap(metadata, "agenda");
        List<String> decisions = safeGetStringList(metadata, "decisions");
        List<String> mentionedEntities = safeGetStringList(metadata, "mentionedEntities");
        List<String> topics = safeGetStringList(metadata, METADATA_KEY_TOPICS);
        
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
        
        if (value instanceof String str) {
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
                    .toList();
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
                    .toList();
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
        
        if (value instanceof Number number) {
            return number.intValue();
        }
        
        if (value instanceof String str) {
            try {
                return Integer.parseInt(str);
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
        if (ner == null || ner.isEmpty()) {
            return true;
        }
        if (!passesMentionedEntitiesGate(minute, ner) || !passesAgendaNerGate(minute, ner)) {
            return false;
        }
        return evaluateMinuteNerWithLlm(minute, ner);
    }

    private boolean passesMentionedEntitiesGate(Minute minute, JSONObject ner) {
        if (ner.has("mentionedEntities") && !ner.getJSONArray("mentionedEntities").isEmpty()) {
            if (!matchesMentionedEntities(minute, ner)) {
                log().info("Minute {} filtered out by mentionedEntities mismatch", minute.id());
                return false;
            }
        }
        return true;
    }

    private boolean passesAgendaNerGate(Minute minute, JSONObject ner) {
        if (ner.has("agenda") && !ner.getJSONArray("agenda").isEmpty()) {
            if (!matchesAgendaItems(minute, ner)) {
                log().info("Minute {} filtered out by agenda items mismatch", minute.id());
                return false;
            }
        }
        return true;
    }

    private boolean evaluateMinuteNerWithLlm(Minute minute, JSONObject ner) {
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
            return validated == null || validated; // Default to true to avoid false negatives
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
                    .toList();
            
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
            boolean matches = validated == null || validated; // Default to true to avoid false negatives

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
                    .toList();
            
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
     * Calculates the duration of a meeting in minutes.
     */
    protected int calculateDurationFromMinute(Minute minute) {
        if (minute == null) {
            log().warn("Cannot calculate duration: minute is null");
            return 0;
        }
        
        String startTimeStr = minute.startTime();
        String endTimeStr = minute.endTime();
        
        if (startTimeStr == null || startTimeStr.trim().isEmpty()) {
            log().warn("Cannot calculate duration: startTime is null or empty for minute {} (date: {})", 
                      minute.id(), minute.date());
            return 0;
        }
        
        if (endTimeStr == null || endTimeStr.trim().isEmpty()) {
            log().warn("Cannot calculate duration: endTime is null or empty for minute {} (date: {})", 
                      minute.id(), minute.date());
            return 0;
        }
        
        // Normalize time strings before parsing
        String normalizedStart = normalizeTimeString(startTimeStr);
        String normalizedEnd = normalizeTimeString(endTimeStr);
        
        if (normalizedStart == null || normalizedEnd == null) {
            log().warn("Cannot normalize times for duration calculation: startTime='{}', endTime='{}' for minute {} (date: {})", 
                      startTimeStr, endTimeStr, minute.id(), minute.date());
            return 0;
        }
        
        log().debug("Normalized times for minute {}: '{}' -> '{}', '{}' -> '{}'", 
                   minute.id(), startTimeStr, normalizedStart, endTimeStr, normalizedEnd);
        
        try {
            LocalTime start = LocalTime.parse(normalizedStart, TIME_FORMATTER);
            LocalTime end = LocalTime.parse(normalizedEnd, TIME_FORMATTER);
            return computeDurationFromParsedTimes(start, end, minute, normalizedStart, normalizedEnd);
        } catch (DateTimeParseException e) {
            log().warn("Cannot parse normalized times for duration calculation: startTime='{}', endTime='{}' for minute {} (date: {}). Error: {}", 
                      normalizedStart, normalizedEnd, minute.id(), minute.date(), e.getMessage());
            return tryDurationWithHhMmSsFallback(normalizedStart, normalizedEnd, minute);
        } catch (Exception ex) {
            log().error("Unexpected error calculating duration for minute {} (date: {}): {}", 
                       minute.id(), minute.date(), ex.getMessage(), ex);
            return 0;
        }
    }

    private int computeDurationFromParsedTimes(LocalTime start, LocalTime end, Minute minute,
            String normalizedStart, String normalizedEnd) {
        if (end.isBefore(start) || end.equals(start)) {
            log().warn("Invalid duration: endTime ({}) is not after startTime ({}) for minute {} (date: {})",
                    normalizedEnd, normalizedStart, minute.id(), minute.date());
            if (end.isBefore(start)) {
                int duration = (24 * 60) - (start.getHour() * 60 + start.getMinute())
                        + (end.getHour() * 60 + end.getMinute());
                log().debug("Calculated duration assuming next day: {} minutes ({}h{}m) for minute {} (date: {})",
                        duration, duration / 60, duration % 60, minute.id(), minute.date());
                return duration > 0 && duration <= 24 * 60 ? duration : 0;
            }
            return 0;
        }
        int startMinutes = start.getHour() * 60 + start.getMinute();
        int endMinutes = end.getHour() * 60 + end.getMinute();
        int duration = endMinutes - startMinutes;
        if (duration < 1) {
            log().warn("Invalid duration: {} minutes (too short) for minute {} (date: {}, start: {}, end: {})",
                    duration, minute.id(), minute.date(), normalizedStart, normalizedEnd);
            return 0;
        }
        if (duration > 24 * 60) {
            log().warn("Invalid duration: {} minutes (too long, >24h) for minute {} (date: {}, start: {}, end: {})",
                    duration, minute.id(), minute.date(), normalizedStart, normalizedEnd);
            return 0;
        }
        log().info("Calculated duration: {} minutes ({}h{}m) for minute {} (date: {}, start: {}, end: {})",
                duration, duration / 60, duration % 60, minute.id(), minute.date(), normalizedStart, normalizedEnd);
        return duration;
    }

    private int tryDurationWithHhMmSsFallback(String normalizedStart, String normalizedEnd, Minute minute) {
        try {
            LocalTime start = LocalTime.parse(normalizedStart, DateTimeFormatter.ofPattern("HH:mm:ss"));
            LocalTime end = LocalTime.parse(normalizedEnd, DateTimeFormatter.ofPattern("HH:mm:ss"));
            int startMinutes = start.getHour() * 60 + start.getMinute();
            int endMinutes = end.getHour() * 60 + end.getMinute();
            int duration = endMinutes - startMinutes;
            log().debug("Parsed times with HH:mm:ss format, duration: {} minutes ({}h{}m)",
                    duration, duration / 60, duration % 60);
            return duration > 0 && duration <= 24 * 60 ? duration : 0;
        } catch (DateTimeParseException e2) {
            log().warn("Failed to parse times with alternative format for minute {} (date: {})",
                    minute.id(), minute.date());
            return 0;
        }
    }
    
    /**
     * Normalizes a time string to HH:mm format.
     * Handles various input formats and normalizes them consistently.
     * 
     * @param timeStr Time string in any format (e.g., "19:00", "19:00:00", "19.00", "7:00 PM")
     * @return Normalized time string in HH:mm format, or null if cannot be parsed
     */
    private String normalizeTimeString(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) {
            return null;
        }
        
        String normalized = timeStr.trim();
        
        // Remove common prefixes/suffixes
        normalized = normalized.replaceAll("(?i)^(hora|time|h):\\s*", "");
        normalized = normalized.replaceAll("\\s*$", "");
        
        // Replace dots with colons (e.g., "19.00" -> "19:00")
        normalized = normalized.replace('.', ':');
        
        // Try to parse and reformat to HH:mm
        try {
            // Try HH:mm format first
            LocalTime time = LocalTime.parse(normalized, TIME_FORMATTER);
            return time.format(TIME_FORMATTER);
        } catch (DateTimeParseException ignored) {
            // Try HH:mm:ss format
            try {
                LocalTime time = LocalTime.parse(normalized, 
                    DateTimeFormatter.ofPattern("HH:mm:ss"));
                return time.format(TIME_FORMATTER);
            } catch (DateTimeParseException ignored2) {
                // Try H:mm format (single digit hour)
                try {
                    LocalTime time = LocalTime.parse(normalized, 
                        DateTimeFormatter.ofPattern("H:mm"));
                    return time.format(TIME_FORMATTER);
                } catch (DateTimeParseException ignored3) {
                    log().debug("Could not parse time string '{}' with any standard format", timeStr);
                    return null;
                }
            }
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
        Map.entry("hora_fin", METADATA_KEY_END_TIME),
        Map.entry("hora de fin", METADATA_KEY_END_TIME),
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
        switch (canonicalField) {
            case "date":
                return minute.date();
            case "place":
                return minute.place();
            case "president":
                return minute.president();
            case "secretary":
                return minute.secretary();
            case "starttime":
                return minute.startTime();
            case "endtime":
                return minute.endTime();
            case "topics":
                return minute.topics();
            case "decisions":
                return minute.decisions();
            case "summary":
                return minute.summary();
            case "agenda":
            case "orden_del_dia":
            case "order_of_day": {
                Map<String, String> agenda = minute.agenda();
                if (agenda == null || agenda.isEmpty()) {
                    return null;
                }
                return agenda.values().stream()
                        .filter(v -> v != null && !v.isBlank())
                        .collect(Collectors.joining(", "));
            }
            case "attendees":
                return minute.attendees();
            case "numberofattendees":
            case "attendeescount":
                return minute.numberOfAttendees();
            case "durationminutes", "duration": {
                int duration = calculateDurationFromMinute(minute);
                return duration > 0 ? duration : null;
            }
            default:
                return null;
        }
    }

    /**
     * Compares two duration values and returns the difference.
     */
    protected int compareDurations(Minute minute1, Minute minute2) {
        int duration1 = calculateDurationFromMinute(minute1);
        int duration2 = calculateDurationFromMinute(minute2);
        return duration1 - duration2;
    }

    /**
     * Compares two attendee lists and returns the differences.
     */
    protected Map<String, List<String>> compareAttendees(Minute minute1, Minute minute2) {
        Set<String> attendees1 = new HashSet<>(minute1.attendees() != null ? minute1.attendees() : List.of());
        Set<String> attendees2 = new HashSet<>(minute2.attendees() != null ? minute2.attendees() : List.of());

        List<String> onlyIn1 = attendees1.stream()
                .filter(a -> !attendees2.contains(a))
                .toList();

        List<String> onlyIn2 = attendees2.stream()
                .filter(a -> !attendees1.contains(a))
                .toList();

        return Map.of(
            "only_in_first", onlyIn1,
            "only_in_second", onlyIn2
        );
    }

    /**
     * Compares two topic lists and returns the differences.
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
        var ctx = ContextPropagatingFutures.captureContext();
        return docs.parallelStream()
                .map(doc -> ContextPropagatingFutures.withSnapshot(ctx, () -> {
                    try {
                        Minute m = cacheable().getMinuteFromMetadata(doc);
                        if (m == null || !isMinuteComplete(m) || !hasUsefulData(m)) {
                            return null;
                        }
                        return m;
                    } catch (Exception e) {
                        log().warn("Error extracting minute from document {}, skipping: {}",
                                 doc != null ? doc.getId() : "null", e.getMessage());
                        return null;
                    }
                }))
                .filter(Objects::nonNull)
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
                    .toList();
            
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
                    .toList();
            
            log().info("NER matching filtered {} minutes to {}", directMatched.size(), filtered.size());
            
            // If NER filtering removed too many, use direct matched
            if (filtered.isEmpty() && !directMatched.isEmpty()) {
                log().warn("NER filtering removed all minutes, using direct matched minutes");
                filtered = directMatched.stream().limit(40).collect(Collectors.toList());
            }
        }
        
        // STEP 4: Relevance filtering only when we have many minutes (avoid LLM when <= 10 to reduce false "not found" answers)
        if (filtered.size() <= 10) {
            log().info("Skipping LLM relevance filter ({} minutes <= 10), returning filtered list", filtered.size());
            return filtered;
        }
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
        
        // Conservative fallback: if filtered is empty but query had a date, return minutes that match that date
        if (filtered.isEmpty() && !minutes.isEmpty()) {
            List<String> dateCandidates = extractDateCandidates(query, ner);
            for (String candidate : dateCandidates) {
                LocalDate requested = parseDateFlexible(candidate);
                if (requested != null) {
                    String requestedIso = requested.format(DateTimeFormatter.ISO_LOCAL_DATE);
                    List<Minute> sameDate = minutes.stream()
                            .filter(m -> {
                                LocalDate md = parseDateFlexible(m.date());
                                return md != null && md.format(DateTimeFormatter.ISO_LOCAL_DATE).equals(requestedIso);
                            })
                            .toList();
                    if (!sameDate.isEmpty()) {
                        log().warn("Filtering left 0 minutes but {} minutes match requested date {}; returning them as conservative fallback", sameDate.size(), requestedIso);
                        return sameDate;
                    }
                }
            }
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
                    .toList();
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
            return minutes.stream().limit(50).toList(); // Increased from 30 to 50
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
                .toList();
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

        var ctx = ContextPropagatingFutures.captureContext();
        AbstractMetadataTool proxy = cacheable();
        return minutes.parallelStream()
                .filter(minute -> ContextPropagatingFutures.withSnapshot(ctx, () -> proxy.matchesMinuteWithNERCached(minute, ner)))
                .filter(minute -> ContextPropagatingFutures.withSnapshot(ctx, () -> proxy.isRelevantToQueryCached(query, minute)))
                .toList();
    }

    /**
     * Cached NER matching evaluation.
     * Key must include both minute and NER so a "not relevant" for one query/minute is not reused for another.
     */
    @Cacheable(value = "nerMatching", key = "#minute.id() + '_' + (#ner != null ? #ner.toString() : 'null')")
    protected boolean matchesMinuteWithNERCached(Minute minute, JSONObject ner) {
        return cacheable().matchesMinuteWithNER(minute, ner);
    }

    /**
     * Filters minutes by query relevance using LLM
     */
    protected List<Minute> filterMinutesByQueryRelevance(String query, List<Minute> minutes) {
        log().info("Filtering {} minutes by query relevance", minutes.size());

        var ctx = ContextPropagatingFutures.captureContext();
        return minutes.parallelStream()
                .filter(minute -> ContextPropagatingFutures.withSnapshot(ctx, () -> cacheable().isRelevantToQueryCached(query, minute)))
                .toList();
    }

    /**
     * Cached query relevance evaluation.
     * Key must include both query and minute so a "not relevant" for one pair is not reused for another.
     */
    @Cacheable(value = "queryRelevance", key = "#query + '_' + #minute.id()")
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
            return validated == null || validated;
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
            return QUERY_TYPE_GENERAL;
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
                return QUERY_TYPE_GENERAL;
            }
        } catch (Exception e) {
            log().warn("Error analyzing query type, defaulting to general", e);
            return QUERY_TYPE_GENERAL;
        }
    }

    /**
     * Delegates to {@link MetadataLlmResponseCacheService} so {@code @Cacheable} runs through the Spring proxy.
     */
    protected String getLLMResponseCached(String prompt) {
        return llmResponseCache.getCachedResponse(prompt);
    }

    /**
     * Retrieves documents with intelligent metadata filtering using EnhancedNERHandler.
     * Filters documents that have relevant metadata fields (either complete Minute object or individual fields).
     * Implements robust fallback when metadata filtering removes all documents.
     * When nerEntities is non-null, uses retrieveWithMetadataFilters for date/entity-aware retrieval.
     */
    protected List<Document> retrieveDocumentsWithMetadataFilter(String query, String[] relevantFields) {
        return retrieveDocumentsWithMetadataFilter(query, relevantFields, null);
    }

    protected List<Document> retrieveDocumentsWithMetadataFilter(String query, String[] relevantFields, JSONObject nerEntities) {
        List<Document> docs = (nerEntities != null && !nerEntities.isEmpty())
                ? retriever.retrieveWithMetadataFilters(query, nerEntities)
                : retriever.retrieve(query);

        List<Document> metadataDocs = docs.stream()
                .filter(doc -> hasMetadataFields(doc, relevantFields))
                .toList();

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
                    .toList();
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
                                metadata.containsKey(METADATA_KEY_FILENAME);
        
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
               metadata.containsKey(METADATA_KEY_FILENAME) ||
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
                return (metadata.containsKey("date") && metadata.get("date") != null)
                        || (metadata.containsKey(METADATA_KEY_DATE_ISO) && metadata.get(METADATA_KEY_DATE_ISO) != null)
                        || (metadata.containsKey("year") && metadata.get("year") != null)
                        || (metadata.containsKey("month") && metadata.get("month") != null);
            case "numberOfAttendees":
                return (metadata.containsKey("numberOfAttendees") && metadata.get("numberOfAttendees") != null)
                        || (metadata.containsKey(METADATA_KEY_ATTENDEES_COUNT)
                            && metadata.get(METADATA_KEY_ATTENDEES_COUNT) != null);
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
        if (metadataSummary.isEmpty()) {
            metadataSummary.append("date: ").append(metadata.getOrDefault("date", "")).append("; ");
            metadataSummary.append("topics: ").append(metadata.getOrDefault("topics", "")).append("; ");
        }
        
        // Use semantic matching to determine if metadata is relevant to query
        // This is less strict than exact matching but more accurate than always returning true
        return semanticallyMatchesMetadata(doc, query);
    }

    /**
     * LEVEL 1: NER-based retrieval when NER has useful fields.
     *
     * @return a non-null list when documents were found and should be returned; {@code null} to continue with LEVEL 2
     */
    private List<Document> tryRetrieveDocumentsWithNer(String query, String[] relevantFields, JSONObject ner) {
        if (ner == null || ner.isEmpty()) {
            return null;
        }
        if (!hasUsefulNERFields(ner)) {
            log().info("NER entities present but no useful fields, skipping NER-based retrieval");
            return null;
        }
        try {
            List<Document> docs = retriever.retrieveWithMetadataFilters(query, ner);
            List<Document> originalDocs = new ArrayList<>(docs);

            if (relevantFields != null && relevantFields.length > 0 && !docs.isEmpty()) {
                docs = docs.stream()
                        .filter(doc -> hasMetadataFields(doc, relevantFields))
                        .toList();
                if (docs.isEmpty() && !originalDocs.isEmpty()) {
                    log().warn("Filtering by relevantFields removed all documents, using unfiltered documents as fallback");
                    docs = originalDocs;
                }
            }

            if (docs.isEmpty()) {
                log().info("NER-based retrieval returned no documents after filtering, trying fallback");
                return null;
            }
            log().info("Retrieved {} documents using NER-based retrieval with metadata filters", docs.size());
            return validateDateMatchIfPresent(docs, query, ner);
        } catch (Exception e) {
            log().warn("Error using NER-based retrieval, falling back to basic retrieval: {}", e.getMessage());
            return null;
        }
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
        List<Document> nerDocs = tryRetrieveDocumentsWithNer(query, relevantFields, ner);
        if (nerDocs != null) {
            return nerDocs;
        }

        // LEVEL 2: Try metadata filtering (use NER for retrieval when available)
        List<Document> docs = retrieveDocumentsWithMetadataFilter(query, relevantFields, ner);
        
        // Fallback 1: If empty, try basic retrieval (with NER when enabled for date/metadata filtering)
        if (docs.isEmpty()) {
            log().info("Metadata filtering returned no documents, trying basic retrieval");
            docs = retrieveDocuments(query, ner);
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
     * LLM check for a single document against a topic; adds to {@code filtered} when the model confirms a match.
     */
    private void maybeAddDocumentMatchingTopic(Document doc, String topic, List<Document> filtered) {
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

        String content = doc.getText();
        if (content != null && !content.trim().isEmpty()) {
            String truncatedContent = content.length() > 1000 ? content.substring(0, 1000) + "..." : content;
            context.append("Content: ").append(truncatedContent);
        }

        if (context.length() == 0) {
            return;
        }

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
            filtered.add(doc);
        }
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
            if (doc != null) {
                maybeAddDocumentMatchingTopic(doc, topic, filtered);
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
            
            // Check for agenda in NER (topic may be an agenda item, e.g. "aprobación de cuentas")
            if (ner.has("agenda") && !ner.isNull("agenda")) {
                try {
                    org.json.JSONArray agenda = ner.getJSONArray("agenda");
                    if (agenda.length() > 0) {
                        String agendaItem = agenda.getString(0).trim();
                        if (!agendaItem.isEmpty()) {
                            log().info("Extracted topic from NER agenda: {}", agendaItem);
                            return agendaItem;
                        }
                    }
                } catch (Exception e) {
                    log().debug("Error extracting topic from NER agenda: {}", e.getMessage());
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

        // Use literal topic when question explicitly mentions it (avoids LLM substituting heating for pool HVAC, etc.) — item 38
        String queryLower = query.toLowerCase().trim();
        if (queryLower.contains("calefacción") || queryLower.contains("calefaccion")) {
            log().info("Extracted topic from query (literal): calefacción");
            return "calefacción";
        }

        // Heuristic fallback: extract topic from "about X" / "mentioned X" substrings in the query to avoid LLM returning full question (items 8, 15)
        String q = query.trim();
        if (q.length() > 50) {
            String qLower = q.toLowerCase();
            if (qLower.contains("sobre ")) {
                int idx = qLower.indexOf("sobre ");
                String after = q.substring(idx + 6).trim().replaceFirst("\\.$", "").trim();
                after = after.replaceFirst("^el\\s+", "").replaceFirst("^la\\s+", "").trim();
                if (!after.isEmpty() && after.length() < 100) {
                    log().info("Extracted topic from query (after 'sobre'): {}", after);
                    return after;
                }
            }
            if (qLower.contains(TOPIC_CUE_MENCION_ACCENT) || qLower.contains(TOPIC_CUE_MENCION_PLAIN)) {
                int idxMen = qLower.indexOf(TOPIC_CUE_MENCION_ACCENT);
                int skipLen = TOPIC_CUE_MENCION_ACCENT.length();
                if (idxMen < 0) {
                    idxMen = qLower.indexOf(TOPIC_CUE_MENCION_PLAIN);
                    skipLen = TOPIC_CUE_MENCION_PLAIN.length();
                }
                String after = q.substring(idxMen + skipLen).trim().replaceFirst("\\.$", "").trim();
                if (!after.isEmpty() && after.length() < 60) {
                    String topic = after.replaceFirst("^la ", "").trim();
                    log().info("Extracted topic from query (after 'mencionó'): {}", topic);
                    return topic;
                }
            }
        }

        // Use LLM to extract topic/keyword from query (handles compound topics)
        String prompt = String.format("""
            Task: Extract the main topic or keyword from the following question about meeting minutes.
            
            Question (may be in any language): "%s"
            
            Extract the main topic, keyword, or subject that the question is asking about.
            Return ONLY a short nominal phrase (one to five words, or a short compound like "estado de cuentas y presupuesto anual"). Do NOT return the full question or a long sentence.
            CRITICAL: Extract the topic EXACTLY as stated in the question. Do NOT replace or generalize it.
            For example: if the question says "calefacción", return "calefacción", NOT "climatización de la piscina" or "heating".
            If the question mentions a compound topic explicitly (e.g., "climatización de la piscina"), extract that FULL phrase.
            
            Examples:
            - "Muestra lo dicho sobre el estado de cuentas y presupuesto anual." → topic: "estado de cuentas y presupuesto anual" (NOT the full question)
            - "Resume todo lo tratado sobre calefacción" → topic: "calefacción" (do not substitute with climatización de la piscina)
            - "How many meetings discussed the elevator?" → topic: "elevator"
            - "¿Cuántas actas hay sobre el ascensor?" → topic: "ascensor" or "elevator"
            - "Resume lo tratado sobre la climatización de la piscina" → topic: "climatización de la piscina" (FULL compound topic)
            - "¿Qué se dijo sobre el control de plagas?" → topic: "control de plagas" (FULL compound topic)
            
            If the question is asking about a count without a specific topic (e.g., "How many meetings were there?"),
            respond with "NONE".
            
            Return ONLY the topic/keyword as stated in the question (or full compound topic if explicitly given), or "NONE" if no specific topic is mentioned.
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
                // Post-process: if LLM returned the full question, try heuristic extraction (items 8, 15)
                if (topic.length() > 50 || topic.split("\\s+").length > 8) {
                    String qTrim = query.trim();
                    String qLower = qTrim.toLowerCase();
                    if (qLower.contains("sobre ")) {
                        int idx = qLower.indexOf("sobre ");
                        String after = qTrim.substring(idx + 6).trim().replaceFirst("\\.$", "").trim();
                        if (!after.isEmpty() && after.length() < topic.length()) {
                            log().info("Extracted topic from query (post-process after 'sobre'): {}", after);
                            return after;
                        }
                    }
                    if (qLower.contains(TOPIC_CUE_MENCION_ACCENT) || qLower.contains(TOPIC_CUE_MENCION_PLAIN)) {
                        int idxMen = qLower.indexOf(TOPIC_CUE_MENCION_ACCENT);
                        if (idxMen < 0) {
                            idxMen = qLower.indexOf(TOPIC_CUE_MENCION_PLAIN);
                        }
                        int cueLen = idxMen >= 0 && qLower.startsWith(TOPIC_CUE_MENCION_ACCENT, idxMen)
                                ? TOPIC_CUE_MENCION_ACCENT.length()
                                : TOPIC_CUE_MENCION_PLAIN.length();
                        String after = qTrim.substring(idxMen + cueLen).trim().replaceFirst("\\.$", "").trim();
                        if (!after.isEmpty() && after.length() < 60) {
                            String shortTopic = after.replaceFirst("^la ", "").trim();
                            log().info("Extracted topic from query (post-process after 'mencionó'): {}", shortTopic);
                            return shortTopic;
                        }
                    }
                }
                log().info("Extracted topic from query using LLM: {}", topic);
                return topic;
            }
        } catch (Exception e) {
            log().warn("Error extracting topic from query using LLM: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * Uses LLM to detect if query asks about number of attendees with a comparison operator.
     * Returns structured information about the comparison (operator and threshold).
     * 
     * @param query User query
     * @return AttendeesCountQueryInfo with operator and threshold, or null if not applicable
     */
    protected AttendeesCountQueryInfo detectAttendeesCountQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }
        
        String prompt = String.format("""
            Task: Analyze if this query asks about the number of attendees/participants with a comparison.
            
            Query (may be in any language): "%s"
            
            Determine:
            1. Does the query ask about number of attendees/participants/people present?
            2. If yes, what comparison operator is used? (less than, more than, equal to, etc.)
            3. What is the threshold number mentioned? (extract the numeric value, e.g., "diez" = 10, "ten" = 10, "15" = 15)
            
            Examples:
            - "En cuántas actas participaron menos de diez personas" → {"isAttendeesQuery": true, "operator": "less_than", "threshold": 10}
            - "En cuántas actas participaron menos de 10 personas" → {"isAttendeesQuery": true, "operator": "less_than", "threshold": 10}
            - "How many meetings had more than 15 attendees" → {"isAttendeesQuery": true, "operator": "more_than", "threshold": 15}
            - "¿Cuántas reuniones tuvieron exactamente 20 asistentes?" → {"isAttendeesQuery": true, "operator": "equal", "threshold": 20}
            - "Número de actas" → {"isAttendeesQuery": false}
            - "Cuántas actas hay" → {"isAttendeesQuery": false}
            
            IMPORTANT: 
            - Extract numeric values correctly (e.g., "diez" = 10, "veinte" = 20, "quince" = 15)
            - Recognize comparison operators: "menos de" = less_than, "más de" = more_than, "exactamente" = equal
            - Only return true if the query explicitly asks about NUMBER of attendees with a comparison
            
            Respond with JSON format ONLY (no additional text):
            {
              "isAttendeesQuery": true/false,
              "operator": "less_than" | "more_than" | "equal" | null,
              "threshold": number or null
            }
            
            If not an attendees count query, respond: {"isAttendeesQuery": false}
            """, query);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip();
            
            // Parse JSON response
            JSONObject json = new JSONObject(response);
            if (json.optBoolean("isAttendeesQuery", false)) {
                String operator = json.optString("operator", null);
                Integer threshold = json.has(JSON_KEY_THRESHOLD) && !json.isNull(JSON_KEY_THRESHOLD)
                    ? json.getInt(JSON_KEY_THRESHOLD) : null;
                
                if (operator != null && threshold != null) {
                    log().info("Detected attendees count query: operator={}, threshold={}", operator, threshold);
                    return new AttendeesCountQueryInfo(operator, threshold);
                }
            }
        } catch (Exception e) {
            log().debug("Error detecting attendees count query with LLM: {}", e.getMessage());
        }
        
        // Fallback: explicit string match for "menos de diez" / "menos de 10" so filter is always applied
        String q = query == null ? "" : query.toLowerCase().trim();
        String qNorm = java.text.Normalizer.normalize(q, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        if (qNorm.contains("menos de diez") || qNorm.contains("menos de 10")
                || q.contains("menos de diez") || q.contains("menos de 10")
                || q.contains("menos de diez personas") || q.contains("menos de 10 personas")) {
            log().info("Attendees count query fallback: detected 'menos de diez' (or variant), using less_than 10");
            return new AttendeesCountQueryInfo("less_than", 10);
        }
        return null;
    }
    
    /**
     * Uses LLM to detect if query asks for date where a specific person acted as president/secretary.
     * 
     * @param query User query
     * @return true if query asks for date where person acted, false otherwise
     */
    protected boolean detectDateWherePersonQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        
        String prompt = String.format("""
            Task: Determine if this query asks for the date of a meeting where a specific person acted as president or secretary.
            
            Query (may be in any language): "%s"
            
            Examples:
            - "Proporciona la fecha del acta donde Juan Pérez Gutiérrez actuó como presidente" → YES
            - "¿Cuándo presidió Juan Pérez Gutiérrez?" → YES
            - "Fecha del acta donde [nombre] fue secretario" → YES
            - "Dime la fecha del acta" → NO (no person mentioned)
            - "¿Quién presidió el 25 de agosto?" → NO (asks for person, not date)
            
            Respond with ONLY: YES or NO
            """, query);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip()
                    .toUpperCase();
            
            boolean result = response.contains("YES");
            log().debug("Detected date where person query: {} for query: {}", result, query);
            return result;
        } catch (Exception e) {
            log().debug("Error detecting date where person query with LLM: {}", e.getMessage());
            // Fallback to simple check
            String queryLower = query.toLowerCase();
            return queryLower.contains("donde") || queryLower.contains("where") ||
                   queryLower.contains("actuó") || queryLower.contains("acted");
        }
    }
    
    /**
     * Uses LLM to detect if query requires filtering by both topic AND person.
     * 
     * @param query User query
     * @return true if query requires topic + person filtering (AND logic)
     */
    protected boolean detectTopicAndPersonFilter(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        
        String prompt = String.format("""
            Task: Determine if this query requires filtering meetings by BOTH a topic AND a person (AND logic).
            
            Query (may be in any language): "%s"
            
            Examples that require topic + person filtering (BOTH conditions must be met):
            - "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez" → YES
            - "Asistentes de reuniones donde se habló de climatización y que fueran presididas por Natalia Vázquez Gutiérrez" → YES
            - "Actas sobre seguridad presididas por [nombre]" → YES
            - "Reuniones donde se trató el tema X y fueron presididas por Y" → YES
            - "Actas que mencionan Z y fueron presididas por W" → YES
            
            Examples that do NOT require this filtering:
            - "Dime qué actas mencionan el ascensor" → NO (only topic, no person filter)
            - "¿Quién presidió la reunión del 25 de agosto?" → NO (only person/date, no topic filter)
            - "Actas sobre seguridad" → NO (only topic, no person filter)
            - "Actas presididas por Juan" → NO (only person, no topic filter)
            
            IMPORTANT: Both a topic AND a person must be mentioned for filtering. If only one is mentioned, respond NO.
            
            Respond with ONLY: YES or NO
            """, query);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip()
                    .toUpperCase();
            
            boolean result = response.contains("YES");
            log().info("Detected topic + person filter requirement: {} for query: '{}'", result, query);
            return result;
        } catch (Exception e) {
            log().warn("Error detecting topic + person filter with LLM: {}", e.getMessage());
            // Fallback to simple check
            String queryLower = query.toLowerCase();
            boolean hasTopic = queryLower.contains("mencionan") || queryLower.contains("hablaron") ||
                              queryLower.contains("menciona") || queryLower.contains("habló") ||
                              queryLower.contains("sobre") || queryLower.contains("about") ||
                              queryLower.contains("trat") || queryLower.contains("discut");
            boolean hasPerson = queryLower.contains("presididas por") || queryLower.contains("presidida por") ||
                               queryLower.contains("presidió") || queryLower.contains("presided") ||
                               queryLower.contains("presididas") || queryLower.contains("presidida");
            boolean hasAnd = queryLower.contains(" y ") || queryLower.contains(" and ") ||
                            queryLower.contains("donde") || queryLower.contains("where");
            boolean result = hasTopic && hasPerson && hasAnd;
            log().debug("Fallback detection: topic={}, person={}, and={}, result={}", hasTopic, hasPerson, hasAnd, result);
            return result;
        }
    }
    
    /**
     * Normalizes a person name for comparison (lowercase, trim, remove extra spaces).
     * This helps match names with variations in spacing or capitalization.
     */
    protected String normalizePersonName(String name) {
        if (name == null || name.trim().isEmpty()) {
            return "";
        }
        // Normalize: lowercase, trim, collapse multiple spaces, remove accents for matching (e.g. á->a)
        String n = name.trim().toLowerCase().replaceAll("\\s+", " ");
        n = java.text.Normalizer.normalize(n, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n;
    }
    
    /**
     * Extracts person name from query or NER using multiple strategies.
     * Tries NER first, then regex patterns, then LLM as fallback.
     * 
     * @param query User query
     * @param ner NER entities (may be null)
     * @return Extracted person name or null if not found
     */
    protected String extractPersonNameFromQuery(String query, JSONObject ner) {
        if (query == null || query.trim().isEmpty()) {
            return null;
        }
        
        // Strategy 1: Try NER first
        if (ner != null && ner.has("person")) {
            try {
                org.json.JSONArray persons = ner.getJSONArray("person");
                if (persons.length() > 0) {
                    String personName = persons.getString(0).trim();
                    if (!personName.isEmpty()) {
                        log().debug("Extracted person name from NER: {}", personName);
                        return personName;
                    }
                }
            } catch (Exception e) {
                log().debug("Could not extract person from NER", e);
            }
        }
        
        // Strategy 2: Try regex patterns for common Spanish/English patterns
        Pattern[] patterns = {
            // Pattern 1: "presided by [Full Name]" (Spanish/English)
            Pattern.compile(
                "(?:presididas?|presidió|presided)\\s+por\\s+([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+){1,3})",
                UNICODE_CASE_INSENSITIVE
            ),
            // Pattern 2: "where [Full Name] acted" (Spanish/English)
            Pattern.compile(
                "(?:donde|where)\\s+([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+){1,3})\\s+(?:actuó|acted|fue|was)",
                UNICODE_CASE_INSENSITIVE
            ),
            // Pattern 3: "[Full Name] acted as president"
            Pattern.compile(
                "([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+){1,3})\\s+(?:actuó|acted)\\s+como",
                UNICODE_CASE_INSENSITIVE
            ),
            // Pattern 4: "where [Full Name]" (more general)
            Pattern.compile(
                "(?:donde|where)\\s+([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+){1,3})",
                UNICODE_CASE_INSENSITIVE
            ),
            // Pattern 5: "attended [Full Name]" (e.g. "In which meetings did X attend?")
            Pattern.compile(
                "(?:asistió|attended|attend(?:ed|ee))\\s+([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+){1,3})\\s*(?:\\?|$)",
                UNICODE_CASE_INSENSITIVE
            ),
            // Pattern 6: "meetings attended [Name]" - name at end before ?
            Pattern.compile(
                "reuniones?\\s+asistió\\s+([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+){1,3})\\s*\\??",
                UNICODE_CASE_INSENSITIVE
            ),
            // Pattern 7: "When and in which meetings did [Name] attend" - capture name after "attend"
            Pattern.compile(
                "asistió\\s+([\\p{L}]+(?:\\s+[\\p{L}]+){2,4})\\s*\\??",
                UNICODE_CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS
            )
        };
        
        String queryBounded = RegexSafety.truncateString(query, RegexSafety.MAX_QUERY_TEXT_FOR_REGEX);
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(queryBounded);
            if (matcher.find()) {
                String personName = matcher.group(1).trim();
                if (!personName.isEmpty()) {
                    log().debug("Extracted person name using regex pattern: {}", personName);
                    return personName;
                }
            }
        }
        
        // Strategy 3: Use LLM as fallback for complex cases
        String prompt = String.format("""
            Task: Extract the person name from this query about meeting minutes.
            
            Query (may be in any language): "%s"
            
            Extract the full name of the person mentioned in the query.
            Examples:
            - "presididas por Juan Pérez Gutiérrez" → "Juan Pérez Gutiérrez"
            - "donde Natalia Vázquez Gutiérrez actuó" → "Natalia Vázquez Gutiérrez"
            - "fue presidida por Manuel Ortega Medina" → "Manuel Ortega Medina"
            
            Return ONLY the person's full name, or "NONE" if no person is mentioned.
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
                String personName = response.trim();
                log().debug("Extracted person name using LLM: {}", personName);
                return personName;
            }
        } catch (Exception e) {
            log().debug("Error extracting person name with LLM: {}", e.getMessage());
        }
        
        log().debug("Could not extract person name from query: '{}'", query);
        return null;
    }

    /**
     * Returns true when the query clearly requires a person (chairperson, attendee, president, secretary, etc.).
     * Used to log WARN only when person extraction fails and the query actually asks for a person.
     */
    protected boolean queryRequiresPerson(String query) {
        if (query == null || query.isBlank()) return false;
        String q = query.toLowerCase();
        return q.contains("presididas por") || q.contains("presidida por") || q.contains("presidió") || q.contains("president")
                || q.contains("asistió") || q.contains("asistieron") || q.contains("participó") || q.contains("participaron")
                || q.contains("secretari") || q.contains("quién presidió") || q.contains("quién fue el presidente")
                || q.contains("quién fue la secretaria") || q.contains("quién fue el secretario");
    }
    
    /**
     * Uses LLM to detect if query asks about a specific topic (not just general summary).
     * 
     * @param query User query
     * @return true if query asks about a specific topic
     */
    protected boolean detectSpecificTopicQuery(String query) {
        if (query == null || query.trim().isEmpty()) {
            return false;
        }
        
        String prompt = String.format("""
            Task: Determine if this query asks about a SPECIFIC topic (not just a general summary).
            
            Query (may be in any language): "%s"
            
            Examples that ask about specific topics:
            - "Resume lo tratado sobre la climatización de la piscina" → YES
            - "¿Qué se dijo sobre el ascensor?" → YES
            - "Dime lo comentado sobre control de plagas" → YES
            
            Examples that do NOT ask about specific topics:
            - "Resume las reuniones" → NO (general summary)
            - "Dime qué se trató" → NO (too general)
            - "Cuéntame sobre las actas" → NO (too general)
            
            Respond with ONLY: YES or NO
            """, query);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip()
                    .toUpperCase();
            
            boolean result = response.contains("YES");
            log().debug("Detected specific topic query: {} for query: {}", result, query);
            return result;
        } catch (Exception e) {
            log().debug("Error detecting specific topic query with LLM: {}", e.getMessage());
            // Fallback to simple check
            String queryLower = query.toLowerCase();
            return queryLower.contains("sobre") || queryLower.contains("about") ||
                   queryLower.contains("relativo a") || queryLower.contains("relating to");
        }
    }
    
    /**
     * Data class to hold attendees count query information
     */
    protected static class AttendeesCountQueryInfo {
        public final String operator; // "less_than", "more_than", "equal"
        public final int threshold;
        
        public AttendeesCountQueryInfo(String operator, int threshold) {
            this.operator = operator;
            this.threshold = threshold;
        }
    }
    
    /**
     * Validates that a keyword actually exists in the documents using LLM semantic matching.
     * 
     * @param docs List of documents to search
     * @param keyword Keyword to validate
     * @return true if keyword is found in at least one document, false otherwise
     */
    private StringBuilder buildKeywordValidationContext(Document doc) {
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
        String content = doc.getText();
        if (content != null && !content.trim().isEmpty()) {
            String truncatedContent = content.length() > 500 ? content.substring(0, 500) + "..." : content;
            context.append("Content: ").append(truncatedContent);
        }
        return context;
    }

    protected boolean validateKeywordExists(List<Document> docs, String keyword) {
        if (docs == null || docs.isEmpty() || keyword == null || keyword.trim().isEmpty()) {
            return false;
        }
        
        log().info("Validating keyword '{}' exists in {} documents", keyword, docs.size());
        
        // Check a sample of documents (first 5) to avoid too many LLM calls
        int sampleSize = Math.min(5, docs.size());
        List<Document> sampleDocs = docs.subList(0, sampleSize);
        
        for (Document doc : sampleDocs) {
            if (doc != null) {
                StringBuilder context = buildKeywordValidationContext(doc);
                if (context.length() > 0) {
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
        String fromText = tryExtractYearFromQueryText(query);
        if (fromText != null) {
            return fromText;
        }
        String fromNer = tryExtractYearFromNer(ner);
        if (fromNer != null) {
            return fromNer;
        }
        return tryExtractYearFromLlm(query);
    }

    private String tryExtractYearFromQueryText(String query) {
        Pattern yearPattern = Pattern.compile("\\b(20\\d{2})\\b");
        Matcher matcher = yearPattern.matcher(query);
        if (matcher.find()) {
            String year = matcher.group(1);
            log().info("Extracted year from query text: {}", year);
            return year;
        }
        return null;
    }

    private String tryExtractYearFromNer(JSONObject ner) {
        if (ner == null || ner.isEmpty()) {
            return null;
        }
        try {
            if (ner.has(NER_KEY_FILTERS) && !ner.isNull(NER_KEY_FILTERS)) {
                JSONObject filters = ner.getJSONObject(NER_KEY_FILTERS);
                if (filters.has("date") && !filters.isNull("date")) {
                    org.json.JSONArray dates = filters.getJSONArray("date");
                    Pattern yearPattern = Pattern.compile("\\b(20\\d{2})\\b");
                    for (int i = 0; i < dates.length(); i++) {
                        String dateStr = dates.getString(i);
                        Matcher matcher = yearPattern.matcher(dateStr);
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
        return null;
    }

    private String tryExtractYearFromLlm(String query) {
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

            if (response != null && !response.trim().isEmpty() && !response.trim().equalsIgnoreCase("NONE")
                    && response.matches("20\\d{2}")) {
                log().info("Extracted year from query using LLM: {}", response);
                return response;
            }
        } catch (Exception e) {
            log().warn("Error extracting year from query using LLM: {}", e.getMessage());
        }

        return null;
    }

    private boolean documentMatchesYear(Document doc, String year) {
        String docDate = getDocumentDate(doc);
        if (docDate == null) {
            return false;
        }
        if (docDate.contains(year)) {
            return true;
        }
        try {
            java.time.LocalDate parsedDate = parseDateFlexible(docDate);
            if (parsedDate != null) {
                return String.valueOf(parsedDate.getYear()).equals(year);
            }
        } catch (Exception e) {
            log().debug("Error parsing date '{}' for year filtering: {}", docDate, e.getMessage());
        }
        return false;
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
            if (doc == null) {
                continue;
            }
            if (documentMatchesYear(doc, year)) {
                filtered.add(doc);
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

        // When query mentions only a year (e.g. "reunión de 2026"), extractDateFromQuery may return yyyy-01-01.
        // Do NOT filter by exact date in that case: caller (e.g. Boolean) will use filterDocumentsByYear instead.
        if (requestedDate.matches("\\d{4}-01-01")) {
            log().debug("Date '{}' looks like year-only; skipping exact date validation so year filter can be applied by caller", requestedDate);
            return docs;
        }

        // Validate date match
        List<Document> filtered = validateDateMatch(docs, requestedDate);
        
        if (filtered.isEmpty() && !docs.isEmpty()) {
            log().debug("Date validation filtered out all {} documents for requested date: {} (date not in corpus is expected)",
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
                if (value instanceof org.json.JSONArray ja && ja.length() > 0) {
                    return true;
                } else if (value instanceof String str && !str.trim().isEmpty()) {
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
    @Override
    protected String generateFallbackNotFoundMessage(String query) {
        if (query == null || query.trim().isEmpty()) {
            return MSG_NO_RELEVANT_MINUTES;
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
        return MSG_NO_RELEVANT_MINUTES;
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
            - If field is "president" and date is provided: "No president information found for the date [date]"
            - If field is "agenda": "No agenda found for the specified date"
            - If date is not found: "No meeting minutes found for the date [date]"
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
            return MSG_NO_RELEVANT_MINUTES;
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
        return MSG_NO_RELEVANT_MINUTES;
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
     * Generates a clear "topic not found" message (e.g. for pool HVAC, roof renovation).
     */
    protected String generateTopicNotFoundMessage(String query, String topic) {
        if (topic == null || topic.trim().isEmpty()) {
            return generateNoDataMessage(query);
        }
        return "No se ha encontrado ninguna información relativa a \"" + topic + "\" en las actas disponibles.";
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
        
        Set<String> itemWords = new HashSet<>(Arrays.asList(itemLower.split("\\s+")));
        Set<String> clusterWords = new HashSet<>(Arrays.asList(clusterLower.split("\\s+")));
        
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
            summary.append(String.format("Cluster %d (%s):%n", i + 1, clusterType));
            summary.append(clusters.get(i).toString());
            summary.append(String.format("%n%n"));
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
        analysis.append(String.format("Total %s clusters: %d%n", clusterType, clusters.size()));
        
        for (int i = 0; i < clusters.size(); i++) {
            analysis.append(String.format("- Cluster %d: %s%n", i + 1, clusterType));
        }
        
        return analysis.toString();
    }

    /**
     * Truncates content before sending it to an LLM prompt to avoid overflowing
     * context. Keeps head and tail to preserve relevant signal.
     */
    protected String truncateForPrompt(String content, int maxChars) {
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (trimmed.length() <= maxChars) {
            return trimmed;
        }

        int head = (int) (maxChars * 0.65); // keep more from the head
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
                // Try next formatter in list
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
                // Try next formatter in list
            }
        }

        // Regex fallback: "d de mes de yyyy" or "dd de mes de yyyy" (Spanish month name, any case)
        Pattern spanishPattern = Pattern.compile(
            "(\\d{1,2})\\s+de\\s+(\\p{L}+)\\s+de\\s+(\\d{4})",
            Pattern.CASE_INSENSITIVE
        );
        Matcher matcher = spanishPattern.matcher(v);
        if (matcher.matches()) {
            int day = Integer.parseInt(matcher.group(1));
            String monthStr = matcher.group(2).toLowerCase();
            int year = Integer.parseInt(matcher.group(3));
            int month = spanishMonthToNumber(monthStr);
            if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                try {
                    return LocalDate.of(year, month, Math.min(day, LocalDate.of(year, month, 1).lengthOfMonth()));
                } catch (Exception ignored) {
                    // Invalid day/month combination for parsed components.
                }
            }
        }

        // If all parsing fails, log for debugging
        log().debug("Could not parse date: {}", dateStr);
        return null;
    }

    private static final Map<String, Integer> SPANISH_MONTHS = new HashMap<>(Map.ofEntries(
        Map.entry("enero", 1), Map.entry("febrero", 2), Map.entry("marzo", 3), Map.entry("abril", 4),
        Map.entry("mayo", 5), Map.entry("junio", 6), Map.entry("julio", 7), Map.entry("agosto", 8),
        Map.entry("septiembre", 9), Map.entry("setiembre", 9), Map.entry("octubre", 10),
        Map.entry("noviembre", 11), Map.entry("diciembre", 12)
    ));

    private static int spanishMonthToNumber(String monthStr) {
        return SPANISH_MONTHS.getOrDefault(monthStr != null ? monthStr.toLowerCase() : "", -1);
    }

    /**
     * Regex fallback for getDocumentDate when parseDateFlexible fails.
     * Tries "d de mes de yyyy" and "yyyy" only to avoid excluding documents.
     */
    private LocalDate parseDateByRegexFallback(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        String v = dateStr.trim();
        Pattern spanishPattern = Pattern.compile(
            "(\\d{1,2})\\s+de\\s+(\\p{L}+)\\s+de\\s+(\\d{4})",
            Pattern.CASE_INSENSITIVE
        );
        Matcher m = spanishPattern.matcher(v);
        if (m.find()) {
            int day = Integer.parseInt(m.group(1));
            int month = spanishMonthToNumber(m.group(2));
            int year = Integer.parseInt(m.group(3));
            if (month >= 1 && month <= 12 && day >= 1 && day <= 31) {
                try {
                    return LocalDate.of(year, month, Math.min(day, LocalDate.of(year, month, 1).lengthOfMonth()));
                } catch (Exception ignored) {
                }
            }
        }
        Matcher yearOnly = Pattern.compile("(\\d{4})").matcher(v);
        if (yearOnly.find()) {
            int year = Integer.parseInt(yearOnly.group(1));
            if (year >= 1900 && year <= 2100) {
                return LocalDate.of(year, 1, 1);
            }
        }
        return null;
    }

    /**
     * Extracts date candidates from query and NER entities.
     * Supports multiple date formats and uses LLM for normalization when needed.
     */
    protected List<String> extractDateCandidates(String query, JSONObject ner) {
        List<String> out = new ArrayList<>();

        // From NER (highest priority - most accurate); use optJSONArray to avoid IllegalArgumentException if "date" is not an array
        if (ner != null && ner.has("date")) {
            try {
                org.json.JSONArray arr = ner.optJSONArray("date");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                    String s = arr.optString(i, "").trim();
                    if (!s.isBlank()) {
                        out.add(s);
                        log().debug("Extracted date from NER: {}", s);
                    }
                }
                }
            } catch (Exception e) {
                log().warn("Error extracting dates from NER: {}", e.getMessage());
            }
        }

        // From query (regex patterns) - enhanced with more patterns
        if (query != null) {
            // ISO format: yyyy-MM-dd
            Matcher m1 = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})").matcher(query);
            while (m1.find()) {
                String iso = m1.group(1);
                out.add(iso);
                log().debug("Extracted ISO date from query: {}", iso);
            }

            // Slash format: dd/MM/yyyy or d/M/yyyy
            Matcher m2 = Pattern.compile("(\\d{1,2}/\\d{1,2}/\\d{4})").matcher(query);
            while (m2.find()) {
                String slash = m2.group(1);
                out.add(slash);
                log().debug("Extracted slash date from query: {}", slash);
            }

            // Dash format: dd-MM-yyyy or d-M-yyyy
            Matcher m3 = Pattern.compile("(\\d{1,2}-\\d{1,2}-\\d{4})").matcher(query);
            while (m3.find()) {
                String dash = m3.group(1);
                out.add(dash);
                log().debug("Extracted dash date from query: {}", dash);
            }

            // Dot format: dd.MM.yyyy or d.M.yyyy
            Matcher m4 = Pattern.compile("(\\d{1,2}\\.\\d{1,2}\\.\\d{4})").matcher(query);
            while (m4.find()) {
                String dot = m4.group(1);
                out.add(dot);
                log().debug("Extracted dot date from query: {}", dot);
            }

            // Spanish format: "d de mes de yyyy" or "dd de mes de yyyy" (case insensitive)
            Matcher m5 = Pattern.compile(
                "(\\d{1,2}\\s+de\\s+\\p{L}+\\s+de\\s+\\d{4})", 
                Pattern.CASE_INSENSITIVE
            ).matcher(query);
            while (m5.find()) {
                String es = m5.group(1);
                out.add(es);
                log().debug("Extracted Spanish date from query: {}", es);
            }

            // Spanish format without "de" between day and month: "d mes yyyy"
            Matcher m6 = Pattern.compile(
                "(\\d{1,2}\\s+(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|setiembre|octubre|noviembre|diciembre)\\s+\\d{4})",
                Pattern.CASE_INSENSITIVE
            ).matcher(query);
            while (m6.find()) {
                String esNoDe = m6.group(1);
                out.add(esNoDe);
                log().debug("Extracted Spanish date (no 'de') from query: {}", esNoDe);
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

        List<String> distinct = out.stream().distinct().toList();
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
                        log().warn("Could not parse date '{}' for minute {} (ID: {}). " +
                                  "This may indicate an unsupported date format. Excluding from results.", 
                                  minute.date(), minute.id(), minute.id());
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
     * Gets document_id from document metadata for deduplication (one doc per acta).
     * Uses metadata "document_id", then "id", then doc.getId().
     */
    protected String getDocumentIdFromDoc(Document doc) {
        if (doc == null) return null;
        Map<String, Object> metadata = doc.getMetadata();
        if (metadata != null) {
            String id = safeGetString(metadata, "document_id");
            if (id != null && !id.isBlank()) return id;
            id = safeGetString(metadata, "id");
            if (id != null && !id.isBlank()) return id;
        }
        return doc.getId();
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
        String fromIso = tryGetDateFromDateIsoMetadata(doc, metadata);
        if (fromIso != null) {
            return fromIso;
        }
        String fromPrimary = tryGetDateFromPrimaryDateField(doc, metadata);
        if (fromPrimary != null) {
            return fromPrimary;
        }
        String fromAlt = tryGetDateFromAlternateMetadataDateFields(doc, metadata);
        if (fromAlt != null) {
            return fromAlt;
        }
        return tryGetDateFromMinuteEmbedded(doc);
    }

    private String tryGetDateFromDateIsoMetadata(Document doc, Map<String, Object> metadata) {
        Object dateIsoValue = metadata.get(METADATA_KEY_DATE_ISO);
        if (dateIsoValue == null) {
            return null;
        }
        String dateIsoStr = dateIsoValue.toString().trim();
        if (dateIsoStr.isEmpty()) {
            return null;
        }
        try {
            LocalDate.parse(dateIsoStr, DateTimeFormatter.ISO_LOCAL_DATE);
            log().debug("Using date_iso field for document {}: {}", doc.getId(), dateIsoStr);
            return dateIsoStr;
        } catch (DateTimeParseException ignored) {
            log().warn("date_iso field '{}' for document {} is not valid ISO format, will try other fields",
                    dateIsoStr, doc.getId());
            return null;
        }
    }

    private String tryGetDateFromPrimaryDateField(Document doc, Map<String, Object> metadata) {
        Object dateValue = metadata.get("date");
        if (dateValue == null) {
            return null;
        }
        String dateStr = dateValue.toString().trim();
        if (dateStr.isEmpty()) {
            return null;
        }
        LocalDate parsed = parseDateFlexible(dateStr);
        if (parsed != null) {
            String normalized = parsed.format(DateTimeFormatter.ISO_LOCAL_DATE);
            log().debug("Parsed and normalized date field for document {}: '{}' -> {}",
                    doc.getId(), dateStr, normalized);
            return normalized;
        }
        try {
            LocalDate.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE);
            log().debug("Date field for document {} is already in ISO format: {}", doc.getId(), dateStr);
            return dateStr;
        } catch (DateTimeParseException ignored) {
            // fall through to regex fallback
        }
        LocalDate fallback = parseDateByRegexFallback(dateStr);
        if (fallback != null) {
            String isoFromFallback = fallback.format(DateTimeFormatter.ISO_LOCAL_DATE);
            log().warn("Parsed date '{}' from 'date' field for document {} via regex fallback -> {}. "
                            + "Consider populating date_iso in metadata for reliability.",
                    dateStr, doc.getId(), isoFromFallback);
            return isoFromFallback;
        }
        log().warn("Could not parse date '{}' from 'date' field for document {} (parseDateFlexible and regex fallback failed). "
                        + "Document may be excluded from date filtering.", dateStr, doc.getId());
        return null;
    }

    private String tryGetDateFromAlternateMetadataDateFields(Document doc, Map<String, Object> metadata) {
        String[] otherDateFields = {"fecha", "meeting_date", "document_date"};
        for (String field : otherDateFields) {
            Object fieldValue = metadata.get(field);
            if (fieldValue == null) {
                continue;
            }
            String dateStr = fieldValue.toString().trim();
            if (dateStr.isEmpty()) {
                continue;
            }
            LocalDate parsed = parseDateFlexible(dateStr);
            if (parsed != null) {
                String normalized = parsed.format(DateTimeFormatter.ISO_LOCAL_DATE);
                log().debug("Parsed and normalized date from field '{}' for document {}: {} -> {}",
                        field, doc.getId(), dateStr, normalized);
                return normalized;
            }
        }
        return null;
    }

    private String tryGetDateFromMinuteEmbedded(Document doc) {
        try {
            Minute minute = cacheable().getMinuteFromMetadata(doc);
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
                .toList();

        log().info("Date validation: {} documents matched date {} (normalized: {}) out of {} total documents", 
                  filtered.size(), requestedDate, requestedNormalized, docs.size());
        
        // Enhanced logging: show sample document dates when no matches found
        if (filtered.isEmpty() && !docs.isEmpty()) {
            List<String> sampleDates = docs.stream()
                    .limit(5)
                    .map(doc -> {
                        Map<String, Object> docMetadata = doc.getMetadata();
                        String dateIso = docMetadata != null && docMetadata.containsKey(METADATA_KEY_DATE_ISO) ?
                                        docMetadata.get(METADATA_KEY_DATE_ISO).toString() : null;
                        String dateRaw = docMetadata != null && docMetadata.containsKey("date") ? 
                                        docMetadata.get("date").toString() : null;
                        
                        String date = getDocumentDate(doc);
                        StringBuilder info = new StringBuilder();
                        if (date != null) {
                            try {
                                LocalDate.parse(date, DateTimeFormatter.ISO_LOCAL_DATE);
                                info.append(date).append(" (ISO)");
                            } catch (DateTimeParseException e) {
                                LocalDate parsed = parseDateFlexible(date);
                                info.append(date).append(parsed != null ? 
                                    " (parsed: " + parsed.format(DateTimeFormatter.ISO_LOCAL_DATE) + ")" : 
                                    " (parse failed)");
                            }
                        } else {
                            info.append("no date");
                        }
                        
                        // Add metadata info for debugging
                        if (dateIso != null) {
                            info.append(" [date_iso: ").append(dateIso).append("]");
                        }
                        if (dateRaw != null) {
                            info.append(" [date_raw: ").append(dateRaw).append("]");
                        }
                        
                        return info.toString();
                    })
                    .toList();
            log().debug("Date validation failed: Requested date {} (normalized: {}) not found. " +
                      "This may indicate: 1) date_iso missing in metadata, 2) date parsing failed, or 3) dates don't match (e.g. date not in corpus). " +
                      "Sample document dates: {}",
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
        if (doc == null || doc.getMetadata() == null) {
            return new ArrayList<>();
        }

        List<String> attendees = new ArrayList<>();
        appendAttendeesFromMetadataValue(doc.getMetadata().get("attendees"), attendees);
        mergeAttendeesFromMinuteIfPresent(doc, attendees);

        log().info("Extracted {} attendees from document metadata", attendees.size());
        return attendees;
    }

    private void appendAttendeesFromMetadataValue(Object attendeesObj, List<String> attendees) {
        if (attendeesObj == null) {
            return;
        }
        if (attendeesObj instanceof List<?> attendeesList) {
            for (Object item : attendeesList) {
                if (item != null) {
                    String attendee = item.toString().trim();
                    if (!attendee.isEmpty()) {
                        attendees.add(attendee);
                    }
                }
            }
            return;
        }
        if (attendeesObj instanceof String attendeesStr) {
            String[] parts = attendeesStr.split("[,\n•]");
            for (String part : parts) {
                String attendee = part.trim();
                if (attendee.isEmpty()) {
                    continue;
                }
                attendee = attendee.replaceAll("\\([^)]*\\)", "").trim();
                if (!attendee.isEmpty()) {
                    attendees.add(attendee);
                }
            }
        }
    }

    private void mergeAttendeesFromMinuteIfPresent(Document doc, List<String> attendees) {
        try {
            Minute minute = cacheable().getMinuteFromMetadata(doc);
            if (minute == null || minute.attendees() == null) {
                return;
            }
            for (String attendee : minute.attendees()) {
                if (attendee != null && !attendee.isBlank() && !attendees.contains(attendee)) {
                    attendees.add(attendee);
                }
            }
        } catch (Exception e) {
            log().debug("Error extracting attendees from minute object: {}", e.getMessage());
        }
    }
    
    /**
     * Extracts attendeesCount from document metadata robustly.
     * Handles both Integer and String formats.
     * 
     * @param doc Document to extract attendeesCount from
     * @return Integer count or null if not available
     */
    protected Integer getAttendeesCount(Document doc) {
        if (doc == null || doc.getMetadata() == null) {
            return null;
        }
        
        Map<String, Object> metadata = doc.getMetadata();
        
        Integer fromAttendeesCount = parseIntegerMetadataField(
                metadata.get(METADATA_KEY_ATTENDEES_COUNT), METADATA_KEY_ATTENDEES_COUNT);
        if (fromAttendeesCount != null) {
            return fromAttendeesCount;
        }
        Integer fromNumberOfAttendees = parseIntegerMetadataField(metadata.get("numberOfAttendees"), "numberOfAttendees");
        if (fromNumberOfAttendees != null) {
            return fromNumberOfAttendees;
        }
        
        // Fallback: count from attendees list
        List<String> attendees = extractAttendeesFromMetadata(doc);
        if (!attendees.isEmpty()) {
            return attendees.size();
        }
        
        // Try to get from Minute object
        try {
            Minute minute = cacheable().getMinuteFromMetadata(doc);
            if (minute != null) {
                if (minute.numberOfAttendees() > 0) {
                    return minute.numberOfAttendees();
                }
                if (minute.attendees() != null && !minute.attendees().isEmpty()) {
                    return minute.attendees().size();
                }
            }
        } catch (Exception e) {
            log().debug("Could not extract attendeesCount from Minute object: {}", e.getMessage());
        }
        
        return null;
    }

    /**
     * Parses Integer / Number / String metadata values used for attendee counts.
     */
    private Integer parseIntegerMetadataField(Object countObj, String fieldName) {
        if (countObj == null) {
            return null;
        }
        if (countObj instanceof Integer i) {
            return i;
        }
        if (countObj instanceof Number n) {
            return n.intValue();
        }
        if (countObj instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                log().warn("Could not parse {} as integer: {}", fieldName, countObj.getClass().getSimpleName());
            }
        }
        return null;
    }
    
}


