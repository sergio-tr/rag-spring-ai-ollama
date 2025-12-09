package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.*;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Enhanced MetadataExtractEntitiesTool for extracting and analyzing entities from meeting minutes with intelligent processing.
 */
public class MetadataExtractEntitiesTool extends AbstractMetadataTool {

    public MetadataExtractEntitiesTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().debug("Executing entity extraction query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently with fallback (using NER if available)
        List<Document> docs = retrieveDocumentsWithFallback(
            query, 
            new String[] {"date", "place", "topics", "decisions", "summary", "president", "secretary", "attendees"},
            ner
        );
        
        if (docs.isEmpty()) {
            log().debug("No documents found for entity extraction query: {}", query);
            return ToolResult.from(generateEntityNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().debug("No valid minutes found for entity extraction query: {}", query);
            return ToolResult.from(generateEntityNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().debug("No relevant minutes found for entity extraction query: {}", query);
            return ToolResult.from(generateEntityNotFoundMessage(query), getClass());
        }

        // Step 4: Extract entities in parallel
        List<Entity> entities = extractEntitiesInParallel(query, relevantMinutes);
        if (entities.isEmpty()) {
            log().debug("No relevant entities found for query: {}", query);
            return ToolResult.from(generateEntityNotFoundMessage(query), getClass());
        }

        // Step 5: Deduplicate and order entities (metadata-first heuristic)
        List<Entity> rankedEntities = deduplicateAndRankEntities(entities);

        // Step 6: Cluster similar entities
        List<EntityCluster> clusters = clusterEntities(rankedEntities);

        // Step 7: Generate enhanced final answer
        String answer = generateEnhancedEntityAnswer(query, rankedEntities, clusters);
        log().debug("Generated entity extraction answer for query: {} with {} entities in {} clusters", 
                   query, entities.size(), clusters.size());
        
        return ToolResult.from(answer, getClass());
    }


    /**
     * Extracts entities in parallel
     */
    private List<Entity> extractEntitiesInParallel(String query, List<Minute> minutes) {
        List<CompletableFuture<List<Entity>>> futures = minutes.stream()
                .map(minute -> CompletableFuture.supplyAsync(() -> extractEntitiesFromMinute(query, minute)))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
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
        
        // Extract attendees
        if (minute.attendees() != null) {
            for (String attendee : minute.attendees()) {
                if (attendee != null && !attendee.isBlank()) {
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
        
        // Extract mentionedEntities directly from metadata
        if (minute.mentionedEntities() != null && !minute.mentionedEntities().isEmpty()) {
            for (String entity : minute.mentionedEntities()) {
                if (entity != null && !entity.isBlank()) {
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
        
        // If it looks like a person name (starts with capital, has spaces, or common name patterns)
        if (entityName.matches("^[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+)+")) {
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
                log().debug("Empty response from LLM in extractEntitiesFromText, returning empty list");
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
            log().debug("Error parsing JSON from LLM response, trying line-by-line parsing: {}", e.getMessage());
            // Fallback to line-by-line parsing
            return parseEntitiesFromLines(response);
        } catch (Exception e) {
            log().debug("Error parsing entities from LLM response: {}", e.getMessage());
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
            log().debug("Error in fallback line parsing: {}", e.getMessage());
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
        return switch (type.toUpperCase()) {
            case "PERSON" -> EntityType.PERSON;
            case "ORGANIZATION" -> EntityType.ORGANIZATION;
            case "ROLE" -> EntityType.ROLE;
            case "DATE" -> EntityType.DATE;
            case "AMOUNT" -> EntityType.AMOUNT;
            default -> EntityType.OTHER;
        };
    }

    /**
     * Parses entity role from string
     */
    private EntityRole parseEntityRole(String role) {
        if (role == null) return EntityRole.UNKNOWN;
        
        return switch (role.toUpperCase()) {
            case "PRESIDENT" -> EntityRole.PRESIDENT;
            case "SECRETARY" -> EntityRole.SECRETARY;
            case "ATTENDEE" -> EntityRole.ATTENDEE;
            case "MEETING_PLACE" -> EntityRole.MEETING_PLACE;
            default -> EntityRole.UNKNOWN;
        };
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
                .collect(Collectors.toList());
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
     * Generates a fallback entity answer when LLM fails.
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackEntityAnswer(String query, List<Entity> entities) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return String.format("Se encontraron %d entidades relevantes:\n%s",
                              entities.size(),
                              entities.stream()
                                      .limit(5)
                                      .map(e -> String.format("- %s (%s)", e.getName(), e.getType()))
                                      .collect(Collectors.joining("\n")));
        } else {
            return String.format("Found %d relevant entities:\n%s",
                              entities.size(),
                              entities.stream()
                                      .limit(5)
                                      .map(e -> String.format("- %s (%s)", e.getName(), e.getType()))
                                      .collect(Collectors.joining("\n")));
        }
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
        return switch (type.toUpperCase()) {
            case "PERSON" -> "Personas";
            case "LOCATION" -> "Lugares";
            case "ORGANIZATION" -> "Organizaciones";
            default -> "Otros";
        };
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
     * Detects language from query and responds accordingly.
     */
    private String generateFallbackEntityNotFoundMessage(String query) {
        String queryLower = query != null ? query.toLowerCase() : "";
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return "No se encontraron entidades relevantes para esta consulta en los documentos disponibles.";
        } else {
            return "No relevant entities were found for this query in the available documents.";
        }
    }

}
