package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.AbstractTool;
import com.uniovi.rag.services.tools.EnhancedNERHandler;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.cache.annotation.Cacheable;
import com.uniovi.rag.model.Minute;
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
                String prompt = """
                        Given these specific terms from the query:
                        %s
                        
                        And the document metadata:
                        %s
                        
                        Do these terms match the metadata semantically?
                        Answer only with "YES" or "NO".
                        """.formatted(String.join(", ", terms), doc.getMetadata().toString());

                String result = chatClient
                        .prompt()
                        .user(prompt)
                        .call()
                        .content()
                        .strip()
                        .toLowerCase();

                if (result.contains("yes")) {
                    return true;
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

    protected boolean semanticallyMatches(String content, String[] keywords) {
        String prompt = """
                Dado el siguiente contenido de acta, dime si se hace alguna mención relacionada con estos temas: %s
                Contenido del acta:
                %s
                
                Responde solo con "Sí" o "No".
                """.formatted(String.join(", ", keywords), content);

        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();

        return result.contains("sí");
    }

    protected boolean semanticallyMatchesMetadata(Document doc, String query) {
        String prompt = """
                Given the following user query (in any language):
                "%s"
                
                And the following document metadata:
                %s
                
                Does this document's metadata match the intent of the query, regardless of exact wording or language?
                Consider all metadata fields and their semantic meaning.
                
                Answer only with "YES" or "NO".
                """.formatted(query, doc.getMetadata().toString());

        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();

        return result.contains("yes");
    }

    protected boolean semanticallyMatchesMinute(Minute minute, String query) {
        String prompt = """
                Given the following user query (in any language):
                "%s"
                
                And the following meeting metadata:
                Date: %s
                Place: %s
                Topics: %s
                Decisions: %s
                Summary: %s
                Agenda: %s
                
                Does this meeting match the intent of the query, regardless of exact wording or language?
                Consider all fields and their semantic meaning.
                
                Answer only with "YES" or "NO".
                """.formatted(
                    query,
                    minute.date() != null ? minute.date() : "unknown",
                    minute.place() != null ? minute.place() : "unknown",
                    minute.topics() != null ? String.join(", ", minute.topics()) : "unknown",
                    minute.decisions() != null ? String.join(", ", minute.decisions()) : "unknown",
                    minute.summary() != null ? minute.summary() : "unknown",
                    minute.agenda() != null ? minute.agenda().toString() : "unknown"
                );

        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();

        return result.contains("yes");
    }

    /**
     * Extrae y deserializa el objeto Minute del Document
     */
    protected Minute getMinuteFromMetadata(Document doc) {
        Object minuteObj = doc.getMetadata().get("minute");
        if (minuteObj instanceof Minute) {
            return (Minute) minuteObj;
        }
        if (minuteObj instanceof String json) {
            try {
                return objectMapper.readValue(json, Minute.class);
            } catch (Exception ex) {
                return null;
            }
        }
        return null;
    }

    /**
     * Compara semánticamente un Minute con las entidades NER
     */
    protected boolean matchesMinuteWithNER(Minute minute, JSONObject ner) {
        if (ner == null || ner.isEmpty()) return true;

        String prompt = """
                Given the following meeting metadata:
                Date: %s
                Place: %s
                President: %s
                Secretary: %s
                Topics: %s
                Summary: %s
                
                And these NER entities to match:
                %s
                
                Does this meeting match all the specified entities?
                Consider semantic meaning, not just exact matches.
                Answer only with YES or NO.
                """.formatted(
                    minute.date() != null ? minute.date() : "",
                    minute.place() != null ? minute.place() : "",
                    minute.president() != null ? minute.president() : "",
                    minute.secretary() != null ? minute.secretary() : "",
                    minute.topics() != null ? String.join(", ", minute.topics()) : "",
                    minute.summary() != null ? minute.summary() : "",
                    ner.toString(2)
                );

        String result = chatClient.prompt().user(prompt).call().content().strip().toLowerCase();
        return result.contains("yes") || result.contains("sí");
    }

    /**
     * Extrae un campo específico del Minute y lo formatea según el tipo
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
            return objectMapper.readValue(result, Map.class);
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
     * Filters relevant minutes based on NER or query relevance using EnhancedNERHandler
     */
    protected List<Minute> filterRelevantMinutes(String query, List<Minute> minutes, JSONObject ner) {
        if (ner != null && !ner.isEmpty()) {
            // Use enhanced NER filtering with temporal context
            List<Minute> temporalFiltered = nerHandler.filterMinutesByTemporalContext(minutes, ner);
            
            // Filter by NER matching
            return temporalFiltered.stream()
                    .filter(minute -> nerHandler.matchesMinuteWithNER(minute, ner))
                    .filter(minute -> isRelevantToQueryCached(query, minute))
                    .collect(Collectors.toList());
        } else {
            return filterMinutesByQueryRelevance(query, minutes);
        }
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
     * Determines if a minute is relevant to the query using LLM
     */
    protected boolean isRelevantToQueryByLLM(String query, Minute minute) {
        String prompt = generateRelevancePrompt(query, minute);
        String result = getLLMResponseCached(prompt);
        return result.toLowerCase().contains("yes") || result.toLowerCase().contains("sí");
    }

    /**
     * Generates adaptive relevance prompt based on query context
     */
    protected String generateRelevancePrompt(String query, Minute minute) {
        String queryType = analyzeQueryType(query);
        
        return String.format("""
            Given the following user query (in any language):
            "%s"
            
            Query type: %s
            
            Meeting metadata:
            Date: %s
            Place: %s
            President: %s
            Secretary: %s
            Topics: %s
            Decisions: %s
            Summary: %s
            
            Does this meeting contain information that directly answers or relates to the query?
            Consider semantic meaning, not just exact matches.
            Answer only with YES or NO.
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
     * Cached LLM response for better performance
     */
    @Cacheable(value = "llmResponses", key = "#prompt.hashCode()")
    protected String getLLMResponseCached(String prompt) {
        return chatClient.prompt().user(prompt).call().content().strip();
    }

    /**
     * Retrieves documents with intelligent metadata filtering using EnhancedNERHandler
     */
    protected List<Document> retrieveDocumentsWithMetadataFilter(String query, String[] relevantFields) {
        List<Document> docs = retrieveAllDocuments(query);
        
        // Filter documents that have valid metadata
        List<Document> metadataDocs = docs.stream()
                .filter(doc -> doc.getMetadata().containsKey("minute"))
                .filter(doc -> hasRelevantMetadata(doc, query, relevantFields))
                .collect(Collectors.toList());
        
        return metadataDocs;
    }

    /**
     * Checks if document has metadata relevant to the query
     */
    protected boolean hasRelevantMetadata(Document doc, String query, String[] relevantFields) {
        Map<String, Object> metadata = doc.getMetadata();
        
        // Check if any metadata field might be relevant to the query
        String queryLower = query.toLowerCase();
        
        for (String field : relevantFields) {
            Object value = metadata.get(field);
            if (value != null && value.toString().toLowerCase().contains(queryLower)) {
                return true;
            }
        }
        
        return true; // If no direct match, let LLM decide
    }

    /**
     * Generates not found message using LLM
     */
    protected String generateNotFoundMessage(String query) {
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            Write a short message indicating that no relevant meeting minutes were found, 
            in the same language as the query.
            """, query);
        
        return getLLMResponseCached(prompt);
    }

    /**
     * Generates no data message using LLM
     */
    protected String generateNoDataMessage(String query) {
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            Write a short message indicating that no relevant data was found for the query, 
            in the same language as the query.
            """, query);
        
        return getLLMResponseCached(prompt);
    }

    /**
     * Calculates relevance score using LLM
     */
    protected double calculateRelevanceScore(String query, String itemContent, String context) {
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Item content: %s
            Context: %s
            
            Rate the relevance of this item to the query on a scale of 0.0 to 1.0.
            Consider: direct relevance, completeness, clarity, and usefulness.
            Respond with only a number between 0.0 and 1.0.
            """, query, itemContent, context);
        
        try {
            String result = getLLMResponseCached(prompt).strip();
            return Double.parseDouble(result);
        } catch (NumberFormatException e) {
            return 0.5; // Default score if parsing fails
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

