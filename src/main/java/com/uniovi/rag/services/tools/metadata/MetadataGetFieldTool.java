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
            return ToolResult.from(generateSpecificErrorMessage(query, detectedField, date, 0, "no_documents"), getClass());
        }

        // Step 2: Extract minutes in parallel
        List<Minute> minutes = extractMinutesInParallel(docs);
        if (minutes.isEmpty()) {
            log().info("No valid minutes found for get field query: {}", query);
            List<String> dateCandidates = extractDateCandidates(query, ner);
            String date = dateCandidates.isEmpty() ? null : dateCandidates.get(0);
            return ToolResult.from(generateSpecificErrorMessage(query, detectedField, date, docs.size(), "no_valid_minutes"), getClass());
        }

        // Step 3: Filter relevant minutes based on NER or query relevance
        List<Minute> relevantMinutes = filterRelevantMinutes(query, minutes, ner);
        if (relevantMinutes.isEmpty()) {
            log().info("No relevant minutes found for get field query: {}", query);
            List<String> dateCandidates = extractDateCandidates(query, ner);
            String date = dateCandidates.isEmpty() ? null : dateCandidates.get(0);
            return ToolResult.from(generateSpecificErrorMessage(query, detectedField, date, minutes.size(), "no_relevant_minutes"), getClass());
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
            return ToolResult.from(generateSpecificErrorMessage(query, detectedField, date, relevantMinutes.size(), "date_not_found"), getClass());
        }
        // If no date in query, use all relevant minutes
        List<Minute> minutesToEvaluate = dateFilteredMinutes.isEmpty() ? relevantMinutes : dateFilteredMinutes;

        // Step 5: Evaluate each minute with LLM to validate it contains the requested information
        List<Minute> validatedMinutes = evaluateMinutesWithLLM(query, minutesToEvaluate);
        if (validatedMinutes.isEmpty()) {
            log().info("No minutes validated by LLM for get field query: {}", query);
            return ToolResult.from(generateSpecificErrorMessage(query, detectedField, date, minutesToEvaluate.size(), "no_validated_minutes"), getClass());
        }

        // Step 6: Classify field by rules (no LLM) - already done above
        if (detectedField.equals("unknown")) {
            log().info("Could not classify field intent for query: {}", query);
            return ToolResult.from(generateSpecificErrorMessage(query, null, date, validatedMinutes.size(), "field_classification_failed"), getClass());
        }

        // Step 7: Extract field values in parallel (only from validated minutes)
        List<FieldResult> results = extractFieldValuesInParallel(validatedMinutes, detectedField);
        if (results.isEmpty()) {
            log().info("No field values extracted for query: {} (field: {})", query, detectedField);
            return ToolResult.from(generateSpecificErrorMessage(query, detectedField, date, validatedMinutes.size(), "field_not_found_in_metadata"), getClass());
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
     * Extracts field value for a minute (metadata-first).
     * Tries multiple field name variations and synonyms.
     */
    private FieldResult extractFieldValue(Minute minute, String detectedField) {
        // Try to extract with detected field name
        String fieldValue = extractFieldFromMinute(detectedField, minute);
        
        // If not found, try alternative field names
        if (fieldValue == null || fieldValue.isBlank()) {
            // Try synonyms for common fields
            String[] alternativeNames = getAlternativeFieldNames(detectedField);
            for (String altName : alternativeNames) {
                fieldValue = extractFieldFromMinute(altName, minute);
                if (fieldValue != null && !fieldValue.isBlank()) {
                    log().info("Found field '{}' using alternative name '{}'", detectedField, altName);
                    break;
                }
            }
        }
        
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
        if (containsAny(q, "secretario", "secretary")) return "secretary";
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
            
            Respond with a short message in the EXACT SAME LANGUAGE as the question,
            stating the field values found.
            If there's only one value, format it as a single sentence.
            If there are multiple values, format them as a list.
            Be concise and direct.
            Do not repeat the question.
            """, query, detectedField, fieldData.toString());

        try {
            String response = getLLMResponseCached(prompt);
            if (response != null && !response.trim().isEmpty()) {
                return response.trim();
            }
        } catch (Exception e) {
            log().warn("Error generating field answer with LLM", e);
        }

        // Fallback
        if (top.size() == 1) {
            FieldResult r = top.get(0);
            String date = r.getDate() != null ? r.getDate() : "unknown date";
            return String.format("In the meeting minutes on %s, %s: %s.", date, detectedField, r.getFieldValue());
        } else {
            String joined = top.stream()
                    .map(r -> String.format("- %s: %s", r.getDate() != null ? r.getDate() : "unknown date", r.getFieldValue()))
                    .collect(Collectors.joining("\n"));
            return String.format("I found these values:\n%s", joined);
        }
    }
    
    /**
     * Filters minutes by person (president/secretary) when query asks for "fecha del acta donde [persona]"
     * Example: "Proporciona la fecha del acta donde Juan Pérez Gutiérrez actuó como presidente"
     */
    private List<Minute> filterMinutesByPerson(String query, List<Minute> minutes, JSONObject ner) {
        if (minutes.isEmpty() || query == null) {
            return minutes;
        }
        
        String queryLower = query.toLowerCase();
        
        // Check if query asks for date WHERE person was president/secretary
        // Note: detectedField is not accessible here, so we check directly
        boolean asksForDateWherePerson = (queryLower.contains("fecha") || queryLower.contains("date")) &&
                                         (queryLower.contains("donde") || queryLower.contains("where")) &&
                                         (queryLower.contains("presidente") || queryLower.contains("president") ||
                                          queryLower.contains("secretario") || queryLower.contains("secretary") ||
                                          queryLower.contains("secretaria"));
        
        if (!asksForDateWherePerson) {
            return minutes; // No person filtering needed
        }
        
        // Extract person name from query or NER
        String personName = null;
        if (ner != null && ner.has("person")) {
            try {
                org.json.JSONArray persons = ner.getJSONArray("person");
                if (persons.length() > 0) {
                    personName = persons.getString(0).trim();
                }
            } catch (Exception e) {
                log().debug("Could not extract person from NER", e);
            }
        }
        
        // If no person in NER, try to extract from query
        if (personName == null || personName.isEmpty()) {
            // Look for patterns like "donde [Nombre] actuó" or "where [Name] acted"
            // Try multiple patterns to capture full names
            java.util.regex.Pattern[] patterns = {
                // Pattern 1: "donde [Nombre Completo] actuó"
                java.util.regex.Pattern.compile(
                    "(?i)(?:donde|where)\\s+([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+){1,3})\\s+(?:actuó|acted|fue|was)",
                    java.util.regex.Pattern.CASE_INSENSITIVE
                ),
                // Pattern 2: "donde [Nombre Completo]"
                java.util.regex.Pattern.compile(
                    "(?i)(?:donde|where)\\s+([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+){1,3})",
                    java.util.regex.Pattern.CASE_INSENSITIVE
                ),
                // Pattern 3: "[Nombre Completo] actuó como presidente"
                java.util.regex.Pattern.compile(
                    "(?i)([A-ZÁÉÍÓÚÑ][a-záéíóúñ]+(?:\\s+[A-ZÁÉÍÓÚÑ][a-záéíóúñ]+){1,3})\\s+(?:actuó|acted)\\s+como",
                    java.util.regex.Pattern.CASE_INSENSITIVE
                )
            };
            
            for (java.util.regex.Pattern pattern : patterns) {
                java.util.regex.Matcher matcher = pattern.matcher(query);
                if (matcher.find()) {
                    personName = matcher.group(1).trim();
                    log().debug("Extracted person name using pattern: {}", personName);
                    break;
                }
            }
        }
        
        if (personName == null || personName.isEmpty()) {
            log().debug("Could not extract person name from query for filtering: {}", query);
            return minutes; // Can't filter, return all
        }
        
        log().info("Filtering {} minutes by person: {}", minutes.size(), personName);
        
        // Determine if filtering by president or secretary
        boolean filterByPresident = queryLower.contains("presidente") || queryLower.contains("president");
        boolean filterBySecretary = queryLower.contains("secretario") || queryLower.contains("secretary") || 
                                    queryLower.contains("secretaria");
        
        final String finalPersonName = personName.toLowerCase();
        final boolean byPresident = filterByPresident;
        final boolean bySecretary = filterBySecretary;
        
        List<Minute> filtered = minutes.stream()
                .filter(minute -> {
                    if (byPresident && minute.president() != null) {
                        String president = minute.president().toLowerCase();
                        // Check if president matches (case-insensitive, allow partial match for full names)
                        if (president.contains(finalPersonName) || finalPersonName.contains(president)) {
                            return true;
                        }
                    }
                    if (bySecretary && minute.secretary() != null) {
                        String secretary = minute.secretary().toLowerCase();
                        if (secretary.contains(finalPersonName) || finalPersonName.contains(secretary)) {
                            return true;
                        }
                    }
                    return false;
                })
                .collect(Collectors.toList());
        
        log().info("Filtered {} minutes by person '{}', {} remaining", 
                  minutes.size(), personName, filtered.size());
        
        return filtered;
    }

}