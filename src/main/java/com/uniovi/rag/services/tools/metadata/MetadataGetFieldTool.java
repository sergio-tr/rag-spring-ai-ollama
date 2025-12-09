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
 * Enhanced MetadataGetFieldTool for extracting specific fields from meeting minutes with intelligent analysis.
 */
public class MetadataGetFieldTool extends AbstractMetadataTool {

    public MetadataGetFieldTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().info("Executing get field query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        // Step 1: Retrieve and filter documents efficiently with fallback (using NER if available)
        List<Document> docs = retrieveDocumentsWithFallback(
            query, 
            new String[] {"date", "place", "startTime", "endTime", "topics", "decisions", "summary", "president", "secretary", "attendees"},
            ner
        );
        
        if (docs.isEmpty()) {
            log().info("No documents found for get field query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().info("No valid minutes found for get field query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().info("No relevant minutes found for get field query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 4: Clasificar campo por reglas (sin LLM)
        String detectedField = classifyFieldIntent(query, ner);
        if (detectedField.equals("unknown")) {
            log().info("Could not classify field intent for query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 5: Extract field values in parallel
        List<FieldResult> results = extractFieldValuesInParallel(relevantMinutes, detectedField);
        if (results.isEmpty()) {
            log().info("No field values extracted for query: {}", query);
            return ToolResult.from(generateNoDataMessage(query), getClass());
        }

        // Step 6: Analyze and rank field results
        List<FieldResult> rankedResults = analyzeAndRankFieldResults(results);

        // Step 7: Generate enhanced final answer
        String answer = generateFieldAnswer(query, rankedResults, detectedField);
        log().info("Generated get field answer for query: {} with {} field values for field: {}", 
                   query, results.size(), detectedField);
        
        return ToolResult.from(answer, getClass());
    }

    /**
     * Extracts field values in parallel
     */
    private List<FieldResult> extractFieldValuesInParallel(List<Minute> minutes, String detectedField) {
        List<CompletableFuture<FieldResult>> futures = minutes.stream()
                .map(minute -> CompletableFuture.supplyAsync(() -> extractFieldValue(minute, detectedField)))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .filter(result -> result.getFieldValue() != null && !result.getFieldValue().isBlank())
                .collect(Collectors.toList());
    }

    /**
     * Extracts field value for a minute (metadata-first)
     */
    private FieldResult extractFieldValue(Minute minute, String detectedField) {
        String fieldValue = extractFieldFromMinute(detectedField, minute);
        
        if (fieldValue == null || fieldValue.isBlank()) {
            return null;
        }

        return new FieldResult(
            minute.id(),
            minute.date(),
            minute.place(),
            detectedField,
            fieldValue
        );
    }

    /**
     * Clasifica el campo a extraer por reglas y palabras clave (sin LLM).
     */
    private String classifyFieldIntent(String query, JSONObject ner) {
        if (query == null || query.trim().isEmpty()) {
            return "unknown";
        }
        String q = query.toLowerCase();

        if (containsAny(q, "duración", "duration", "cuánto dur")) return "durationMinutes";
        if (containsAny(q, "fecha", "date", "día")) return "date";
        if (containsAny(q, "año", "year")) return "year";
        if (containsAny(q, "mes", "month")) return "month";
        if (containsAny(q, "lugar", "sitio", "place", "ubicación")) return "place";
        if (containsAny(q, "inicio", "start time", "hora de inicio", "comienzo")) return "startTime";
        if (containsAny(q, "fin", "final", "end time", "hora de cierre", "termin")) return "endTime";
        if (containsAny(q, "presidente", "president")) return "president";
        if (containsAny(q, "secretario", "secretary")) return "secretary";
        if (containsAny(q, "asistente", "attendee", "participante", "personas")) return "attendees";
        if (containsAny(q, "número de asistentes", "cuántos asistieron", "attendees count")) return "attendeesCount";
        if (containsAny(q, "tema", "topics", "orden del día", "agenda")) return "topics";
        if (containsAny(q, "decisión", "acuerdo", "decision", "agreements")) return "decisions";
        if (containsAny(q, "resumen", "summary")) return "summary";
        if (containsAny(q, "agenda", "puntos", "orden del día")) return "agenda";

        // fallback: NER place/date hints
        if (ner != null && ner.has("date")) return "date";
        if (ner != null && ner.has("location")) return "place";

        return "unknown";
    }

    private boolean containsAny(String text, String... needles) {
        for (String n : needles) {
            if (text.contains(n.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Analyzes and ranks field results by relevance and quality
     */
    private List<FieldResult> analyzeAndRankFieldResults(List<FieldResult> results) {
        // Ordenar por longitud del valor (desc) como señal simple de riqueza
        return results.stream()
                .sorted((a, b) -> Integer.compare(
                        b.getFieldValue() != null ? b.getFieldValue().length() : 0,
                        a.getFieldValue() != null ? a.getFieldValue().length() : 0))
                .collect(Collectors.toList());
    }

    /**
     * Genera respuesta directa usando solo metadatos.
     */
    private String generateFieldAnswer(String query, List<FieldResult> results, String detectedField) {
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");
        
        if (isSpanish) {
            return String.format("Se encontraron %d valores para el campo '%s':\n%s",
                              results.size(),
                              detectedField,
                              results.stream()
                                      .limit(5)
                                      .map(r -> String.format("- %s: %s", r.getDate() != null ? r.getDate() : "fecha desconocida", r.getFieldValue()))
                                      .collect(Collectors.joining("\n")));
        } else {
            return String.format("Found %d values for field '%s':\n%s",
                              results.size(),
                              detectedField,
                              results.stream()
                                      .limit(5)
                                      .map(r -> String.format("- %s: %s", r.getDate() != null ? r.getDate() : "unknown date", r.getFieldValue()))
                                      .collect(Collectors.joining("\n")));
        }
    }

}