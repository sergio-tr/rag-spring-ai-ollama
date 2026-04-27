package com.uniovi.rag.tool.metadata;

import com.uniovi.rag.domain.model.*;
import com.uniovi.rag.service.extraction.DocumentContentExtractor;
import com.uniovi.rag.service.retriever.ContextRetriever;
import com.uniovi.rag.tool.ToolExecutionContext;
import com.uniovi.rag.tool.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import static com.uniovi.rag.infrastructure.observability.ContextPropagatingFutures.supplyAsync;
import java.util.stream.Collectors;

/**
 * Enhanced MetadataExtractEntitiesTool for extracting and analyzing entities from meeting minutes with intelligent processing.
 */
public class MetadataExtractEntitiesTool extends AbstractMetadataTool {

    private static final String META_FIELD_TOPICS = "topics";

    private static final String META_FIELD_PRESIDENT = "president";

    private static final String META_FIELD_SECRETARY = "secretary";

    private static final String META_FIELD_DATE = "date";

    private static final String META_FIELD_PLACE = "place";

    private static final String META_FIELD_DECISIONS = "decisions";

    private static final String META_FIELD_SUMMARY = "summary";

    private static final String META_FIELD_ATTENDEES = "attendees";

    private static final String[] ENTITY_RETRIEVAL_FIELD_NAMES = {
        META_FIELD_DATE,
        META_FIELD_PLACE,
        META_FIELD_TOPICS,
        META_FIELD_DECISIONS,
        META_FIELD_SUMMARY,
        META_FIELD_PRESIDENT,
        META_FIELD_SECRETARY,
        META_FIELD_ATTENDEES
    };

    public MetadataExtractEntitiesTool(ChatClient chatClient, ContextRetriever retriever, DocumentContentExtractor extractor,
            MetadataLlmResponseCacheService llmResponseCache) {
        super(chatClient, retriever, extractor, llmResponseCache);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();

        log().info("Executing entity extraction query: {} with NER: {}", query, ner != null ? ner.toString() : "null");

        LoadedCorpus corpus = loadDocumentsAndRelevantMinutes(query, ner);
        if (corpus == null) {
            return ToolResult.from(formatResponse(generateEntityNotFoundMessage(query), query), getClass());
        }

        ToolResult shortcut = tryMetadataShortcutResponses(query, ner, corpus);
        if (shortcut != null) {
            return shortcut;
        }

        List<Minute> relevantMinutes = corpus.relevantMinutes();

        // Step 3.5: Additional filtering by topic + person if query requires it
        // Example: "Dime qué actas mencionan el ascensor y fueron presididas por Juan Pérez Gutiérrez"
        if (requiresTopicAndPersonFilter(query)) {
            List<Minute> topicPersonFiltered = filterMinutesByTopicAndPerson(query, relevantMinutes, ner);
            log().info("Filtered {} minutes by topic + person, {} remaining (applied filter even if empty)", 
                      relevantMinutes.size(), topicPersonFiltered.size());
            relevantMinutes = topicPersonFiltered; // Apply filter even if empty - this indicates no matches
        }

        // Step 4: Extract entities in parallel (prioritize metadata)
        List<Entity> entities = extractEntitiesInParallel(query, relevantMinutes, corpus.docs());
        if (entities.isEmpty()) {
            log().info("No relevant entities found for query: {}", query);
            return ToolResult.from(formatResponse(generateEntityNotFoundMessage(query), query), getClass());
        }

        // Step 5: Deduplicate and order entities (metadata-first heuristic)
        List<Entity> rankedEntities = deduplicateAndRankEntities(entities);

        // Step 6: Cluster similar entities
        List<EntityCluster> clusters = clusterEntities(rankedEntities);

        // Step 7: Generate enhanced final answer
        String answer = generateEnhancedEntityAnswer(query, rankedEntities, clusters);
        log().info("Generated entity extraction answer for query: {} with {} entities in {} clusters", 
                   query, entities.size(), clusters.size());
        
        return ToolResult.from(formatResponse(answer, query), getClass());
    }

    private record LoadedCorpus(List<Document> docs, List<Minute> relevantMinutes) {}

