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

        // Step 4: Filter by date first (if query includes date) - early filtering reduces LLM calls
        List<Minute> dateFilteredMinutes = filterMinutesByDate(query, ner, relevantMinutes);
        if (dateFilteredMinutes.isEmpty() && !extractDateCandidates(query, ner).isEmpty()) {
            // User asked about a specific date but no minutes matched
            log().info("No minutes found for the specified date in query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }
        // If no date in query, use all relevant minutes
        List<Minute> minutesToEvaluate = dateFilteredMinutes.isEmpty() ? relevantMinutes : dateFilteredMinutes;

        // Step 5: Evaluate each minute with LLM to validate it contains the requested information
        List<Minute> validatedMinutes = evaluateMinutesWithLLM(query, minutesToEvaluate);
        if (validatedMinutes.isEmpty()) {
            log().info("No minutes validated by LLM for get field query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 6: Classify field by rules (no LLM)
        String detectedField = classifyFieldIntent(query, ner);
        if (detectedField.equals("unknown")) {
            log().info("Could not classify field intent for query: {}", query);
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 7: Extract field values in parallel (only from validated minutes)
        List<FieldResult> results = extractFieldValuesInParallel(validatedMinutes, detectedField);
        if (results.isEmpty()) {
            log().info("No field values extracted for query: {}", query);
            return ToolResult.from(generateNoDataMessage(query), getClass());
        }

        // Step 8: Analyze and rank field results
        List<FieldResult> rankedResults = analyzeAndRankFieldResults(results);

        // Step 9: Generate enhanced final answer
        String answer = generateFieldAnswer(query, rankedResults, detectedField);
        log().info("Generated get field answer for query: {} with {} field values for field: {}", 
                   query, results.size(), detectedField);
        
        return ToolResult.from(answer, getClass());
    }

    /**
     * Evaluates minutes with LLM to validate they contain the requested information.
     * Only minutes that pass validation are used for field extraction.
     */
    private List<Minute> evaluateMinutesWithLLM(String query, List<Minute> minutes) {
        List<CompletableFuture<Minute>> futures = minutes.stream()
                .map(minute -> CompletableFuture.supplyAsync(() -> {
                    if (evaluateMinuteContainsRequestedInfo(query, minute)) {
                        return minute;
                    }
                    return null;
                }))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
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
        if (results == null || results.isEmpty()) {
            return generateNotFoundMessage(query);
        }
        
        String queryLower = query.toLowerCase();
        boolean isSpanish = queryLower.matches(".*[áéíóúñ¿¡].*");

        // Direct answer style (no "se encontraron X...")
        List<FieldResult> top = results.stream().limit(5).collect(Collectors.toList());

        // If only one result, answer as a single sentence.
        if (top.size() == 1) {
            FieldResult r = top.get(0);
            String date = r.getDate() != null ? r.getDate() : (isSpanish ? "fecha desconocida" : "unknown date");
            String value = r.getFieldValue();
            String label = fieldLabel(detectedField, isSpanish);

            if (isSpanish) {
                return String.format("En el acta del %s, %s: %s.", date, label, value);
            }
            return String.format("In the meeting minutes on %s, %s: %s.", date, label, value);
        }

        // Multiple candidates: provide a compact list, each line answers with date + value.
        String joined = top.stream()
                .map(r -> {
                    String date = r.getDate() != null ? r.getDate() : (isSpanish ? "fecha desconocida" : "unknown date");
                    return isSpanish
                            ? String.format("- Acta del %s: %s", date, r.getFieldValue())
                            : String.format("- %s: %s", date, r.getFieldValue());
                })
                .collect(Collectors.joining("\n"));

        if (isSpanish) {
            return String.format("He encontrado estos valores:\n%s", joined);
        }
        return String.format("I found these values:\n%s", joined);
    }

    private String fieldLabel(String detectedField, boolean isSpanish) {
        String f = detectedField != null ? detectedField.toLowerCase() : "";
        return switch (f) {
            case "date", "fecha" -> isSpanish ? "la fecha es" : "date is";
            case "place", "lugar" -> isSpanish ? "el lugar es" : "place is";
            case "starttime", "hora_inicio", "starttime " -> isSpanish ? "la hora de inicio es" : "start time is";
            case "endtime", "hora_fin" -> isSpanish ? "la hora de fin es" : "end time is";
            case "president", "presidente" -> isSpanish ? "el presidente es" : "president is";
            case "secretary", "secretario" -> isSpanish ? "el secretario es" : "secretary is";
            case "attendees", "asistentes" -> isSpanish ? "los asistentes son" : "attendees are";
            case "attendeescount", "numberofattendees" -> isSpanish ? "el número de asistentes es" : "attendees count is";
            case "durationminutes" -> isSpanish ? "la duración es" : "duration is";
            case "topics", "temas" -> isSpanish ? "los temas son" : "topics are";
            case "decisions", "decisiones" -> isSpanish ? "las decisiones son" : "decisions are";
            case "summary", "resumen" -> isSpanish ? "el resumen es" : "summary is";
            default -> isSpanish ? "el valor es" : "value is";
        };
    }


}