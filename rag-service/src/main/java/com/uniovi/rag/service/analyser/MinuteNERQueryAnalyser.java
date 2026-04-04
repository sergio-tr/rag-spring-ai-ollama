package com.uniovi.rag.service.analyser;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Enhanced NER Query Analyser for extracting entities from meeting minutes queries.
 * 
 * Features:
 * - Multilingual support with adaptive prompts
 * - Enhanced entity extraction with context analysis
 * - Intelligent normalization of dates, names, and values
 * - Semantic analysis for better entity matching
 * - Cached evaluations for improved performance
 * - Comprehensive validation and quality analysis
 */
public class MinuteNERQueryAnalyser implements QueryAnalyser {

    private static final String JSON_KEY_ATTENDEES = "attendees";

    private static final String JSON_KEY_TOPICS = "topics";

    private static final String JSON_KEY_PRESIDENT = "president";

    private static final String JSON_KEY_SECRETARY = "secretary";

    private static final String JSON_KEY_PLACE = "place";

    private static final String JSON_KEY_ANSWER_TYPE = "answerType";

    private static final String JSON_KEY_COMPARISON_TYPE = "comparisonType";

    private static final String JSON_KEY_TEMPORAL_CONTEXT = "temporalContext";

    private static final String JSON_KEY_START_TIME = "startTime";

    private static final String JSON_KEY_DECISIONS = "decisions";

    private static final String TEMPORAL_CONTEXT_FUTURE = "future";

    /** Spring proxy for {@code @Cacheable} (avoid self-invocation). */
    private MinuteNERQueryAnalyser cacheableSelf;

    private static final String JSON_VALUE_UNKNOWN = "unknown";

    // Enhanced prompt with multilingual support and better examples
    private static final String NER_PROMPT = String.format("""
        Analyze the following <query> to extract key entities that may be present in meeting minutes.
        Return ONLY a JSON object with the following fields (fill only the relevant ones for the query, leave the rest as empty arrays):

        - date: Dates or temporal references (e.g., "25 de febrero de 2026", "última reunión", "last meeting", "February 25, 2026").
        - %s: Location where the meeting was held.
        - startTime: Meeting start time.
        - endTime: Meeting end time.
        - president: Person who presided the meeting.
        - secretary: Person who acted as secretary.
        - attendees: Names of attendees mentioned in the query.
        - numberOfAttendees: Numbers or references to the number of attendees.
        - agenda: Specific agenda items or topics being asked about (e.g., "aprobación de cuentas", "budget approval", "ruegos y preguntas").
        - decisions: Explicit decisions or agreements mentioned in the query (e.g., "se aprobó el presupuesto", "budget was approved").
        - mentionedEntities: Companies, organizations, technicians or other entities mentioned.
        - topics: General topics discussed (e.g., "seguridad", "security", "iluminación", "lighting").
        - section: Section of the minutes being asked about (e.g., "asistentes", "attendees", "acuerdos", "agreements", "ruegos y preguntas").
        - summary: If the query asks for a summary, indicate the type of summary requested (e.g., "resumen de la reunión", "meeting summary").
        - %s: Expected answer type ("person", "number", "text", "date", "decision", "topic", "boolean", "list", "comparison", "duration", "field").
        - %s: If comparing, indicate the type ("date", "duration", "attendees", "topics", "decisions", "%s").
        - %s: Temporal context ("current", "past", "future", "specific_date", "range", "latest", "oldest").

        Examples:
        Query: "¿Quién fue el presidente en la reunión del 25 de febrero de 2026?"
        Response:
    {
      "date": ["25 de febrero de 2026"],
      "president": [],
          "%s": "person",
          "temporalContext": "specific_date"
    }

        Query: "¿Cuántos asistentes hubo en la última reunión?"
        Response:
    {
      "numberOfAttendees": [],
      "date": ["última"],
          "%s": "number",
          "temporalContext": "latest"
    }

        Query: "¿Qué se decidió sobre la calefacción?"
        Response:
    {
      "decisions": ["calefacción"],
          "topics": ["calefacción"],
      "%s": "decision"
    }

        Query: "Compare the duration of meetings in February vs March"
        Response:
        {
          "date": ["February", "March"],
          "%s": "comparison",
          "%s": "duration",
          "%s": "range"
        }

        Query: "Resume la reunión del 25 de febrero de 2026"
        Response:
    {
      "date": ["25 de febrero de 2026"],
      "summary": ["resumen de la reunión"],
          "%s": "text",
          "temporalContext": "specific_date"
        }

        Query: "What field contains the meeting place information?"
        Response:
        {
          "%s": [],
          "%s": "field",
          "section": ["%s"]
        }

        If no information is available for a field, leave it as an empty array [].
        
        CRITICAL: Return ONLY the JSON object, without any markdown formatting, explanations, or additional text.
        The response must start with { and end with }.
        Do NOT include ```json or ``` markers.
        
        Query to analyze: {query}
    """,
            JSON_KEY_PLACE,
            JSON_KEY_ANSWER_TYPE,
            JSON_KEY_COMPARISON_TYPE,
            JSON_KEY_PLACE,
            JSON_KEY_TEMPORAL_CONTEXT,
            JSON_KEY_ANSWER_TYPE,
            JSON_KEY_ANSWER_TYPE,
            JSON_KEY_ANSWER_TYPE,
            JSON_KEY_ANSWER_TYPE,
            JSON_KEY_COMPARISON_TYPE,
            JSON_KEY_TEMPORAL_CONTEXT,
            JSON_KEY_ANSWER_TYPE,
            JSON_KEY_PLACE,
            JSON_KEY_ANSWER_TYPE,
            JSON_KEY_PLACE);

