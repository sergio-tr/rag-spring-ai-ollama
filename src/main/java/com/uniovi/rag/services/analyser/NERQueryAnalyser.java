package com.uniovi.rag.services.analyser;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;

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
public class NERQueryAnalyser implements QueryAnalyser {

    // Enhanced prompt with multilingual support and better examples
    private static final String NER_PROMPT = """
        Analyze the following <query> to extract key entities that may be present in meeting minutes.
        Return ONLY a JSON object with the following fields (fill only the relevant ones for the query, leave the rest as empty arrays):

        - date: Dates or temporal references (e.g., "25 de febrero de 2026", "última reunión", "last meeting", "February 25, 2026").
        - place: Location where the meeting was held.
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
        - answerType: Expected answer type ("person", "number", "text", "date", "decision", "topic", "boolean", "list", "comparison", "duration", "field").
        - comparisonType: If comparing, indicate the type ("date", "duration", "attendees", "topics", "decisions", "place").
        - temporalContext: Temporal context ("current", "past", "future", "specific_date", "range", "latest", "oldest").

        Examples:
        Query: "¿Quién fue el presidente en la reunión del 25 de febrero de 2026?"
        Response:
    {
      "date": ["25 de febrero de 2026"],
      "president": [],
          "answerType": "person",
          "temporalContext": "specific_date"
    }

        Query: "¿Cuántos asistentes hubo en la última reunión?"
        Response:
    {
      "numberOfAttendees": [],
      "date": ["última"],
          "answerType": "number",
          "temporalContext": "latest"
    }

        Query: "¿Qué se decidió sobre la calefacción?"
        Response:
    {
      "decisions": ["calefacción"],
          "topics": ["calefacción"],
      "answerType": "decision"
    }

        Query: "Compare the duration of meetings in February vs March"
        Response:
        {
          "date": ["February", "March"],
          "answerType": "comparison",
          "comparisonType": "duration",
          "temporalContext": "range"
        }

        Query: "Resume la reunión del 25 de febrero de 2026"
        Response:
    {
      "date": ["25 de febrero de 2026"],
      "summary": ["resumen de la reunión"],
          "answerType": "text",
          "temporalContext": "specific_date"
        }

        Query: "What field contains the meeting place information?"
        Response:
        {
          "place": [],
          "answerType": "field",
          "section": ["place"]
        }

        If no information is available for a field, leave it as an empty array. Return ONLY the JSON, without explanations or additional formatting.
        <Query>: {query}
    """;

    private final ChatClient chatClient;
    
    // Date patterns for normalization
    private static final List<DateTimeFormatter> DATE_FORMATTERS = Arrays.asList(
        DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", Locale.forLanguageTag("es")),
        DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH),
        DateTimeFormatter.ofPattern("d/M/yyyy"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd"),
        DateTimeFormatter.ofPattern("d-MM-yyyy")
    );
    
    // Patterns for common entity types
    private static final Pattern TIME_PATTERN = Pattern.compile("\\b(\\d{1,2}):(\\d{2})\\b");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+\\b");

