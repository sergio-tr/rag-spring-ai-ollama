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

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

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
                    .content()
                    .strip()
                    .toLowerCase();

            return result.contains("yes") || result.contains("sí");
        } catch (Exception e) {
            log().warn("Error in semantic metadata matching, defaulting to false", e);
            return false; // Default to false on error to avoid false positives
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
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip()
                    .toLowerCase();

            return result.contains("yes") || result.contains("sí");
        } catch (Exception e) {
            log().warn("Error in semantic minute matching, defaulting to false", e);
            return false; // Default to false on error to avoid false positives
        }
    }

    /**
     * Extracts and deserializes the Minute object from Document metadata.
     * First tries to get the complete object from "minute" key (JSON or object).
     * If not available, reconstructs it from individual metadata fields.
     */
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
                log().debug("Failed to deserialize Minute from JSON, attempting reconstruction from fields", ex);
                // Fall through to reconstruction
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
        // Extract all fields with proper type handling
        String id = getStringValue(metadata, "id");
        String filename = getStringValue(metadata, "filename");
        String date = getStringValue(metadata, "date");
        String place = getStringValue(metadata, "place");
        String startTime = getStringValue(metadata, "startTime");
        String endTime = getStringValue(metadata, "endTime");
        String president = getStringValue(metadata, "president");
        String secretary = getStringValue(metadata, "secretary");
        
        @SuppressWarnings("unchecked")
        List<String> attendees = (List<String>) metadata.getOrDefault("attendees", new ArrayList<>());
        
        int numberOfAttendees = metadata.containsKey("numberOfAttendees") 
            ? ((Number) metadata.get("numberOfAttendees")).intValue() 
            : attendees.size();
        
        @SuppressWarnings("unchecked")
        Map<String, String> agenda = metadata.containsKey("agenda") && metadata.get("agenda") instanceof Map
            ? (Map<String, String>) metadata.get("agenda")
            : new LinkedHashMap<>();
        
        @SuppressWarnings("unchecked")
        List<String> decisions = (List<String>) metadata.getOrDefault("decisions", new ArrayList<>());
        
        @SuppressWarnings("unchecked")
        List<String> mentionedEntities = (List<String>) metadata.getOrDefault("mentionedEntities", new ArrayList<>());
        
        @SuppressWarnings("unchecked")
        List<String> topics = (List<String>) metadata.getOrDefault("topics", new ArrayList<>());
        
        String summary = getStringValue(metadata, "summary");
        
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
     * Safely extracts a string value from metadata.
     */
    private String getStringValue(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    /**
     * Checks if minute semantically matches NER entities.
     * Uses English for internal processing, but preserves original language in metadata values.
     * 
     * MEJORA: Added direct filtering by mentionedEntities before LLM call for better efficiency.
     */
    protected boolean matchesMinuteWithNER(Minute minute, JSONObject ner) {
        if (ner == null || ner.isEmpty()) return true;

        if (ner.has("mentionedEntities") && !ner.getJSONArray("mentionedEntities").isEmpty()) {
            if (!matchesMentionedEntities(minute, ner)) {
                log().debug("Minute {} filtered out by mentionedEntities mismatch", minute.id());
                return false;
            }
        }

        if (ner.has("agenda") && !ner.getJSONArray("agenda").isEmpty()) {
            if (!matchesAgendaItems(minute, ner)) {
                log().debug("Minute {} filtered out by agenda items mismatch", minute.id());
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
            log().warn("Error matching minute with NER, defaulting to false", e);
            return false; // Default to false on error to avoid false positives
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
                    log().debug("Found matching agenda item: NER='{}' matches Minute agenda", nerAgendaItem);
                    return true; // At least one match found
                }
            }
            
            log().debug("No matching agenda items found. NER agenda: {}, Minute agenda: {}", 
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
                        log().debug("Found matching entity: NER='{}' matches Minute='{}'", nerEntity, minuteEntity);
                        return true; // At least one match found
                    }
                }
            }
            
            log().debug("No matching entities found. NER entities: {}, Minute entities: {}", 
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

    // ============================================================================
    // COMMON METHODS FOR METADATA TOOLS - EXTRACTED FROM IMPROVED TOOLS
    // ============================================================================

    /**
     * Extracts minutes from documents in parallel for better performance
     */
    protected List<Minute> extractMinutesInParallel(List<Document> docs) {
        return docs.parallelStream()
                .map(this::getMinuteFromMetadataCached)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Cached extraction of minute objects to improve performance
     */
    @Cacheable(value = "minuteObjects", key = "#doc.id")
    protected Minute getMinuteFromMetadataCached(Document doc) {
        return getMinuteFromMetadata(doc);
    }

    /**
     * Filters relevant minutes based on NER or query relevance using EnhancedNERHandler.
     * Implements progressive fallback when filters are too strict.
     */
    protected List<Minute> filterRelevantMinutes(String query, List<Minute> minutes, JSONObject ner) {
        if (minutes.isEmpty()) {
            return minutes;
        }
        
        List<Minute> filtered;
        
        if (ner != null && !ner.isEmpty()) {
            // Use enhanced NER filtering with temporal context
            List<Minute> temporalFiltered = nerHandler.filterMinutesByTemporalContext(minutes, ner);
            
            // Filter by NER matching
            filtered = temporalFiltered.stream()
                    .filter(minute -> nerHandler.matchesMinuteWithNER(minute, ner))
                    .filter(minute -> isRelevantToQueryCached(query, minute))
                    .collect(Collectors.toList());
            
            if (filtered.isEmpty() && !temporalFiltered.isEmpty()) {
                log().debug("All minutes filtered out by NER matching, trying fallback: only temporal + relevance");
                // Fallback 1: Skip NER matching, keep temporal + relevance
                filtered = temporalFiltered.stream()
                        .filter(minute -> isRelevantToQueryCached(query, minute))
                        .collect(Collectors.toList());
            }
            
            if (filtered.isEmpty() && !minutes.isEmpty()) {
                log().debug("All minutes filtered out even with fallback, trying: only relevance");
                // Fallback 2: Skip all NER filters, only relevance
                filtered = filterMinutesByQueryRelevance(query, minutes);
            }
        } else {
            filtered = filterMinutesByQueryRelevance(query, minutes);
        }
        
        // Final fallback: if still empty and we have minutes, return at least some
        if (filtered.isEmpty() && !minutes.isEmpty()) {
            log().warn("All filtering failed, returning first {} minutes as last resort", Math.min(3, minutes.size()));
            return minutes.stream().limit(3).collect(Collectors.toList());
        }
        
        log().debug("Filtered {} relevant minutes from {} total", filtered.size(), minutes.size());
        return filtered;
    }

    /**
     * Filters minutes using NER entities intelligently
     */
    protected List<Minute> filterMinutesWithNER(String query, List<Minute> minutes, JSONObject ner) {
        log().debug("Filtering {} minutes with NER entities", minutes.size());
        
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
        log().debug("Filtering {} minutes by query relevance", minutes.size());
        
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
     */
    protected boolean isRelevantToQueryByLLM(String query, Minute minute) {
        if (query == null || query.trim().isEmpty() || minute == null) {
            return false;
        }
        
        String prompt = generateRelevancePrompt(query, minute);
        String result = getLLMResponseCached(prompt);
        
        if (result == null || result.trim().isEmpty()) {
            log().warn("Empty response from LLM in isRelevantToQueryByLLM, defaulting to false");
            return false;
        }
        
        String normalized = result.toLowerCase();
        return normalized.contains("yes") || normalized.contains("sí");
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
     * 
     * MEJORA 2: Groups chunks by document_id to avoid processing the same document multiple times.
     * This significantly improves performance by eliminating duplicate Minute reconstructions.
     */
    protected List<Document> retrieveDocumentsWithMetadataFilter(String query, String[] relevantFields) {
        List<Document> docs = retrieveAllDocuments(query);
        
        // Filter by metadata fields and relevance
        List<Document> metadataDocs = docs.stream()
                .filter(doc -> hasMetadataFields(doc, relevantFields))
                .filter(doc -> hasRelevantMetadata(doc, query, relevantFields))
                .collect(Collectors.toList());
        
        // When PgVectorStore splits a document into chunks, all chunks have the same document_id
        // We only need to process one chunk per document to reconstruct the Minute object
        Map<String, Document> uniqueDocuments = metadataDocs.stream()
                .collect(Collectors.toMap(
                    doc -> getDocumentId(doc),  // Use document_id as key
                    doc -> doc,                 // Keep the document
                    (existing, replacement) -> existing  // If duplicate, keep the first one
                ));
        
        List<Document> deduplicatedDocs = new ArrayList<>(uniqueDocuments.values());
        
        log().debug("Filtered {} unique documents from {} chunks ({} total retrieved)", 
                   deduplicatedDocs.size(), metadataDocs.size(), docs.size());
        return deduplicatedDocs;
    }
    
    /**
     * Extracts the document_id from a document's metadata.
     * Falls back to the document's id if document_id is not present (for backward compatibility).
     * 
     * MEJORA 2: Helper method to get document identifier for deduplication.
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
        
        // Last resort: use document's own id
        // Note: This might be a chunk id, not the document id, but it's better than nothing
        return doc.getId();
    }
    
    /**
     * Checks if a document has the necessary metadata fields.
     * Returns true if it has "minute" key OR has at least one of the relevant fields.
     */
    private boolean hasMetadataFields(Document doc, String[] relevantFields) {
        Map<String, Object> metadata = doc.getMetadata();
        
        // If it has the "minute" key, it's valid
        if (metadata.containsKey("minute")) {
            return true;
        }
        
        // Otherwise, check if it has at least one relevant field
        for (String field : relevantFields) {
            if (metadata.containsKey(field)) {
                return true;
            }
        }
        
        // Also check for basic metadata fields that indicate it's a minute document
        return metadata.containsKey("date") || metadata.containsKey("id") || metadata.containsKey("filename");
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
}