    /**
     * Parses NER JSON string. If org.json throws due to duplicate keys (e.g. LLM returns "comparisonType" twice),
     * falls back to Jackson which keeps the last value per key, then returns an org.json.JSONObject.
     */
    private static final ObjectMapper JACKSON_MAPPER = new ObjectMapper();

    private final ChatClient chatClient;
    
    // Date patterns for normalization - enhanced to match parseDateFlexible and parseDateToLocalDate
    // These formatters are used to normalize dates extracted from NER
    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
        // ISO format first (most reliable)
        DateTimeFormatter.ISO_LOCAL_DATE,
        // Spanish formats with quotes
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
        // English formats
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH),
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
    
    // Patterns for common entity types
    private static final Pattern TIME_PATTERN = Pattern.compile("\\b(\\d{1,2}):(\\d{2})\\b");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+\\b");

    public MinuteNERQueryAnalyser(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Autowired
    public void setCacheableSelf(@Lazy MinuteNERQueryAnalyser cacheableSelf) {
        this.cacheableSelf = cacheableSelf;
    }

    private MinuteNERQueryAnalyser cacheable() {
        return cacheableSelf != null ? cacheableSelf : this;
    }

    @Override
    public JSONObject analyse(String query) {
        if (query == null || query.isBlank()) {
            log().warn("NER: Empty query provided");
            return createFallbackResponse(query);
        }
        
        try {
            return cacheable().analyseWithCache(query);
        } catch (Exception e) {
            // Degraded path: return heuristic fallback; avoid ERROR + full stack on every LLM/cache glitch.
            log().warn("NER: Unexpected error analyzing query '{}': {}", query, e.getMessage());
            log().debug("NER: analysis failure detail", e);
            return createFallbackResponse(query);
        }
    }
    
    /**
     * Analyzes the query with robust validation and normalization.
     */
    @Cacheable(value = "nerAnalysis", keyGenerator = "nerCacheKeyGenerator")
    private JSONObject analyseWithCache(String query) {
        // Use simple string replacement instead of PromptTemplate to avoid issues with [ and ] in JSON examples
        String prompt = NER_PROMPT.replace("{query}", query);

        String response = chatClient
                .prompt()
                .system(getSystemPrompt())
                .user(prompt)
                .call()
                .content();

        if (response == null || response.trim().isEmpty()) {
            log().warn("NER: Empty response from LLM for query: {}", query);
            return createFallbackResponse(query);
        }

        String cleanResponse = cleanJsonResponse(response);
        
        log().info("NER-QUERY: Raw response length: {}, Cleaned response:\n{}", response.length(), cleanResponse);

        if (!cleanResponse.trim().startsWith("{")) {
            log().warn("NER: Response does not start with {{, attempting to extract JSON");
            // Try to extract JSON from response
            int startIdx = cleanResponse.indexOf("{");
            int endIdx = cleanResponse.lastIndexOf("}");
            if (startIdx >= 0 && endIdx > startIdx) {
                cleanResponse = cleanResponse.substring(startIdx, endIdx + 1);
                log().info("NER: Extracted JSON substring: {}", cleanResponse);
            } else {
                log().error("NER: Response does not contain valid JSON structure for query: {}", query);
                return createFallbackResponse(query);
            }
        }

        try {
            JSONObject json = parseNerJson(cleanResponse);
            validateAndNormalize(json);
            enhanceWithContextAnalysis(json, query);
            
            log().info("NER: Successfully parsed and normalized JSON for query: {}", query);
            return json;
        } catch (org.json.JSONException e) {
            log().error("NER: JSON parsing error for query '{}': {}", query, e.getMessage(), e);
            return createFallbackResponse(query);
        } catch (IllegalArgumentException e) {
            log().error("NER: Invalid JSON structure for query '{}': {}", query, e.getMessage(), e);
            return createFallbackResponse(query);
        }
    }

    private JSONObject parseNerJson(String cleanResponse) throws org.json.JSONException {
        try {
            return new JSONObject(cleanResponse);
        } catch (org.json.JSONException e) {
            if (e.getMessage() != null && e.getMessage().contains("Duplicate key")) {
                try {
                    JsonNode node = JACKSON_MAPPER.readTree(cleanResponse);
                    String deduped = JACKSON_MAPPER.writeValueAsString(node);
                    return new JSONObject(deduped);
                } catch (Exception jacksonEx) {
                    log().warn("NER: Jackson fallback failed for duplicate-key JSON: {}", jacksonEx.getMessage());
                    throw e;
                }
            }
            throw e;
        }
    }

    /**
     * Enhanced system prompt with better instructions for consistent JSON output.
     */
    private String getSystemPrompt() {
        return """
            You are an expert entity extractor for meeting minutes queries. Your task is to extract structured 
            information from user queries and return it as a valid JSON object.
            
            These meeting minutes follow a formal structure with sections like:
            - Date (day, month and year)
            - Location
            - Start time
            - End time
            - List of Attendees: number of attendees, names and positions (e.g., president, secretary)
            - Order of the Day: topics discussed during the meeting, including Agreements, News, Decisions Made; approved or voted resolutions
            - Questions and Comments: open interventions at the end of the session
            - Meeting end time
            
            CRITICAL OUTPUT REQUIREMENTS:
            1. Your output MUST be a valid JSON object starting with { and ending with }
            2. Do NOT include any text before or after the JSON object
            3. Do NOT include markdown code blocks (```json or ```)
            4. Do NOT include explanations or comments
            5. All field names must be exactly as specified (lowercase, no spaces)
            6. Array fields must be JSON arrays, even if empty: []
            7. String fields (%s, %s, %s) must be strings, not arrays
            
            EXTRACTION GUIDELINES:
            1. Extract entities considering semantic meaning, not just exact matches
            2. Preserve original language of extracted entities (do not translate)
            3. Consider multilingual queries (Spanish, English, etc.) - extract entities in their original language
            4. Identify temporal context (past, present, future, specific dates, ranges)
            5. Detect comparison queries and extract comparison types
            6. Be precise with answer types and entity relationships
            7. If a field has no relevant information, use an empty array [] or appropriate default value
            
            VALIDATION:
            - Ensure all required fields are present
            - Ensure arrays are properly formatted
            - Ensure no syntax errors in JSON
            """.formatted(JSON_KEY_ANSWER_TYPE, JSON_KEY_COMPARISON_TYPE, JSON_KEY_TEMPORAL_CONTEXT);
    }

    /**
     * Cleans JSON response from common formatting issues
     */
    private String cleanJsonResponse(String response) {
        return response
                .replaceAll("(?s)```.*?\\n", "")  // Remove ```json\n or similar
                .replace("```", "")
                .replace("'", "\"")  // Convert single quotes to double quotes
                .replaceAll("(?m)^\\s*//.*$", "")  // Remove comments
                .strip();
    }

    /**
     * Validates and normalizes the extracted JSON
     */
    private void validateAndNormalize(JSONObject json) {
        // List of all expected fields
        String[] fields = {
            "date", JSON_KEY_PLACE, JSON_KEY_START_TIME, "endTime", JSON_KEY_PRESIDENT, JSON_KEY_SECRETARY,
            JSON_KEY_ATTENDEES, "numberOfAttendees", "agenda", JSON_KEY_DECISIONS,
            "mentionedEntities", JSON_KEY_TOPICS, "section", "summary", JSON_KEY_ANSWER_TYPE,
            JSON_KEY_COMPARISON_TYPE, JSON_KEY_TEMPORAL_CONTEXT
        };
        
        for (String field : fields) {
            if (!json.has(field)) {
                if (field.equals(JSON_KEY_ANSWER_TYPE)) {
                    json.put(field, JSON_VALUE_UNKNOWN);
                } else if (field.equals(JSON_KEY_COMPARISON_TYPE) || field.equals(JSON_KEY_TEMPORAL_CONTEXT)) {
                    json.put(field, "none");
                } else {
                    json.put(field, new JSONArray());
                }
            } else {
                // Special fields that must be strings
                if (field.equals(JSON_KEY_ANSWER_TYPE) || field.equals(JSON_KEY_COMPARISON_TYPE)
                        || field.equals(JSON_KEY_TEMPORAL_CONTEXT)) {
                    Object value = json.get(field);
                    // If it's an array, take the first element or default value
                    if (value instanceof JSONArray array) {
                        if (array.length() > 0) {
                            String firstValue = array.getString(0);
                            json.put(field, firstValue != null && !firstValue.trim().isEmpty() ? firstValue.trim() : getDefaultStringValue(field));
                        } else {
                            json.put(field, getDefaultStringValue(field));
                        }
                    } else if (value instanceof String) {
                        // Already a string, just ensure it's not empty
                        String strValue = (String) value;
                        if (strValue.trim().isEmpty()) {
                            json.put(field, getDefaultStringValue(field));
                        }
                    } else {
                        // Convert other types to string or use default
                        json.put(field, getDefaultStringValue(field));
                    }
                } else {
                    // Ensure non-special fields are arrays
                    if (!(json.get(field) instanceof JSONArray)) {
                        json.put(field, new JSONArray());
                    }
                }
            }
        }
        
        // Normalize extracted values
        normalizeDates(json);
        normalizeTimes(json);
        normalizeNumbers(json);
        normalizeNames(json);
    }
    
    /**
     * Gets the default string value for special fields
     */
    private String getDefaultStringValue(String field) {
        if (field.equals(JSON_KEY_ANSWER_TYPE)) {
            return JSON_VALUE_UNKNOWN;
        } else if (field.equals(JSON_KEY_COMPARISON_TYPE) || field.equals(JSON_KEY_TEMPORAL_CONTEXT)) {
            return "none";
        }
        return "";
    }

    /**
     * Normalizes date values to standard formats
     */
    private void normalizeDates(JSONObject json) {
        if (json.has("date")) {
            JSONArray dates = json.getJSONArray("date");
            JSONArray normalizedDates = new JSONArray();
            
            for (int i = 0; i < dates.length(); i++) {
                String dateStr = dates.getString(i);
                String normalized = normalizeDate(dateStr);
                if (normalized != null) {
                    normalizedDates.put(normalized);
                } else {
                    normalizedDates.put(dateStr); // Keep original if can't normalize
                }
            }
            
            json.put("date", normalizedDates);
        }
    }

    /**
     * Normalizes a single date string to ISO format (yyyy-MM-dd).
     * Handles relative dates and various date formats consistently with the rest of the system.
     */
    private String normalizeDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        
        String trimmed = dateStr.trim();
        String lower = trimmed.toLowerCase();
        
        // Handle relative dates (keep as-is for temporal context, don't normalize to ISO)
        if (lower.contains("última") || lower.contains("last")) return "latest";
        if (lower.contains("primera") || lower.contains("first")) return "oldest";
        if (lower.contains("pasada") || lower.contains("past")) return "past";
        if (lower.contains("próxima") || lower.contains("next")) return TEMPORAL_CONTEXT_FUTURE;
        
        // Try ISO format first (most reliable and fastest) - use original trimmed for ISO
        try {
            LocalDate date = LocalDate.parse(trimmed, DateTimeFormatter.ISO_LOCAL_DATE);
            return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignored) {
            // Not ISO format, continue to other formatters
        }
        
        // Try to parse with different formatters - use lowercase for Spanish formats
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                // Use lowercase for parsing to handle case variations (e.g., "Agosto" vs "agosto")
                String toParse = formatter.getLocale() != null && formatter.getLocale().getLanguage().equals("es") 
                    ? lower : trimmed;
                LocalDate date = LocalDate.parse(toParse, formatter);
                // Always return in ISO format for consistency
                return date.format(DateTimeFormatter.ISO_LOCAL_DATE);
            } catch (DateTimeParseException ignored) {
                // Continue to next formatter
            }
        }
        
        // If all parsing fails, log for debugging but return original
        log().debug("Could not normalize date: {}", dateStr);
        return null; // Could not normalize
    }

    /**
     * Normalizes time values
     */
    private void normalizeTimes(JSONObject json) {
        normalizeTimeField(json, JSON_KEY_START_TIME);
        normalizeTimeField(json, "endTime");
    }

    /**
     * Normalizes a specific time field
     */
    private void normalizeTimeField(JSONObject json, String fieldName) {
        if (json.has(fieldName)) {
            JSONArray times = json.getJSONArray(fieldName);
            JSONArray normalizedTimes = new JSONArray();
            
            for (int i = 0; i < times.length(); i++) {
                String timeStr = times.getString(i);
                String normalized = normalizeTime(timeStr);
                if (normalized != null) {
                    normalizedTimes.put(normalized);
                } else {
                    normalizedTimes.put(timeStr);
                }
            }
            
            json.put(fieldName, normalizedTimes);
        }
    }

    /**
     * Normalizes a single time string
     */
    private String normalizeTime(String timeStr) {
        if (timeStr == null || timeStr.trim().isEmpty()) return null;
        
        var matcher = TIME_PATTERN.matcher(timeStr);
        if (matcher.find()) {
            int hours = Integer.parseInt(matcher.group(1));
            int minutes = Integer.parseInt(matcher.group(2));
            return String.format("%02d:%02d", hours, minutes);
        }
        
        return null;
    }

    /**
     * Normalizes number values
     */
    private void normalizeNumbers(JSONObject json) {
        if (json.has("numberOfAttendees")) {
            JSONArray numbers = json.getJSONArray("numberOfAttendees");
            JSONArray normalizedNumbers = new JSONArray();
            
            for (int i = 0; i < numbers.length(); i++) {
                String numberStr = numbers.getString(i);
                String normalized = normalizeNumber(numberStr);
                if (normalized != null) {
                    normalizedNumbers.put(normalized);
                } else {
                    normalizedNumbers.put(numberStr);
                }
            }
            
            json.put("numberOfAttendees", normalizedNumbers);
        }
    }

    /**
     * Normalizes a single number string
     */
    private String normalizeNumber(String numberStr) {
        if (numberStr == null || numberStr.trim().isEmpty()) return null;
        
        var matcher = NUMBER_PATTERN.matcher(numberStr);
        if (matcher.find()) {
            return matcher.group();
        }
        
        return null;
    }

    /**
     * Normalizes person names
     */
    private void normalizeNames(JSONObject json) {
        normalizeNameField(json, JSON_KEY_PRESIDENT);
        normalizeNameField(json, JSON_KEY_SECRETARY);
        normalizeNameField(json, JSON_KEY_ATTENDEES);
    }

    /**
     * Normalizes a specific name field
     */
    private void normalizeNameField(JSONObject json, String fieldName) {
        if (json.has(fieldName)) {
            JSONArray names = json.getJSONArray(fieldName);
            JSONArray normalizedNames = new JSONArray();
            
            for (int i = 0; i < names.length(); i++) {
                String nameStr = names.getString(i);
                String normalized = normalizeName(nameStr);
                if (normalized != null) {
                    normalizedNames.put(normalized);
                } else {
                    normalizedNames.put(nameStr);
                }
            }
            
            json.put(fieldName, normalizedNames);
        }
    }

    /**
     * Normalizes a single name string
     */
    private String normalizeName(String nameStr) {
        if (nameStr == null || nameStr.trim().isEmpty()) return null;
        
        // Capitalize first letter of each word
        String[] words = nameStr.trim().split("\\s+");
        StringBuilder normalized = new StringBuilder();
        
        for (int i = 0; i < words.length; i++) {
            if (i > 0) normalized.append(" ");
            String word = words[i];
            if (!word.isEmpty()) {
                normalized.append(Character.toUpperCase(word.charAt(0)))
                         .append(word.substring(1).toLowerCase());
            }
        }
        
        return normalized.toString();
    }

    /**
     * Helper method to safely get string value from JSON, handling arrays
     */
    private String getStringSafely(JSONObject obj, String key, String defaultValue) {
        try {
            if (!obj.has(key)) {
                return defaultValue;
            }
            Object value = obj.get(key);
            if (value instanceof String str) {
                return str;
            } else if (value instanceof JSONArray) {
                JSONArray array = (JSONArray) value;
                if (array.length() > 0) {
                    return array.getString(0);
                }
            }
            return defaultValue;
        } catch (Exception e) {
            log().info("Error getting string value for key '{}': {}", key, e.getMessage());
            return defaultValue;
        }
    }
    
    /**
     * Enhances the JSON with context analysis
     */
    private void enhanceWithContextAnalysis(JSONObject json, String query) {
        // Analyze query complexity
        String queryLower = query.toLowerCase();
        
        // Detect comparison queries
        if (queryLower.contains("compare") || queryLower.contains("comparar") || 
            queryLower.contains("vs") || queryLower.contains("versus") ||
            queryLower.contains("difference") || queryLower.contains("diferencia")) {
            
            String comparisonType = getStringSafely(json, JSON_KEY_COMPARISON_TYPE, "none");
            if (comparisonType.equals("none")) {
                // Try to infer comparison type
                if (queryLower.contains("duration") || queryLower.contains("duración")) {
                    json.put(JSON_KEY_COMPARISON_TYPE, "duration");
                } else if (queryLower.contains("attendee") || queryLower.contains("asistente")) {
                    json.put(JSON_KEY_COMPARISON_TYPE, JSON_KEY_ATTENDEES);
                } else if (queryLower.contains("topic") || queryLower.contains("tema")) {
                    json.put(JSON_KEY_COMPARISON_TYPE, "topics");
                } else if (queryLower.contains("decision") || queryLower.contains("decisión")) {
                    json.put(JSON_KEY_COMPARISON_TYPE, JSON_KEY_DECISIONS);
                } else if (queryLower.contains(JSON_KEY_PLACE) || queryLower.contains("lugar")) {
                    json.put(JSON_KEY_COMPARISON_TYPE, JSON_KEY_PLACE);
                } else {
                    json.put(JSON_KEY_COMPARISON_TYPE, "general");
                }
            }
        }
        
        // Detect temporal context
        String temporalContext = getStringSafely(json, JSON_KEY_TEMPORAL_CONTEXT, "none");
        if (temporalContext.equals("none")) {
            if (queryLower.contains("última") || queryLower.contains("last") || 
                queryLower.contains("reciente") || queryLower.contains("recent")) {
                json.put(JSON_KEY_TEMPORAL_CONTEXT, "latest");
            } else if (queryLower.contains("primera") || queryLower.contains("first") ||
                      queryLower.contains("antigua") || queryLower.contains("oldest")) {
                json.put(JSON_KEY_TEMPORAL_CONTEXT, "oldest");
            } else if (queryLower.contains("pasada") || queryLower.contains("past")) {
                json.put(JSON_KEY_TEMPORAL_CONTEXT, "past");
            } else if (queryLower.contains("próxima") || queryLower.contains("next") ||
                      queryLower.contains("futura") || queryLower.contains("future")) {
                json.put(JSON_KEY_TEMPORAL_CONTEXT, TEMPORAL_CONTEXT_FUTURE);
            } else if (queryLower.contains("entre") || queryLower.contains("between") ||
                      queryLower.contains("desde") || queryLower.contains("from")) {
                json.put(JSON_KEY_TEMPORAL_CONTEXT, "range");
            } else {
                json.put(JSON_KEY_TEMPORAL_CONTEXT, "general");
            }
        }
        
        // Detect answer type if not specified
        String answerType = getStringSafely(json, JSON_KEY_ANSWER_TYPE, JSON_VALUE_UNKNOWN);
        if (answerType.equals(JSON_VALUE_UNKNOWN)) {
            if (queryLower.contains("quién") || queryLower.contains("who") ||
                queryLower.contains("president") || queryLower.contains("secretary")) {
                json.put(JSON_KEY_ANSWER_TYPE, "person");
            } else if (queryLower.contains("cuántos") || queryLower.contains("how many") ||
                      queryLower.contains("número") || queryLower.contains("number")) {
                json.put(JSON_KEY_ANSWER_TYPE, "number");
            } else if (queryLower.contains("cuándo") || queryLower.contains("when") ||
                      queryLower.contains("fecha") || queryLower.contains("date")) {
                json.put(JSON_KEY_ANSWER_TYPE, "date");
            } else if (queryLower.contains("dónde") || queryLower.contains("where") ||
                      queryLower.contains("lugar") || queryLower.contains(JSON_KEY_PLACE)) {
                json.put(JSON_KEY_ANSWER_TYPE, "location");
            } else if (queryLower.contains("resumen") || queryLower.contains("summary")) {
                json.put(JSON_KEY_ANSWER_TYPE, "text");
            } else if (queryLower.contains("sí") || queryLower.contains("no") ||
                      queryLower.contains("yes") || queryLower.contains("no")) {
                json.put(JSON_KEY_ANSWER_TYPE, "boolean");
            } else {
                json.put(JSON_KEY_ANSWER_TYPE, "text");
            }
        }
    }

    /**
     * Creates a fallback response when parsing fails
     */
    private JSONObject createFallbackResponse(String query) {
        JSONObject fallback = new JSONObject();
        
        // Try to extract basic information from the query
        String queryLower = query == null ? "" : query.toLowerCase(Locale.ROOT);
        
        // Basic date detection
        if (queryLower.contains("febrero") || queryLower.contains("february")) {
            fallback.put("date", new JSONArray().put("february"));
        } else if (queryLower.contains("marzo") || queryLower.contains("march")) {
            fallback.put("date", new JSONArray().put("march"));
        }
        
        // Basic answer type detection
        if (queryLower.contains("quién") || queryLower.contains("who")) {
            fallback.put(JSON_KEY_ANSWER_TYPE, "person");
        } else if (queryLower.contains("cuántos") || queryLower.contains("how many")) {
            fallback.put(JSON_KEY_ANSWER_TYPE, "number");
        } else {
            fallback.put(JSON_KEY_ANSWER_TYPE, "text");
        }
        
        // Fill remaining fields
        String[] fields = {JSON_KEY_PLACE, JSON_KEY_START_TIME, "endTime", JSON_KEY_PRESIDENT, JSON_KEY_SECRETARY,
                          JSON_KEY_ATTENDEES, "numberOfAttendees", "agenda", JSON_KEY_DECISIONS, 
                          "mentionedEntities", "topics", "section", "summary"};
        
        for (String field : fields) {
            if (!fallback.has(field)) {
                fallback.put(field, new JSONArray());
            }
        }
        
        fallback.put(JSON_KEY_COMPARISON_TYPE, "none");
        fallback.put(JSON_KEY_TEMPORAL_CONTEXT, "general");
        
        return fallback;
    }
}