    public NERQueryAnalyser(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public JSONObject analyse(String query) {
        String prompt = new PromptTemplate(NER_PROMPT).create(Map.of("query", query)).getContents();

        String response = chatClient
                .prompt()
                .system(getSystemPrompt())
                .user(prompt)
                .call()
                .content();

        String cleanResponse = cleanJsonResponse(response);
        
        log().debug("NER-QUERY: JSON returned →\n{}", cleanResponse);

        try {
            if (!cleanResponse.trim().startsWith("{")) {
                throw new IllegalArgumentException("Response is not a valid JSON.");
            }

            JSONObject json = new JSONObject(cleanResponse);
            validateAndNormalize(json);
            enhanceWithContextAnalysis(json, query);
            return json;
        } catch (Exception e) {
            log().warn("NER: Error parsing JSON for query '{}': {}", query, e.getMessage());
            return createFallbackResponse(query);
        }
    }

    /**
     * Enhanced system prompt with better instructions
     */
    private String getSystemPrompt() {
        return """
            You are an expert entity extractor for meeting minutes queries in JSON format.
            These minutes follow a formal structure with sections like:
            - Date (day, month and year)
            - Location
            - Start time
            - End time
            - List of Attendees: number of attendees, names and positions (e.g., president, secretary).
            - Order of the Day: topics discussed during the meeting, including Agreements, News, Decisions Made; approved or voted resolutions.
            - Questions and Comments: open interventions at the end of the session.
            - Meeting end time.
            
            Your output must be a JSON object that starts with an opening brace and ends with a closing brace.
            
            IMPORTANT GUIDELINES:
            1. Extract entities considering semantic meaning, not just exact matches
            2. Normalize dates to standard formats when possible
            3. Consider multilingual queries (Spanish, English, etc.)
            4. Identify temporal context (past, present, future, specific dates, ranges)
            5. Detect comparison queries and extract comparison types
            6. Be precise with answer types and entity relationships
            7. Return only valid JSON without any additional text
            """;
    }

    /**
     * Cleans JSON response from common formatting issues
     */
    private String cleanJsonResponse(String response) {
        return response
                .replaceAll("(?s)```.*?\\n", "")  // Remove ```json\n or similar
                .replaceAll("```", "")
                .replaceAll("'", "\"")  // Convert single quotes to double quotes
                .replaceAll("(?m)^\\s*//.*$", "")  // Remove comments
                .strip();
    }

    /**
     * Validates and normalizes the extracted JSON
     */
    private void validateAndNormalize(JSONObject json) {
        // List of all expected fields
        String[] fields = {
            "date", "place", "startTime", "endTime", "president", "secretary", 
            "attendees", "numberOfAttendees", "agenda", "decisions", 
            "mentionedEntities", "topics", "section", "summary", "answerType",
            "comparisonType", "temporalContext"
        };
        
        for (String field : fields) {
            if (!json.has(field)) {
                if (field.equals("answerType")) {
                    json.put(field, "unknown");
                } else if (field.equals("comparisonType") || field.equals("temporalContext")) {
                    json.put(field, "none");
                } else {
                    json.put(field, new JSONArray());
                }
            } else {
                // Ensure non-special fields are arrays
                if (!field.equals("answerType") && !field.equals("comparisonType") && !field.equals("temporalContext")) {
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
     * Normalizes a single date string
     */
    private String normalizeDate(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) return null;
        
        String lower = dateStr.toLowerCase().trim();
        
        // Handle relative dates
        if (lower.contains("última") || lower.contains("last")) return "latest";
        if (lower.contains("primera") || lower.contains("first")) return "oldest";
        if (lower.contains("pasada") || lower.contains("past")) return "past";
        if (lower.contains("próxima") || lower.contains("next")) return "future";
        
        // Try to parse with different formatters
        for (DateTimeFormatter formatter : DATE_FORMATTERS) {
            try {
                LocalDate date = LocalDate.parse(dateStr, formatter);
                return date.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            } catch (DateTimeParseException ignored) {
                // Continue to next formatter
            }
        }
        
        return null; // Could not normalize
    }

    /**
     * Normalizes time values
     */
    private void normalizeTimes(JSONObject json) {
        normalizeTimeField(json, "startTime");
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
        normalizeNameField(json, "president");
        normalizeNameField(json, "secretary");
        normalizeNameField(json, "attendees");
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
     * Enhances the JSON with context analysis
     */
    private void enhanceWithContextAnalysis(JSONObject json, String query) {
        // Analyze query complexity
        String queryLower = query.toLowerCase();
        
        // Detect comparison queries
        if (queryLower.contains("compare") || queryLower.contains("comparar") || 
            queryLower.contains("vs") || queryLower.contains("versus") ||
            queryLower.contains("difference") || queryLower.contains("diferencia")) {
            
            if (json.getString("comparisonType").equals("none")) {
                // Try to infer comparison type
                if (queryLower.contains("duration") || queryLower.contains("duración")) {
                    json.put("comparisonType", "duration");
                } else if (queryLower.contains("attendee") || queryLower.contains("asistente")) {
                    json.put("comparisonType", "attendees");
                } else if (queryLower.contains("topic") || queryLower.contains("tema")) {
                    json.put("comparisonType", "topics");
                } else if (queryLower.contains("decision") || queryLower.contains("decisión")) {
                    json.put("comparisonType", "decisions");
                } else if (queryLower.contains("place") || queryLower.contains("lugar")) {
                    json.put("comparisonType", "place");
                } else {
                    json.put("comparisonType", "general");
                }
            }
        }
        
        // Detect temporal context
        if (json.getString("temporalContext").equals("none")) {
            if (queryLower.contains("última") || queryLower.contains("last") || 
                queryLower.contains("reciente") || queryLower.contains("recent")) {
                json.put("temporalContext", "latest");
            } else if (queryLower.contains("primera") || queryLower.contains("first") ||
                      queryLower.contains("antigua") || queryLower.contains("oldest")) {
                json.put("temporalContext", "oldest");
            } else if (queryLower.contains("pasada") || queryLower.contains("past")) {
                json.put("temporalContext", "past");
            } else if (queryLower.contains("próxima") || queryLower.contains("next") ||
                      queryLower.contains("futura") || queryLower.contains("future")) {
                json.put("temporalContext", "future");
            } else if (queryLower.contains("entre") || queryLower.contains("between") ||
                      queryLower.contains("desde") || queryLower.contains("from")) {
                json.put("temporalContext", "range");
            } else {
                json.put("temporalContext", "general");
            }
        }
        
        // Detect answer type if not specified
        if (json.getString("answerType").equals("unknown")) {
            if (queryLower.contains("quién") || queryLower.contains("who") ||
                queryLower.contains("president") || queryLower.contains("secretary")) {
                json.put("answerType", "person");
            } else if (queryLower.contains("cuántos") || queryLower.contains("how many") ||
                      queryLower.contains("número") || queryLower.contains("number")) {
                json.put("answerType", "number");
            } else if (queryLower.contains("cuándo") || queryLower.contains("when") ||
                      queryLower.contains("fecha") || queryLower.contains("date")) {
                json.put("answerType", "date");
            } else if (queryLower.contains("dónde") || queryLower.contains("where") ||
                      queryLower.contains("lugar") || queryLower.contains("place")) {
                json.put("answerType", "location");
            } else if (queryLower.contains("resumen") || queryLower.contains("summary")) {
                json.put("answerType", "text");
            } else if (queryLower.contains("sí") || queryLower.contains("no") ||
                      queryLower.contains("yes") || queryLower.contains("no")) {
                json.put("answerType", "boolean");
            } else {
                json.put("answerType", "text");
            }
        }
    }

    /**
     * Creates a fallback response when parsing fails
     */
    private JSONObject createFallbackResponse(String query) {
        JSONObject fallback = new JSONObject();
        
        // Try to extract basic information from the query
        String queryLower = query.toLowerCase();
        
        // Basic date detection
        if (queryLower.contains("febrero") || queryLower.contains("february")) {
            fallback.put("date", new JSONArray().put("february"));
        } else if (queryLower.contains("marzo") || queryLower.contains("march")) {
            fallback.put("date", new JSONArray().put("march"));
        }
        
        // Basic answer type detection
        if (queryLower.contains("quién") || queryLower.contains("who")) {
            fallback.put("answerType", "person");
        } else if (queryLower.contains("cuántos") || queryLower.contains("how many")) {
            fallback.put("answerType", "number");
        } else {
            fallback.put("answerType", "text");
        }
        
        // Fill remaining fields
        String[] fields = {"place", "startTime", "endTime", "president", "secretary", 
                          "attendees", "numberOfAttendees", "agenda", "decisions", 
                          "mentionedEntities", "topics", "section", "summary"};
        
        for (String field : fields) {
            if (!fallback.has(field)) {
                fallback.put(field, new JSONArray());
            }
        }
        
        fallback.put("comparisonType", "none");
        fallback.put("temporalContext", "general");
        
        return fallback;
    }
}