    private LoadedCorpus loadDocumentsAndRelevantMinutes(String query, JSONObject ner) {
        List<Document> docs = retrieveDocumentsWithFallback(query, ENTITY_RETRIEVAL_FIELD_NAMES, ner);
        if (docs.isEmpty()) {
            log().info("No documents found for entity extraction query: {}", query);
            return null;
        }
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().info("No valid minutes found for entity extraction query: {}", query);
            return null;
        }
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().info("No relevant minutes found for entity extraction query: {}", query);
            return null;
        }
        return new LoadedCorpus(docs, relevantMinutes);
    }

    /**
     * Fast paths that only need metadata (role, lists, sections); {@code null} means continue with full extraction.
     */
    private ToolResult tryMetadataShortcutResponses(String query, JSONObject ner, LoadedCorpus corpus) {
        List<Minute> relevantMinutes = corpus.relevantMinutes();
        if (asksForPresidentOrSecretaryOnly(query)) {
            String requestedDate = extractDateFromQuery(query, ner);
            List<Minute> forDate = requestedDate != null ? filterMinutesByDate(query, ner, relevantMinutes) : relevantMinutes;
            if (!forDate.isEmpty()) {
                Minute target = forDate.get(0);
                String roleValue = asksForPresidentOnly(query) ? target.president() : target.secretary();
                if (roleValue != null && !roleValue.isBlank()) {
                    log().info("Returning single role (president/secretary) for query: {}", query);
                    return ToolResult.from(formatResponse(roleValue, query), getClass());
                }
            }
        }
        ListableEntity listType = getRequestedListableEntity(query);
        if (listType != null) {
            List<String> items = buildListFromMinutes(listType, relevantMinutes);
            if (!items.isEmpty()) {
                String answer = formatListAnswer(items, listType);
                log().info("Returning list of {} {} for query (no entity extraction)", items.size(), listType);
                return ToolResult.from(formatResponse(answer, query), getClass());
            }
        }
        if (asksForSectionsOrStructure(query)) {
            List<Minute> forDate = filterMinutesByDate(query, ner, relevantMinutes);
            List<Minute> target = forDate.isEmpty() ? relevantMinutes : forDate;
            if (!target.isEmpty()) {
                String sectionsAnswer = buildSectionsAnswerFromMinute(target.get(0));
                log().info("Returning minute sections/structure for query (no entity extraction)");
                return ToolResult.from(formatResponse(sectionsAnswer, query), getClass());
            }
        }
        return null;
    }

    /**
     * Extracts entities in parallel, prioritizing metadata from documents
     */
    private List<Entity> extractEntitiesInParallel(String query, List<Minute> minutes, List<Document> docs) {
        List<Entity> entities = new ArrayList<>();
        
        // First: Extract attendees from document metadata (highest priority)
        if (query != null && (query.toLowerCase().contains("asistente") || query.toLowerCase().contains("attendee") || 
            query.toLowerCase().contains("quién") || query.toLowerCase().contains("who"))) {
            for (Document doc : docs) {
                List<String> attendees = extractAttendeesFromMetadata(doc);
                for (String attendee : attendees) {
                    if (attendee != null && !attendee.isBlank() && !isGenericEntity(attendee)) {
                        entities.add(new Entity(
                            attendee,
                            EntityType.PERSON,
                            EntityRole.ATTENDEE
                        ));
                    }
                }
            }
        }
        
        // Second: Extract entities from minutes in parallel
        List<CompletableFuture<List<Entity>>> futures = minutes.stream()
                .map(minute -> supplyAsync(() -> extractEntitiesFromMinute(query, minute)))
                .toList();

        List<Entity> minuteEntities = futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .filter(e -> !isGenericEntity(e.getName())) // Filter out generic entities
                .toList();
        
        entities.addAll(minuteEntities);

        return entities;
    }

    /**
     * Extracts entities from a single minute
     */
    private List<Entity> extractEntitiesFromMinute(String query, Minute minute) {
        List<Entity> entities = new ArrayList<>();
        
        // Extract structured entities from metadata
        entities.addAll(extractStructuredEntities(minute));
        
        // Extract entities from text content using LLM (fallback)
        entities.addAll(extractTextEntities(query, minute));
        
        return entities;
    }

    /**
     * Extracts structured entities from minute metadata
     */
    private List<Entity> extractStructuredEntities(Minute minute) {
        List<Entity> entities = new ArrayList<>();
        
        // Extract president
        if (minute.president() != null && !minute.president().isBlank()) {
            entities.add(new Entity(
                minute.president(),
                EntityType.PERSON,
                EntityRole.PRESIDENT
            ));
        }
        
        // Extract secretary
        if (minute.secretary() != null && !minute.secretary().isBlank()) {
            entities.add(new Entity(
                minute.secretary(),
                EntityType.PERSON,
                EntityRole.SECRETARY
            ));
        }
        
        // Extract attendees (filter out generic entities)
        if (minute.attendees() != null) {
            for (String attendee : minute.attendees()) {
                if (attendee != null && !attendee.isBlank() && !isGenericEntity(attendee)) {
                    entities.add(new Entity(
                        attendee,
                        EntityType.PERSON,
                        EntityRole.ATTENDEE
                    ));
                }
            }
        }
        
        // Extract place as location entity
        if (minute.place() != null && !minute.place().isBlank()) {
            entities.add(new Entity(
                minute.place(),
                EntityType.LOCATION,
                EntityRole.MEETING_PLACE
            ));
        }
        
        // Extract mentionedEntities directly from metadata (filter out generic entities)
        if (minute.mentionedEntities() != null && !minute.mentionedEntities().isEmpty()) {
            for (String entity : minute.mentionedEntities()) {
                if (entity != null && !entity.isBlank() && !isGenericEntity(entity)) {
                    // Try to infer type: if it's a person name (capitalized words), treat as PERSON
                    EntityType entityType = inferEntityType(entity);
                    entities.add(new Entity(
                        entity,
                        entityType,
                        EntityRole.UNKNOWN
                    ));
                }
            }
        }
        
        return entities;
    }

    /**
     * Infers entity type from entity name using simple heuristics
     */
    private EntityType inferEntityType(String entityName) {
        if (entityName == null || entityName.isBlank()) {
            return EntityType.OTHER;
        }
        
        // Bounded repetition avoids pathological backtracking on long inputs (Sonar S5998).
        if (entityName.length() <= 256
                && entityName.matches("^[A-ZÁÉÍÓÚÑ][a-záéíóúñ]{1,48}(\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]{1,48}){1,8}$")) {
            return EntityType.PERSON;
        }
        
        // If it contains organization keywords
        String lower = entityName.toLowerCase();
        if (lower.contains("sociedad") || lower.contains("asociación") || lower.contains("comunidad") ||
            lower.contains("empresa") || lower.contains("corporación") || lower.contains("s.a.") ||
            lower.contains("s.l.") || lower.contains("s.c.")) {
            return EntityType.ORGANIZATION;
        }
        
        return EntityType.OTHER;
    }

    /**
     * Extracts entities from text content using LLM
     */
    private List<Entity> extractTextEntities(String query, Minute minute) {
        List<Entity> entities = new ArrayList<>();
        
        // Extract from decisions
        if (minute.decisions() != null) {
            for (String decision : minute.decisions()) {
                entities.addAll(extractEntitiesFromText(decision, EntityType.DECISION, minute));
            }
        }
        
        // Extract from topics
        if (minute.topics() != null) {
            for (String topic : minute.topics()) {
                entities.addAll(extractEntitiesFromText(topic, EntityType.TOPIC, minute));
            }
        }
        
        // Extract from summary
        if (minute.summary() != null && !minute.summary().isBlank()) {
            entities.addAll(extractEntitiesFromText(minute.summary(), EntityType.SUMMARY, minute));
        }
        
        return entities;
    }

    /**
     * Extracts entities from text using LLM.
     * Uses English for internal processing, but preserves original language in text.
     */
    private List<Entity> extractEntitiesFromText(String text, EntityType contextType, Minute minute) {
        if (text == null || text.trim().isEmpty() || minute == null) {
            return Collections.emptyList();
        }
        
        String prompt = String.format("""
            Given the following text (may be in any language):
            "%s"
            
            Extract all relevant entities (people, organizations, roles, dates, amounts, etc.) from this text.
            For each entity, provide:
            1. The entity name
            2. The entity type (PERSON, ORGANIZATION, ROLE, DATE, AMOUNT, OTHER)
            3. The role or context (if applicable)
            
            Format the response as a JSON array of objects with keys: name, type, role, context.
            """, text);
        
        try {
            String result = getLLMResponseCached(prompt);
            
            if (result == null || result.trim().isEmpty()) {
                log().info("Empty response from LLM in extractEntitiesFromText, returning empty list");
                return Collections.emptyList();
            }
            
            return parseEntitiesFromLLMResponse(result.strip(), contextType, minute);
        } catch (Exception e) {
            log().error("Error extracting entities from text, returning empty list", e);
            return Collections.emptyList();
        }
    }

    /**
     * Parses entities from LLM response using JSON parser
     */
    private List<Entity> parseEntitiesFromLLMResponse(String response, EntityType contextType, Minute minute) {
        List<Entity> entities = new ArrayList<>();
        
        try {
            // Try to extract JSON array from response
            String jsonStr = response.trim();
            
            // Find JSON array in response (may be wrapped in markdown code blocks or text)
            int arrayStart = jsonStr.indexOf('[');
            int arrayEnd = jsonStr.lastIndexOf(']');
            
            if (arrayStart == -1 || arrayEnd == -1 || arrayEnd <= arrayStart) {
                // Fallback to line-by-line parsing if no JSON array found
                return parseEntitiesFromLines(response);
            }
            
            jsonStr = jsonStr.substring(arrayStart, arrayEnd + 1);
            
            // Parse JSON array
            org.json.JSONArray jsonArray = new org.json.JSONArray(jsonStr);
            
            for (int i = 0; i < jsonArray.length(); i++) {
                org.json.JSONObject obj = jsonArray.getJSONObject(i);
                String name = obj.optString("name", "").trim();
                String type = obj.optString("type", "OTHER").trim();
                String role = obj.optString("role", "").trim();
                
                if (!name.isEmpty()) {
                    EntityType entityType = parseEntityType(type);
                    EntityRole entityRole = parseEntityRole(role);
                    
                    entities.add(new Entity(
                        name,
                        entityType,
                        entityRole
                    ));
                }
            }
        } catch (org.json.JSONException e) {
            log().info("Error parsing JSON from LLM response, trying line-by-line parsing: {}", e.getMessage());
            // Fallback to line-by-line parsing
            return parseEntitiesFromLines(response);
        } catch (Exception e) {
            log().info("Error parsing entities from LLM response: {}", e.getMessage());
        }
        
        return entities;
    }

    /**
     * Fallback parsing method for non-JSON responses
     */
    private List<Entity> parseEntitiesFromLines(String response) {
        List<Entity> entities = new ArrayList<>();
        
        try {
            String[] lines = response.split("\n");
            for (String line : lines) {
                if (line.contains("name") && line.contains("type")) {
                    // Extract entity information (simplified parsing)
                    String name = extractValueFromLine(line, "name");
                    String type = extractValueFromLine(line, "type");
                    String role = extractValueFromLine(line, "role");
                    
                    if (name != null && type != null) {
                        EntityType entityType = parseEntityType(type);
                        EntityRole entityRole = parseEntityRole(role);
                        
                        entities.add(new Entity(
                            name,
                            entityType,
                            entityRole
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log().info("Error in fallback line parsing: {}", e.getMessage());
        }
        
        return entities;
    }

    /**
     * Extracts value from a line of text
     */
    private String extractValueFromLine(String line, String key) {
        try {
            int start = line.indexOf("\"" + key + "\"");
            if (start == -1) return null;
            
            int valueStart = line.indexOf("\"", start + key.length() + 3);
            if (valueStart == -1) return null;
            
            int valueEnd = line.indexOf("\"", valueStart + 1);
            if (valueEnd == -1) return null;
            
            return line.substring(valueStart + 1, valueEnd);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Parses entity type from string
     */
    private EntityType parseEntityType(String type) {
        switch (type.toUpperCase()) {
            case "PERSON":
                return EntityType.PERSON;
            case "ORGANIZATION":
                return EntityType.ORGANIZATION;
            case "ROLE":
                return EntityType.ROLE;
            case "DATE":
                return EntityType.DATE;
            case "AMOUNT":
                return EntityType.AMOUNT;
            default:
                return EntityType.OTHER;
        }
    }

    /**
     * Parses entity role from string
     */
    private EntityRole parseEntityRole(String role) {
        if (role == null) return EntityRole.UNKNOWN;
        
        switch (role.toUpperCase()) {
            case "PRESIDENT":
                return EntityRole.PRESIDENT;
            case "SECRETARY":
                return EntityRole.SECRETARY;
            case "ATTENDEE":
                return EntityRole.ATTENDEE;
            case "MEETING_PLACE":
                return EntityRole.MEETING_PLACE;
            default:
                return EntityRole.UNKNOWN;
        }
    }

    /**
     * Analyzes and ranks entities by relevance and quality
     */
    private List<Entity> deduplicateAndRankEntities(List<Entity> entities) {
        // Deduplicate by name+type+role
        Map<String, Entity> dedup = new LinkedHashMap<>();
        for (Entity e : entities) {
            if (e == null || e.getName() == null || e.getName().isBlank()) continue;
            String key = (e.getName().trim().toLowerCase()) + "|" + e.getType() + "|" + e.getRole();
            dedup.putIfAbsent(key, e);
        }

        // Order: structured types first (PERSON, ROLE, LOCATION), then ORGANIZATION, then others; within type by name length desc
        return dedup.values().stream()
                .sorted((a, b) -> {
                    int typeOrder = Integer.compare(typePriority(b.getType()), typePriority(a.getType()));
                    if (typeOrder != 0) return typeOrder;
                    int roleOrder = Integer.compare(rolePriority(b.getRole()), rolePriority(a.getRole()));
                    if (roleOrder != 0) return roleOrder;
                    return Integer.compare(
                            b.getName() != null ? b.getName().length() : 0,
                            a.getName() != null ? a.getName().length() : 0);
                })
                .toList();
    }

    private int typePriority(EntityType type) {
        return switch (type) {
            case PERSON, ROLE, LOCATION -> 3;
            case ORGANIZATION -> 2;
            case TOPIC, DECISION -> 1;
            default -> 0;
        };
    }

    private int rolePriority(EntityRole role) {
        return switch (role) {
            case PRESIDENT, SECRETARY -> 3;
            case ATTENDEE, MEETING_PLACE -> 2;
            default -> 0;
        };
    }

    /**
     * Clusters similar entities to avoid redundancy
     */
    private List<EntityCluster> clusterEntities(List<Entity> entities) {
        List<EntityCluster> clusters = new ArrayList<>();
        
        for (Entity entity : entities) {
            boolean addedToCluster = false;
            
            // Try to add to existing cluster
            for (EntityCluster cluster : clusters) {
                if (isSimilarToCluster(entity, cluster)) {
                    cluster.addEntity(entity);
                    addedToCluster = true;
                    break;
                }
            }
            
            // Create new cluster if not similar to any existing one
            if (!addedToCluster) {
                clusters.add(new EntityCluster(entity));
            }
        }
        
        return clusters;
    }

    /**
     * Checks if an entity is similar to a cluster
     */
    private boolean isSimilarToCluster(Entity entity, EntityCluster cluster) {
        // Check if entity types match
        if (!entity.getType().equals(cluster.getRepresentativeEntity().getType())) {
            return false;
        }
        
        // Check name similarity
        String entityName = entity.getName().toLowerCase();
        String clusterName = cluster.getRepresentativeEntity().getName().toLowerCase();
        
        // Simple similarity check
        if (entityName.equals(clusterName)) {
            return true;
        }
        
        // Check if one name contains the other
        return entityName.contains(clusterName) || clusterName.contains(entityName);
    }

    /**
     * Generates enhanced entity answer with clustering and analysis.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateEnhancedEntityAnswer(String query, List<Entity> entities, List<EntityCluster> clusters) {
        if (query == null || query.trim().isEmpty() || entities == null || entities.isEmpty()) {
            return generateEntityNotFoundMessage(query);
        }
        
        String entitySummary = formatEntitySummary(entities, clusters);
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Relevant information found:
            %s
            
            Write a clear, direct answer in the same language as the query.
            Provide only the information requested by the user.
            DO NOT mention any technical details like "clusters", "entity extraction", "analysis", or internal processing.
            DO NOT include phrases like "La extracción de entidades ha identificado" or "Según el análisis".
            Focus on answering the question naturally and concisely, as if you were a helpful assistant.
            """, query, 
            entitySummary != null ? entitySummary : "No relevant information found.");
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                log().warn("Empty response from LLM in generateEnhancedEntityAnswer, using fallback");
                return generateFallbackEntityAnswer(query, entities);
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error generating enhanced entity answer, using fallback", e);
            return generateFallbackEntityAnswer(query, entities);
        }
    }
    
    /**
     * Checks if an entity is generic (should be filtered out).
     * Uses LLM to determine if entity is generic.
     */
    private boolean isGenericEntity(String entityName) {
        if (entityName == null || entityName.isBlank()) {
            return true;
        }
        
        // Common generic terms to filter out
        String lower = entityName.toLowerCase().trim();
        if (lower.equals("vecinos") || lower.equals("residentes") || lower.equals("propietarios") ||
            lower.equals("neighbors") || lower.equals("residents") || lower.equals("owners") ||
            lower.equals("asistentes") || lower.equals("attendees") || lower.equals("participantes") ||
            lower.equals("participants") || lower.equals("miembros") || lower.equals("members")) {
            return true;
        }
        
        // Use LLM to check if it's a proper name vs generic term
        String prompt = String.format("""
            Task: Determine if the following entity name is a proper name (specific person/organization) or a generic term.
            
            Entity name: "%s"
            
            Examples of generic terms: "vecinos", "residentes", "propietarios", "asistentes", "miembros"
            Examples of proper names: "Juan Pérez", "María González", "Comunidad de Vecinos", "Empresa XYZ"
            
            Respond with ONLY one word: PROPER if it's a proper name, GENERIC if it's a generic term.
            """, entityName);
        
        try {
            String response = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip()
                    .toUpperCase();
            
            return response.contains("GENERIC");
        } catch (Exception e) {
            log().debug("Error checking if entity is generic, defaulting to false (keep entity): {}", e.getMessage());
            return false; // Default to keeping entity to avoid false negatives
        }
    }

    /**
     * Generates a fallback entity answer when LLM fails.
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackEntityAnswer(String query, List<Entity> entities) {
        String entitiesText = entities.stream()
                .limit(5)
                .map(e -> String.format("- %s (%s)", e.getName(), e.getType()))
                .collect(Collectors.joining("\n"));
        
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Found %d relevant entities:
            %s
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            listing the found entities.
            Be concise and direct.
            Do not repeat the question.
            """, query, entities.size(), entitiesText);
        
        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating fallback entity answer with LLM", e);
        }
        
        // Ultimate fallback
        return String.format("Found %d relevant entities:%n%s",
                          entities.size(), entitiesText);
    }

    /**
     * Formats entity summary for LLM prompt (without technical details)
     */
    private String formatEntitySummary(List<Entity> entities, List<EntityCluster> clusters) {
        StringBuilder summary = new StringBuilder();
        
        // Group entities by type for natural presentation
        Map<String, List<String>> entitiesByType = new HashMap<>();
        
        for (EntityCluster cluster : clusters) {
            Entity representative = cluster.getRepresentativeEntity();
            String type = representative.getType().toString();
            String name = representative.getName();
            
            entitiesByType.computeIfAbsent(type, k -> new ArrayList<>()).add(name);
        }
        
        // Format in a natural way without technical terms
        for (Map.Entry<String, List<String>> entry : entitiesByType.entrySet()) {
            String typeLabel = getTypeLabel(entry.getKey());
            summary.append(String.format("%s: %s\n", typeLabel, String.join(", ", entry.getValue())));
        }
        
        return summary.toString();
    }

    /**
     * Gets a natural label for entity type
     */
    private String getTypeLabel(String type) {
        switch (type.toUpperCase()) {
            case "PERSON":
                return "Personas";
            case "LOCATION":
                return "Lugares";
            case "ORGANIZATION":
                return "Organizaciones";
            default:
                return "Otros";
        }
    }


    /**
     * Generates entity-specific not found message.
     * Uses English for internal processing, but response matches query language.
     */
    private String generateEntityNotFoundMessage(String query) {
        if (query == null || query.trim().isEmpty()) {
            return generateFallbackEntityNotFoundMessage("");
        }
        
        String prompt = String.format("""
            Given the following entity extraction query (in any language):
            "%s"
            Write a short message indicating that no relevant entities were found for the query, 
            in the same language as the query.
            """, query);
        
        try {
            String response = getLLMResponseCached(prompt);
            
            if (response == null || response.trim().isEmpty()) {
                return generateFallbackEntityNotFoundMessage(query);
            }
            
            return response;
        } catch (Exception e) {
            log().error("Error generating entity not found message, using fallback", e);
            return generateFallbackEntityNotFoundMessage(query);
        }
    }
    
    /**
     * Generates a fallback "entity not found" message when LLM fails.
     * Uses LLM to generate message in correct language.
     */
    private String generateFallbackEntityNotFoundMessage(String query) {
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            No relevant entities were found for this query in the available documents.
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            stating that no relevant entities were found.
            Be concise and direct.
            Do not repeat the question.
            """, query != null ? query : "");
        
        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating fallback entity not found message with LLM", e);
        }
        
        // Ultimate fallback
        return "No relevant entities were found for this query in the available documents.";
    }
    
    /**
     * Checks if query requires filtering by both topic and person (AND logic).
     * Example (Spanish): minutes mentioning the elevator and chaired by a named person.
     */
    private boolean asksForPresidentOrSecretaryOnly(String query) {
        if (query == null || query.trim().isEmpty()) return false;
        String q = query.toLowerCase();
        boolean asksPresident = q.contains("presidió") || q.contains("presidente") || q.contains("presided") || q.contains("president");
        boolean asksSecretary = q.contains("secretaria") || q.contains("secretario") || q.contains("secretary");
        return (asksPresident || asksSecretary) && extractPersonNameFromQuery(query, null) == null;
    }

    private boolean asksForPresidentOnly(String query) {
        if (query == null) return true;
        String q = query.toLowerCase();
        return q.contains("presidió") || q.contains("presidente") || q.contains("presided") || q.contains("president");
    }

    /** Entity types that can be listed directly from metadata without LLM extraction. */
    private enum ListableEntity {
        DATES, PLACES, PRESIDENTS, SECRETARIES, TOPICS, DECISIONS, ATTENDEES
    }

    /** Detects if the query asks for a list of a specific entity type (dates, places, presidents, etc.). */
    private static ListableEntity getRequestedListableEntity(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String q = query.toLowerCase();
        if (!asksForListAll(q)) {
            return null;
        }
        return inferListableEntityFromKeywords(q);
    }

    private static boolean asksForListAll(String q) {
        return q.contains("todas las") || q.contains("todas los") || q.contains("todos los") || q.contains("todos las")
                || q.contains("diferentes") || q.contains("list all") || q.contains("listar") || q.contains("qué ") || q.contains("que ")
                || q.contains("dime las") || q.contains("dime los") || q.contains("dime todas") || q.contains("las fechas de")
                || q.contains("que tienes actas") || q.contains("cuáles son") || q.contains("cuales son")
                || q.contains("what are the") || q.contains("which ") || q.contains("name all");
    }

    private static ListableEntity inferListableEntityFromKeywords(String q) {
        if (q.contains("fecha") || q.contains("date")) {
            return ListableEntity.DATES;
        }
        if (q.contains("lugar") || q.contains("lugares") || q.contains("place") || q.contains("sitio") || q.contains("ubicación") || q.contains("location")) {
            return ListableEntity.PLACES;
        }
        if (q.contains("presidente") || q.contains("presidentes") || q.contains(META_FIELD_PRESIDENT) || q.contains("quién presidió")) {
            return ListableEntity.PRESIDENTS;
        }
        if (q.contains("secretaria") || q.contains("secretarias") || q.contains(META_FIELD_SECRETARY) || q.contains("secretaries")) {
            return ListableEntity.SECRETARIES;
        }
        if (q.contains("tema") || q.contains("temas") || q.contains("topic") || q.contains(META_FIELD_TOPICS) || q.contains("asunto")) {
            return ListableEntity.TOPICS;
        }
        if (q.contains("decisión") || q.contains("decisiones") || q.contains("acuerdo") || q.contains("acuerdos") || q.contains("decision")) {
            return ListableEntity.DECISIONS;
        }
        if (q.contains("asistente") || q.contains("asistentes") || q.contains("attendee") || q.contains("participante") || q.contains("personas que")) {
            return ListableEntity.ATTENDEES;
        }
        return null;
    }

    /** Builds a deduplicated, sorted list of values from minutes for the given entity type. */
    private List<String> buildListFromMinutes(ListableEntity type, List<Minute> minutes) {
        if (minutes == null || minutes.isEmpty()) return Collections.emptyList();
        switch (type) {
            case DATES:
                return minutes.stream().map(Minute::date).filter(Objects::nonNull).filter(d -> !d.isBlank()).distinct().sorted().toList();
            case PLACES:
                return minutes.stream().map(Minute::place).filter(Objects::nonNull).filter(p -> !p.isBlank()).distinct().sorted().toList();
            case PRESIDENTS:
                return minutes.stream().map(Minute::president).filter(Objects::nonNull).filter(p -> !p.isBlank()).distinct().sorted().toList();
            case SECRETARIES:
                return minutes.stream().map(Minute::secretary).filter(Objects::nonNull).filter(s -> !s.isBlank()).distinct().sorted().toList();
            case TOPICS:
                return minutes.stream().filter(m -> m.topics() != null).flatMap(m -> m.topics().stream())
                        .filter(Objects::nonNull).filter(t -> !t.isBlank()).distinct().sorted().toList();
            case DECISIONS:
                return minutes.stream().filter(m -> m.decisions() != null).flatMap(m -> m.decisions().stream())
                        .filter(Objects::nonNull).filter(d -> !d.isBlank()).distinct().sorted().toList();
            case ATTENDEES:
                return minutes.stream().filter(m -> m.attendees() != null).flatMap(m -> m.attendees().stream())
                        .filter(Objects::nonNull).filter(a -> !a.isBlank()).filter(a -> !isGenericEntity(a)).distinct().sorted().toList();
            default:
                return Collections.emptyList();
        }
    }

    /** Formats a list of items for the response (comma-separated or newlines for long lists). */
    private static String formatListAnswer(List<String> items, ListableEntity type) {
        if (items == null || items.isEmpty()) return "";
        if (items.size() <= 8) return String.join(", ", items);
        return String.join("\n", items);
    }

    /** Detects if the query asks for sections/structure of the minute (§4: date, place, time, attendees, agenda). */
    private static boolean asksForSectionsOrStructure(String query) {
        if (query == null || query.isBlank()) return false;
        String q = query.toLowerCase();
        return q.contains("secciones que contiene") || q.contains("secciones del acta") || q.contains("partes del acta")
                || q.contains("estructura del acta") || (q.contains("qué secciones") && q.contains("acta")) || (q.contains("qué partes") && q.contains("acta"))
                || q.contains("indícame las secciones") || q.contains("indicate the sections") || (q.contains("qué contiene") && q.contains("acta"))
                || (q.contains("orden del día") && q.contains("acta")) || q.contains("qué contiene el acta")
                || q.contains("extrae las secciones") || q.contains("indica las secciones") || q.contains("lista las secciones")
                || q.contains("secciones que tiene el acta");
    }

    /** Builds a natural-language list of minute sections from metadata (date, place, start/end time, attendees, agenda items). */
    private String buildSectionsAnswerFromMinute(Minute minute) {
        List<String> sections = new ArrayList<>();
        if (minute.date() != null && !minute.date().isBlank()) sections.add("Fecha");
        if (minute.place() != null && !minute.place().isBlank()) sections.add("Lugar");
        if (minute.startTime() != null && !minute.startTime().isBlank()) sections.add("Hora de inicio");
        if (minute.endTime() != null && !minute.endTime().isBlank()) sections.add("Hora de finalización");
        if (minute.attendees() != null && !minute.attendees().isEmpty()) sections.add("Asistentes");
        sections.add("Orden del día");
        StringBuilder out = new StringBuilder();
        out.append("El acta contiene las siguientes secciones: ").append(String.join(", ", sections)).append(".");
        if (minute.agenda() != null && !minute.agenda().isEmpty()) {
            List<String> items = minute.agenda().values().stream()
                    .filter(Objects::nonNull).filter(s -> !s.isBlank())
                    .toList();
            if (!items.isEmpty()) {
                out.append(" Orden del día (ítems): ");
                out.append(String.join("; ", items));
                out.append(".");
            }
        }
        return out.toString();
    }

    private boolean requiresTopicAndPersonFilter(String query) {
        return detectTopicAndPersonFilter(query);
    }
    
    /**
     * Filters minutes by topic AND person (both conditions must be met).
     * Example (Spanish): elevator topic plus a specific chairperson.
     */
    private List<Minute> filterMinutesByTopicAndPerson(String query, List<Minute> minutes, JSONObject ner) {
        if (minutes.isEmpty() || query == null) {
            return minutes;
        }
        
        if (!requiresTopicAndPersonFilter(query)) {
            return minutes; // No filtering needed
        }
        
        String queryLower = query.toLowerCase();
        
        // Extract topic from query
        String topic = extractTopicFromQuery(query, ner);
        if (topic == null || topic.isEmpty()) {
            log().debug("Could not extract topic from query for filtering: {}", query);
            return minutes; // Can't filter by topic
        }
        
        // Extract person from query or NER using improved extraction
        String personName = extractPersonNameFromQuery(query, ner);
        
        if (personName == null || personName.isEmpty()) {
            if (queryRequiresPerson(query)) {
                log().warn("Could not extract person name from query for topic+person filtering. Query: '{}'", query);
            } else {
                log().debug("Could not extract person name from query for topic+person filtering (query may not require person). Query: '{}'", query);
            }
            return minutes; // Can't filter by person - return all to avoid false negatives
        }
        
        // Normalize names for comparison
        final String normalizedTopic = normalizePersonName(topic); // Reuse normalizePersonName for topic too
        final String normalizedPersonName = normalizePersonName(personName);
        
        // Determine which role to check (president, secretary, or both)
        final boolean filterByPresident = queryLower.contains("presididas") || queryLower.contains("presidida") ||
                                         queryLower.contains("presidió") || queryLower.contains("president") ||
                                         queryLower.contains("presid");
        final boolean filterBySecretary = queryLower.contains("secretario") || queryLower.contains("secretary") ||
                                         queryLower.contains("secretaria");
        
        log().info("Filtering {} minutes by topic '{}' (normalized: '{}') AND person '{}' (normalized: '{}'). " +
                  "Checking president: {}, secretary: {}",
                  minutes.size(), topic, normalizedTopic, personName, normalizedPersonName,
                  filterByPresident, filterBySecretary);

        List<Minute> filtered = minutes.stream()
                .filter(minute -> minuteMatchesTopicAndPerson(
                        minute, topic, normalizedTopic, personName, normalizedPersonName, filterByPresident, filterBySecretary))
                .toList();
        
        log().info("Topic+person filtering result: {} minutes passed (out of {}). Topic: '{}', Person: '{}'", 
                  filtered.size(), minutes.size(), topic, personName);
        
        return filtered;
    }

    private boolean minuteMatchesTopicAndPerson(
            Minute minute,
            String topic,
            String normalizedTopic,
            String personName,
            String normalizedPersonName,
            boolean filterByPresident,
            boolean filterBySecretary) {
        String topicLower = normalizedTopic;
        boolean topicMatches = false;
        if (minute.topics() != null) {
            topicMatches = minute.topics().stream()
                    .anyMatch(t -> t != null && normalizePersonName(t).contains(topicLower));
        }
        if (!topicMatches && minute.decisions() != null) {
            topicMatches = minute.decisions().stream()
                    .anyMatch(d -> d != null && normalizePersonName(d).contains(topicLower));
        }
        if (!topicMatches && minute.summary() != null) {
            topicMatches = normalizePersonName(minute.summary()).contains(topicLower);
        }
        if (!topicMatches) {
            log().debug("Minute {} filtered out: topic '{}' not found in topics/decisions/summary",
                    minute.id(), topic);
            return false;
        }

        boolean personMatches = false;
        if (filterByPresident && minute.president() != null) {
            String presidentNormalized = normalizePersonName(minute.president());
            personMatches = presidentNormalized.equals(normalizedPersonName)
                    || presidentNormalized.contains(normalizedPersonName)
                    || normalizedPersonName.contains(presidentNormalized);
            if (personMatches) {
                log().debug("Minute {} person match (president): '{}' matches '{}'",
                        minute.id(), minute.president(), personName);
            }
        }
        if (!personMatches && filterBySecretary && minute.secretary() != null) {
            String secretaryNormalized = normalizePersonName(minute.secretary());
            personMatches = secretaryNormalized.equals(normalizedPersonName)
                    || secretaryNormalized.contains(normalizedPersonName)
                    || normalizedPersonName.contains(secretaryNormalized);
            if (personMatches) {
                log().debug("Minute {} person match (secretary): '{}' matches '{}'",
                        minute.id(), minute.secretary(), personName);
            }
        }
        if (!personMatches) {
            log().debug("Minute {} filtered out: person '{}' not found as president/secretary. "
                            + "President: '{}', Secretary: '{}'",
                    minute.id(), personName,
                    minute.president() != null ? minute.president() : "null",
                    minute.secretary() != null ? minute.secretary() : "null");
            return false;
        }
        log().debug("Minute {} passed both filters: topic '{}' AND person '{}'",
                minute.id(), topic, personName);
        return true;
    }

}
