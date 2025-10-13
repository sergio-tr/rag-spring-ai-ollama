package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.Minute;
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
 * 
 * Features:
 * - Intelligent entity extraction with context analysis
 * - Parallel processing for better performance
 * - Cached evaluations for efficiency
 * - Entity clustering and pattern analysis
 * - Quality ranking and synthesis of entities
 * - Advanced NER-based filtering
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
        
        // Step 1: Retrieve and filter documents efficiently
        List<Document> docs = retrieveDocumentsWithMetadataFilter(
            query, 
            new String[] {"date", "place", "topics", "decisions", "summary", "president", "secretary", "attendees"}
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

        // Step 5: Analyze and rank entities
        List<Entity> rankedEntities = analyzeAndRankEntities(query, entities);

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
        
        // Extract entities from text content using LLM
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
                EntityRole.PRESIDENT,
                minute.date(),
                minute.place(),
                1.0,
                System.currentTimeMillis()
            ));
        }
        
        // Extract secretary
        if (minute.secretary() != null && !minute.secretary().isBlank()) {
            entities.add(new Entity(
                minute.secretary(),
                EntityType.PERSON,
                EntityRole.SECRETARY,
                minute.date(),
                minute.place(),
                1.0,
                System.currentTimeMillis()
            ));
        }
        
        // Extract attendees
        if (minute.attendees() != null) {
            for (String attendee : minute.attendees()) {
                if (attendee != null && !attendee.isBlank()) {
                    entities.add(new Entity(
                        attendee,
                        EntityType.PERSON,
                        EntityRole.ATTENDEE,
                        minute.date(),
                        minute.place(),
                        0.8,
                        System.currentTimeMillis()
                    ));
                }
            }
        }
        
        // Extract place as location entity
        if (minute.place() != null && !minute.place().isBlank()) {
            entities.add(new Entity(
                minute.place(),
                EntityType.LOCATION,
                EntityRole.MEETING_PLACE,
                minute.date(),
                null,
                0.9,
                System.currentTimeMillis()
            ));
        }
        
        return entities;
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
     * Extracts entities from text using LLM
     */
    private List<Entity> extractEntitiesFromText(String text, EntityType contextType, Minute minute) {
        String prompt = String.format("""
            Given the following text:
            "%s"
            
            Extract all relevant entities (people, organizations, roles, dates, amounts, etc.) from this text.
            For each entity, provide:
            1. The entity name
            2. The entity type (PERSON, ORGANIZATION, ROLE, DATE, AMOUNT, OTHER)
            3. The role or context (if applicable)
            
            Format the response as a JSON array of objects with keys: name, type, role, context.
            """, text);
        
        try {
            String result = getLLMResponseCached(prompt).strip();
            return parseEntitiesFromLLMResponse(result, contextType, minute);
        } catch (Exception e) {
            log().debug("Error extracting entities from text: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Parses entities from LLM response
     */
    private List<Entity> parseEntitiesFromLLMResponse(String response, EntityType contextType, Minute minute) {
        List<Entity> entities = new ArrayList<>();
        
        try {
            // Simple parsing - in a real implementation, you'd use a proper JSON parser
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
                            entityRole,
                            minute.date(),
                            minute.place(),
                            0.7,
                            System.currentTimeMillis()
                        ));
                    }
                }
            }
        } catch (Exception e) {
            log().debug("Error parsing entities from LLM response: {}", e.getMessage());
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
    private List<Entity> analyzeAndRankEntities(String query, List<Entity> entities) {
        // Calculate relevance scores
        List<Entity> scoredEntities = entities.stream()
                .map(entity -> calculateEntityRelevanceScore(query, entity))
                .collect(Collectors.toList());
        
        // Sort by relevance score (descending)
        return scoredEntities.stream()
                .sorted((a, b) -> Double.compare(b.relevanceScore, a.relevanceScore))
                .collect(Collectors.toList());
    }

    /**
     * Calculates relevance score for an entity
     */
    private Entity calculateEntityRelevanceScore(String query, Entity entity) {
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Entity: %s
            Type: %s
            Role: %s
            Context: %s
            
            Rate the relevance of this entity to the query on a scale of 0.0 to 1.0.
            Consider: direct relevance, importance, and usefulness.
            Respond with only a number between 0.0 and 1.0.
            """, 
            query,
            entity.name,
            entity.type,
            entity.role,
            entity.getContext()
        );
        
        try {
            String result = getLLMResponseCached(prompt).strip();
            double score = Double.parseDouble(result);
            return new Entity(
                entity.name,
                entity.type,
                entity.role,
                entity.date,
                entity.place,
                score,
                entity.timestamp
            );
        } catch (NumberFormatException e) {
            return entity; // Keep original score if parsing fails
        }
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
        if (!entity.type.equals(cluster.getRepresentativeEntity().type)) {
            return false;
        }
        
        // Check name similarity
        String entityName = entity.name.toLowerCase();
        String clusterName = cluster.getRepresentativeEntity().name.toLowerCase();
        
        // Simple similarity check
        if (entityName.equals(clusterName)) {
            return true;
        }
        
        // Check if one name contains the other
        return entityName.contains(clusterName) || clusterName.contains(entityName);
    }

    /**
     * Generates enhanced entity answer with clustering and analysis
     */
    private String generateEnhancedEntityAnswer(String query, List<Entity> entities, List<EntityCluster> clusters) {
        String entitySummary = formatEntitySummary(entities, clusters);
        String clusterAnalysis = formatClusterAnalysis(clusters);
        
        String prompt = String.format("""
            Given the following entity extraction query (in any language):
            "%s"
            
            Found %d relevant entities grouped into %d clusters:
            
            %s
            
            Cluster analysis:
            %s
            
            Write a clear, comprehensive answer in the same language as the query, 
            summarizing the relevant entities and their context.
            Group similar entities together and highlight the most important findings.
            """, query, entities.size(), clusters.size(), entitySummary, clusterAnalysis);
        
        return getLLMResponseCached(prompt);
    }

    /**
     * Formats entity summary for LLM prompt
     */
    private String formatEntitySummary(List<Entity> entities, List<EntityCluster> clusters) {
        StringBuilder summary = new StringBuilder();
        
        for (int i = 0; i < clusters.size(); i++) {
            EntityCluster cluster = clusters.get(i);
            summary.append(String.format("Cluster %d (%d entities) - Type: %s\n", 
                                        i + 1, cluster.getSize(), cluster.getEntityType()));
            summary.append(cluster.getRepresentativeEntity().name);
            summary.append("\n\n");
        }
        
        return summary.toString();
    }

    /**
     * Formats cluster analysis for LLM prompt
     */
    private String formatClusterAnalysis(List<EntityCluster> clusters) {
        if (clusters.isEmpty()) {
            return "No clusters found.";
        }
        
        StringBuilder analysis = new StringBuilder();
        analysis.append(String.format("Total clusters: %d\n", clusters.size()));
        
        for (int i = 0; i < clusters.size(); i++) {
            EntityCluster cluster = clusters.get(i);
            analysis.append(String.format("- Cluster %d: %d entities, avg relevance: %.2f, type: %s\n", 
                                        i + 1, cluster.getSize(), cluster.getAverageRelevance(), cluster.getEntityType()));
        }
        
        return analysis.toString();
    }

    /**
     * Generates entity-specific not found message
     */
    private String generateEntityNotFoundMessage(String query) {
        String prompt = String.format("""
            Given the following entity extraction query (in any language):
            "%s"
            Write a short message indicating that no relevant entities were found for the query, 
            in the same language as the query.
            """, query);
        
        return getLLMResponseCached(prompt);
    }

    /**
     * Represents an entity with enhanced metadata
     */
    private static class Entity {
        final String name;
        final EntityType type;
        final EntityRole role;
        final String date;
        final String place;
        final double relevanceScore;
        final long timestamp;

        Entity(String name, EntityType type, EntityRole role, String date, String place, double relevanceScore, long timestamp) {
            this.name = name;
            this.type = type;
            this.role = role;
            this.date = date;
            this.place = place;
            this.relevanceScore = relevanceScore;
            this.timestamp = timestamp;
        }
        
        /**
         * Gets the context information for the entity
         */
        String getContext() {
            return String.format("%s (%s - %s)", name, date != null ? date : "unknown", place != null ? place : "unknown");
        }
        
        @Override
        public String toString() {
            return String.format("Entity[%s, type=%s, role=%s, score=%.2f]", name, type, role, relevanceScore);
        }
    }

    /**
     * Represents a cluster of similar entities
     */
    private static class EntityCluster {
        private final List<Entity> entities = new ArrayList<>();

        EntityCluster(Entity initialEntity) {
            entities.add(initialEntity);
        }

        void addEntity(Entity entity) {
            entities.add(entity);
        }

        int getSize() {
            return entities.size();
        }

        Entity getRepresentativeEntity() {
            // Return the entity with highest relevance score
            return entities.stream()
                    .max((a, b) -> Double.compare(a.relevanceScore, b.relevanceScore))
                    .orElse(entities.get(0));
        }

        EntityType getEntityType() {
            return getRepresentativeEntity().type;
        }

        double getAverageRelevance() {
            return entities.stream()
                    .mapToDouble(e -> e.relevanceScore)
                    .average()
                    .orElse(0.0);
        }
    }

    /**
     * Enum for entity types
     */
    private enum EntityType {
        PERSON, ORGANIZATION, ROLE, DATE, AMOUNT, LOCATION, TOPIC, DECISION, SUMMARY, OTHER
    }

    /**
     * Enum for entity roles
     */
    private enum EntityRole {
        PRESIDENT, SECRETARY, ATTENDEE, MEETING_PLACE, UNKNOWN
    }
}
