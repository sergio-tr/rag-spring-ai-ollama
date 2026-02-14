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
        
        // Step 6: Classify field early to use in error messages and filtering
        String detectedField = classifyFieldIntent(query, ner);
        
        if (docs.isEmpty()) {
            log().info("No documents found for get field query: {}", query);
            List<String> dateCandidates = extractDateCandidates(query, ner);
            String date = dateCandidates.isEmpty() ? null : dateCandidates.get(0);
            return ToolResult.from(formatResponse(generateSpecificErrorMessage(query, detectedField, date, 0, "no_documents"), query), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().info("No valid minutes found for get field query: {}", query);
            List<String> dateCandidates = extractDateCandidates(query, ner);
            String date = dateCandidates.isEmpty() ? null : dateCandidates.get(0);
            return ToolResult.from(formatResponse(generateSpecificErrorMessage(query, detectedField, date, docs.size(), "no_valid_minutes"), query), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().info("No relevant minutes found for get field query: {}", query);
            List<String> dateCandidates = extractDateCandidates(query, ner);
            String date = dateCandidates.isEmpty() ? null : dateCandidates.get(0);
            return ToolResult.from(formatResponse(generateSpecificErrorMessage(query, detectedField, date, minutes.size(), "no_relevant_minutes"), query), getClass());
        }

        // Step 4: Filter by person/president if query asks for "fecha del acta donde [persona]"
        // Example: "Proporciona la fecha del acta donde Juan Pérez Gutiérrez actuó como presidente"
        if (detectedField.equals("date") && detectDateWherePersonQuery(query)) {
            List<Minute> personFilteredMinutes = filterMinutesByPerson(query, relevantMinutes, ner);
            log().info("Filtered {} minutes by person condition, {} remaining (applied filter even if empty)", 
                      relevantMinutes.size(), personFilteredMinutes.size());
            relevantMinutes = personFilteredMinutes; // Apply filter even if empty - this indicates no matches
        }
        
        // Step 5: Filter by date first (if query includes date) - early filtering reduces LLM calls
        List<Minute> dateFilteredMinutes = filterMinutesByDate(query, ner, relevantMinutes);
        List<String> dateCandidates = extractDateCandidates(query, ner);
        String date = dateCandidates.isEmpty() ? null : dateCandidates.get(0);
        
        if (dateFilteredMinutes.isEmpty() && !dateCandidates.isEmpty()) {
            // User asked about a specific date but no minutes matched
            log().info("No minutes found for the specified date in query: {}", query);
            return ToolResult.from(formatResponse(generateSpecificErrorMessage(query, detectedField, date, relevantMinutes.size(), "date_not_found"), query), getClass());
        }
        // If no date in query, use all relevant minutes
        List<Minute> minutesToEvaluate = dateFilteredMinutes.isEmpty() ? relevantMinutes : dateFilteredMinutes;

        // Step 5: Evaluate each minute with LLM to validate it contains the requested information
        List<Minute> validatedMinutes = evaluateMinutesWithLLM(query, minutesToEvaluate);
        if (validatedMinutes.isEmpty()) {
            log().info("No minutes validated by LLM for get field query: {}", query);
            return ToolResult.from(formatResponse(generateSpecificErrorMessage(query, detectedField, date, minutesToEvaluate.size(), "no_validated_minutes"), query), getClass());
        }

        // Step 6: Classify field by rules (no LLM) - already done above
        if (detectedField.equals("unknown")) {
            log().info("Could not classify field intent for query: {}", query);
            return ToolResult.from(formatResponse(generateSpecificErrorMessage(query, null, date, validatedMinutes.size(), "field_classification_failed"), query), getClass());
        }

        // Step 7: Extract field values in parallel (only from validated minutes)
        List<FieldResult> results = extractFieldValuesInParallel(validatedMinutes, detectedField);
        if (results.isEmpty()) {
            log().info("No field values extracted for query: {} (field: {})", query, detectedField);
            return ToolResult.from(formatResponse(generateSpecificErrorMessage(query, detectedField, date, validatedMinutes.size(), "field_not_found_in_metadata"), query), getClass());
        }
        
        // PHASE 4: Validate that extracted field matches requested field
        // Check if we extracted the correct field type
        for (FieldResult result : results) {
            if (result.getFieldValue() != null && !result.getFieldValue().isBlank()) {
                // Basic validation: if we asked for date but got a name, or vice versa
                String value = result.getFieldValue().toLowerCase();
                if (detectedField.equals("date") && (value.matches(".*[a-z].*") && !value.matches(".*\\d{4}.*"))) {
                    // Got text that doesn't look like a date when date was requested
                    log().warn("Possible field mismatch: requested 'date' but got text that doesn't look like date: {}", result.getFieldValue());
                } else if ((detectedField.equals("president") || detectedField.equals("secretary")) && value.matches(".*\\d{4}.*")) {
                    // Got something that looks like a date when person was requested
                    log().warn("Possible field mismatch: requested '{}' but got text that looks like date: {}", detectedField, result.getFieldValue());
                }
            }
        }

        // Step 8: Analyze and rank field results
        List<FieldResult> rankedResults = analyzeAndRankFieldResults(results);

        // Step 9: Generate enhanced final answer
        String answer = generateFieldAnswer(query, rankedResults, detectedField);
        log().info("Generated get field answer for query: {} with {} field values for field: {}", 
                   query, results.size(), detectedField);
        
        return ToolResult.from(formatResponse(answer, query), getClass());
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
     * Extracts field value for a minute (metadata-first).
     * Tries multiple field name variations and synonyms.
     */
    private FieldResult extractFieldValue(Minute minute, String detectedField) {
        log().debug("Extracting field '{}' from minute {} (date: {})", 
                   detectedField, minute.id(), minute.date());
        
        // Try to extract with detected field name
        String fieldValue = extractFieldFromMinute(detectedField, minute);
        log().debug("Extracted field '{}' with name '{}': {}", detectedField, detectedField, 
                   fieldValue != null ? fieldValue : "null");
        
        // If not found, try alternative field names
        if (fieldValue == null || fieldValue.isBlank()) {
            // Try synonyms for common fields
            String[] alternativeNames = getAlternativeFieldNames(detectedField);
            log().debug("Trying alternative names for field '{}': {}", detectedField, 
                       java.util.Arrays.toString(alternativeNames));
            for (String altName : alternativeNames) {
                if (altName.equals(detectedField)) {
                    continue; // Skip if same as detected field (already tried)
                }
                fieldValue = extractFieldFromMinute(altName, minute);
                log().debug("Tried alternative name '{}' for field '{}': {}", altName, detectedField, 
                           fieldValue != null ? fieldValue : "null");
                if (fieldValue != null && !fieldValue.isBlank()) {
                    log().info("Found field '{}' using alternative name '{}'", detectedField, altName);
                    break;
                }
            }
        }
        
        if (fieldValue == null || fieldValue.isBlank()) {
            log().warn("Could not extract field '{}' from minute {} (date: {}). " +
                      "Minute has secretary: {}, president: {}", 
                      detectedField, minute.id(), minute.date(),
                      minute.secretary() != null ? minute.secretary() : "null",
                      minute.president() != null ? minute.president() : "null");
            return null;
        }
        
        log().debug("Successfully extracted field '{}' from minute {}: '{}'", 
                   detectedField, minute.id(), fieldValue);

        return new FieldResult(
            minute.id(),
            minute.date(),
            minute.place(),
            detectedField,
            fieldValue
        );
    }

    /**
     * Gets alternative field names for a given field.
     * Returns array of alternative names to try.
     */
    private String[] getAlternativeFieldNames(String field) {
        if (field == null) {
            return new String[0];
        }
        
        String fieldLower = field.toLowerCase();
        
        // Return alternative names based on field type
        return switch (fieldLower) {
            case "secretary", "secretario", "secretaria" -> new String[]{"secretary", "secretario", "secretaria"};
            case "agenda", "orden_del_dia", "orden del día" -> new String[]{"agenda", "orden_del_dia", "order_of_day", "agenda_raw"};
            case "date", "fecha" -> new String[]{"date", "fecha", "date_iso"};
            case "place", "lugar" -> new String[]{"place", "lugar", "ubicación"};
            case "president", "presidente" -> new String[]{"president", "presidente"};
            case "starttime", "hora_inicio" -> new String[]{"startTime", "hora_inicio", "start_time"};
            case "endtime", "hora_fin" -> new String[]{"endTime", "hora_fin", "end_time"};
            case "topics", "temas" -> new String[]{"topics", "temas"};
            case "decisions", "decisiones" -> new String[]{"decisions", "decisiones", "acuerdos"};
            case "summary", "resumen" -> new String[]{"summary", "resumen"};
            case "attendees", "asistentes" -> new String[]{"attendees", "asistentes", "participantes"};
            default -> new String[0];
        };
    }

    /**
     * Clasifica el campo a extraer por reglas y palabras clave (sin LLM).
     */
    private String classifyFieldIntent(String query, JSONObject ner) {
        if (query == null || query.trim().isEmpty()) {
            return "unknown";
        }
        String q = query.toLowerCase();

        // Priority 1: Check for explicit field requests with context
        // "fecha del acta donde [presidente]" -> date (not president)
        if ((containsAny(q, "fecha del acta", "fecha del", "date of the acta", "date where") ||
             (containsAny(q, "fecha", "date") && containsAny(q, "donde", "where", "acta", "minute"))) &&
            !containsAny(q, "presidió", "presidido", "presided")) {
            log().debug("Classified as 'date' based on context (fecha del acta)");
            return "date";
        }

        // Priority 2: Agenda/orden del día (check before topics)
        if (containsAny(q, "orden del día", "qué contiene el orden", "contenido del orden", "agenda", "puntos del día")) {
            log().debug("Classified as 'agenda'");
            return "agenda";
        }

        // Priority 3: Specific field requests
        if (containsAny(q, "duración", "duration", "cuánto dur")) return "durationMinutes";
        if (containsAny(q, "año", "year")) return "year";
        if (containsAny(q, "mes", "month")) return "month";
        if (containsAny(q, "lugar", "sitio", "place", "ubicación")) return "place";
        if (containsAny(q, "inicio", "start time", "hora de inicio", "comienzo")) return "startTime";
        if (containsAny(q, "fin", "final", "end time", "hora de cierre", "termin")) return "endTime";
        if (containsAny(q, "presidente", "president", "quién presidió", "who presided")) return "president";
        if (containsAny(q, "secretario", "secretary", "secretaria", "quién fue la secretaria", "who was the secretary")) {
            log().debug("Classified as 'secretary' based on query: '{}'", query);
            return "secretary";
        }
        if (containsAny(q, "asistente", "attendee", "participante", "personas")) return "attendees";
        if (containsAny(q, "número de asistentes", "cuántos asistieron", "attendees count")) return "attendeesCount";
        if (containsAny(q, "tema", "topics")) return "topics";
        if (containsAny(q, "decisión", "acuerdo", "decision", "agreements")) return "decisions";
        if (containsAny(q, "resumen", "summary")) return "summary";

        // Priority 4: Date (general, after checking context)
        if (containsAny(q, "fecha", "date", "día", "cuándo", "when")) {
            log().debug("Classified as 'date' (general)");
            return "date";
        }

        // Fallback: NER hints (but be careful - NER date might be for filtering, not extraction)
        if (ner != null && ner.has("date") && !containsAny(q, "presidente", "secretario", "agenda", "orden")) {
            log().debug("Classified as 'date' based on NER");
            return "date";
        }
        if (ner != null && ner.has("location") && !containsAny(q, "fecha", "date")) {
            log().debug("Classified as 'place' based on NER");
            return "place";
        }

        log().warn("Could not classify field intent for query: {}", query);
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
     * Generates direct answer using only metadata.
     * Uses LLM to generate message in correct language.
     */
    private String generateFieldAnswer(String query, List<FieldResult> results, String detectedField) {
        if (results == null || results.isEmpty()) {
            return generateNotFoundMessage(query);
        }

        // Direct answer style (no "se encontraron X...")
        List<FieldResult> top = results.stream().limit(5).collect(Collectors.toList());

        // Build field data for prompt
        StringBuilder fieldData = new StringBuilder();
        for (FieldResult r : top) {
            String date = r.getDate() != null ? r.getDate() : "unknown date";
            String value = r.getFieldValue();
            fieldData.append(String.format("- Date: %s, %s: %s\n", date, detectedField, value));
        }

        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Field requested: %s
            
            Found the following values:
            %s
            
            CRITICAL INSTRUCTIONS:
            1. Respond with a short message in the EXACT SAME LANGUAGE as the question
            2. State the field values found directly - start with the answer immediately
            3. If there's only one value, format it as a single sentence
            4. If there are multiple values, format them as a list
            5. Be concise and direct
            6. DO NOT repeat the question or any part of it
            7. DO NOT start with phrases like "Dime que", "The user asked", "La pregunta era", etc.
            8. Start directly with the answer (e.g., "El presidente fue..." or "The president was...")
            
            Examples of CORRECT responses:
            - Query: "Dime quién presidió la reunión del 25 de agosto de 2026"
              Correct: "El presidente en la reunión del 25 de agosto de 2026 fue Manuel Ortega Medina."
              Wrong: "Dime que el presidente fue Manuel Ortega Medina."
            
            - Query: "Who was the president on August 25, 2026?"
              Correct: "The president on August 25, 2026 was Manuel Ortega Medina."
              Wrong: "The user asked who was the president. The president was Manuel Ortega Medina."
            """, query, detectedField, fieldData.toString());

        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                // Post-process to format and clean response
                return formatResponse(response, query);
            }
        } catch (Exception e) {
            log().warn("Error generating field answer with LLM", e);
        }

        // Fallback: natural language (no raw key:value), then formatResponse
        if (top.size() == 1) {
            FieldResult r = top.get(0);
            String date = r.getDate() != null ? r.getDate() : "unknown date";
            String raw = String.format("In the meeting minutes on %s, %s: %s.", date, detectedField, r.getFieldValue());
            return formatResponse(raw, query);
        } else {
            String joined = top.stream()
                    .map(r -> String.format("%s: %s", r.getDate() != null ? r.getDate() : "unknown date", r.getFieldValue()))
                    .collect(Collectors.joining("; "));
            return formatResponse("Found values: " + joined + ".", query);
        }
    }
    
    /**
     * Filters minutes by person (president/secretary) when query asks for "fecha del acta donde [persona]"
     * Example: "Proporciona la fecha del acta donde Juan Pérez Gutiérrez actuó como presidente"
     * Also handles queries about attendees: "¿Cuándo y en qué reuniones asistió Alejandro Torres Rojas?"
     */
    private List<Minute> filterMinutesByPerson(String query, List<Minute> minutes, JSONObject ner) {
        if (minutes.isEmpty() || query == null) {
            return minutes;
        }
        
        String queryLower = query.toLowerCase();
        
        // Check if query asks for date WHERE person was president/secretary OR asks about when person attended
        boolean asksForDateWherePerson = detectDateWherePersonQuery(query);
        boolean asksAboutAttendee = queryLower.contains("asistió") || queryLower.contains("attended") ||
                                   queryLower.contains("participó") || queryLower.contains("participated");
        
        if (!asksForDateWherePerson && !asksAboutAttendee) {
            log().debug("Query does not require person filtering: {}", query);
            return minutes; // No person filtering needed
        }
        
        // Extract person name using the reusable method
        String personName = extractPersonNameFromQuery(query, ner);
        
        if (personName == null || personName.isEmpty()) {
            log().warn("Could not extract person name from query for filtering: '{}'", query);
            return minutes; // Can't filter, return all to avoid false negatives
        }
        
        // Normalize names for comparison
        final String normalizedPersonName = normalizePersonName(personName);
        
        // Determine if filtering by president, secretary, or attendee
        boolean filterByPresident = queryLower.contains("presidente") || queryLower.contains("president") ||
                                    queryLower.contains("presidió") || queryLower.contains("presided");
        boolean filterBySecretary = queryLower.contains("secretario") || queryLower.contains("secretary") || 
                                    queryLower.contains("secretaria");
        boolean filterByAttendee = asksAboutAttendee && !filterByPresident && !filterBySecretary;
        
        log().info("Filtering {} minutes by person '{}' (normalized: '{}'). " +
                  "Checking president: {}, secretary: {}, attendee: {}", 
                  minutes.size(), personName, normalizedPersonName, 
                  filterByPresident, filterBySecretary, filterByAttendee);
        
        List<Minute> filtered = minutes.stream()
                .filter(minute -> {
                    boolean matches = false;
                    
                    // Check president
                    if (filterByPresident && minute.president() != null) {
                        String presidentNormalized = normalizePersonName(minute.president());
                        matches = presidentNormalized.equals(normalizedPersonName) ||
                                 presidentNormalized.contains(normalizedPersonName) ||
                                 normalizedPersonName.contains(presidentNormalized);
                        if (matches) {
                            log().debug("Minute {} person match (president): '{}' matches '{}'", 
                                      minute.id(), minute.president(), personName);
                            return true;
                        }
                    }
                    
                    // Check secretary
                    if (!matches && filterBySecretary && minute.secretary() != null) {
                        String secretaryNormalized = normalizePersonName(minute.secretary());
                        matches = secretaryNormalized.equals(normalizedPersonName) ||
                                 secretaryNormalized.contains(normalizedPersonName) ||
                                 normalizedPersonName.contains(secretaryNormalized);
                        if (matches) {
                            log().debug("Minute {} person match (secretary): '{}' matches '{}'", 
                                      minute.id(), minute.secretary(), personName);
                            return true;
                        }
                    }
                    
                    // Check attendees
                    if (!matches && filterByAttendee && minute.attendees() != null && !minute.attendees().isEmpty()) {
                        for (String attendee : minute.attendees()) {
                            if (attendee != null) {
                                String attendeeNormalized = normalizePersonName(attendee);
                                matches = attendeeNormalized.equals(normalizedPersonName) ||
                                         attendeeNormalized.contains(normalizedPersonName) ||
                                         normalizedPersonName.contains(attendeeNormalized);
                                if (matches) {
                                    log().debug("Minute {} person match (attendee): '{}' matches '{}'", 
                                              minute.id(), attendee, personName);
                                    return true;
                                }
                            }
                        }
                    }
                    
                    if (!matches) {
                        log().debug("Minute {} filtered out: person '{}' not found. " +
                                  "President: '{}', Secretary: '{}', Attendees count: {}", 
                                  minute.id(), personName,
                                  minute.president() != null ? minute.president() : "null",
                                  minute.secretary() != null ? minute.secretary() : "null",
                                  minute.attendees() != null ? minute.attendees().size() : 0);
                    }
                    
                    return matches;
                })
                .collect(Collectors.toList());
        
        log().info("Filtered {} minutes by person '{}', {} remaining", 
                  minutes.size(), personName, filtered.size());
        
        return filtered;
    }

}