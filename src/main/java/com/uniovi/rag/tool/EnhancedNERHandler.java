package com.uniovi.rag.tool;

import com.uniovi.rag.model.Loggable;
import com.uniovi.rag.model.Minute;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Enhanced NER Handler for intelligent entity matching and analysis.
 * 
 * Features:
 * - Semantic entity matching using LLM
 * - Intelligent normalization of dates, names, and values
 * - Context-aware filtering based on temporal and comparison context
 * - Multilingual support with adaptive prompts
 * - Decoupled from literal word matching
 * - Support for all NER fields including new ones (answerType, comparisonType, temporalContext)
 */
public class EnhancedNERHandler implements Loggable {

    private final ChatClient chatClient;
    
    // Date patterns for normalization - enhanced to match parseDateFlexible for consistency
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

    public EnhancedNERHandler(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Intelligently matches a document against NER entities using semantic analysis.
     * Uses English for internal processing, but preserves original language in content and query.
     */
    public boolean matchesDocumentWithNER(Document doc, JSONObject ner) {
        if (ner == null || ner.isEmpty()) return true;
        
        if (doc == null || doc.getText() == null || doc.getText().trim().isEmpty()) {
            return false;
        }
        
        String content = doc.getText();
        String prompt = generateDocumentMatchingPrompt(content, ner);
        
        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in matchesDocumentWithNER, defaulting to false");
                return false;
            }
            
            // Use LLM to interpret the response as yes/no
            return interpretBooleanResponse(result, "matchesDocumentWithNER");
        } catch (Exception e) {
            log().error("Error in matchesDocumentWithNER, defaulting to false", e);
            return false; // Default to false on error to avoid false positives
        }
    }

    /**
     * Intelligently matches a minute against NER entities using semantic analysis.
     * Uses English for internal processing, but preserves original language in metadata values.
     */
    public boolean matchesMinuteWithNER(Minute minute, JSONObject ner) {
        if (ner == null || ner.isEmpty()) return true;
        
        if (minute == null) {
            return false;
        }
        
        String prompt = generateMinuteMatchingPrompt(minute, ner);
        
        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in matchesMinuteWithNER, defaulting to true to avoid false negatives");
                return true;
            }
            
            // Use LLM to interpret boolean response
            return interpretBooleanResponse(result, "matchesMinuteWithNER");
        } catch (Exception e) {
            log().error("Error in matchesMinuteWithNER, defaulting to true to avoid false negatives", e);
            return true;
        }
    }

    /**
     * Filters documents based on temporal context from NER
     */
    public List<Document> filterDocumentsByTemporalContext(List<Document> docs, JSONObject ner) {
        if (ner == null || !ner.has("temporalContext")) return docs;
        
        String temporalContext = ner.getString("temporalContext");
        if (temporalContext.equals("none") || temporalContext.equals("general")) return docs;
        
        return docs.stream()
                .filter(doc -> matchesTemporalContext(doc, temporalContext))
                .collect(Collectors.toList());
    }

    /**
     * Filters minutes based on temporal context from NER
     */
    public List<Minute> filterMinutesByTemporalContext(List<Minute> minutes, JSONObject ner) {
        if (ner == null || !ner.has("temporalContext")) return minutes;
        
        String temporalContext = ner.getString("temporalContext");
        if (temporalContext.equals("none") || temporalContext.equals("general")) return minutes;
        
        return minutes.stream()
                .filter(minute -> matchesMinuteTemporalContext(minute, temporalContext))
                .collect(Collectors.toList());
    }

    /**
     * Determines comparison type from NER or infers it intelligently
     */
    public String determineComparisonType(String query, JSONObject ner) {
        if (ner != null && ner.has("comparisonType")) {
            String comparisonType = ner.getString("comparisonType");
            if (!comparisonType.equals("none")) {
                return comparisonType;
            }
        }
        
        // Infer comparison type intelligently using LLM
        return inferComparisonTypeWithLLM(query);
    }

    /**
     * Determines answer type from NER or infers it intelligently
     */
    public String determineAnswerType(String query, JSONObject ner) {
        if (ner != null && ner.has("answerType")) {
            String answerType = ner.getString("answerType");
            if (!answerType.equals("unknown")) {
                return answerType;
            }
        }
        
        // Infer answer type intelligently using LLM
        return inferAnswerTypeWithLLM(query);
    }

    /**
     * Extracts and normalizes date values from NER
     */
    public List<String> extractNormalizedDates(JSONObject ner) {
        if (ner == null || !ner.has("date")) return Collections.emptyList();
        
        JSONArray dates = ner.getJSONArray("date");
        List<String> normalizedDates = new ArrayList<>();
        
        for (int i = 0; i < dates.length(); i++) {
            String dateStr = dates.getString(i);
            String normalized = normalizeDate(dateStr);
            if (normalized != null) {
                normalizedDates.add(normalized);
            } else {
                normalizedDates.add(dateStr); // Keep original if can't normalize
            }
        }
        
        return normalizedDates;
    }

    /**
     * Extracts and normalizes time values from NER
     */
    public List<String> extractNormalizedTimes(JSONObject ner, String fieldName) {
        if (ner == null || !ner.has(fieldName)) return Collections.emptyList();
        
        JSONArray times = ner.getJSONArray(fieldName);
        List<String> normalizedTimes = new ArrayList<>();
        
        for (int i = 0; i < times.length(); i++) {
            String timeStr = times.getString(i);
            String normalized = normalizeTime(timeStr);
            if (normalized != null) {
                normalizedTimes.add(normalized);
            } else {
                normalizedTimes.add(timeStr);
            }
        }
        
        return normalizedTimes;
    }

    /**
     * Extracts and normalizes person names from NER
     */
    public List<String> extractNormalizedNames(JSONObject ner, String fieldName) {
        if (ner == null || !ner.has(fieldName)) return Collections.emptyList();
        
        JSONArray names = ner.getJSONArray(fieldName);
        List<String> normalizedNames = new ArrayList<>();
        
        for (int i = 0; i < names.length(); i++) {
            String nameStr = names.getString(i);
            String normalized = normalizeName(nameStr);
            if (normalized != null) {
                normalizedNames.add(normalized);
            } else {
                normalizedNames.add(nameStr);
            }
        }
        
        return normalizedNames;
    }

    /**
     * Extracts section information from NER for targeted analysis
     */
    public List<String> extractSections(JSONObject ner) {
        if (ner == null || !ner.has("section")) return Collections.emptyList();
        
        JSONArray sections = ner.getJSONArray("section");
        List<String> sectionList = new ArrayList<>();
        
        for (int i = 0; i < sections.length(); i++) {
            sectionList.add(sections.getString(i));
        }
        
        return sectionList;
    }

    /**
     * Checks if NER indicates a specific section query
     */
    public boolean isSectionSpecificQuery(JSONObject ner) {
        return ner != null && ner.has("section") && ner.getJSONArray("section").length() > 0;
    }

    /**
     * Checks if NER indicates a comparison query
     */
    public boolean isComparisonQuery(JSONObject ner) {
        return ner != null && ner.has("comparisonType") && 
               !ner.getString("comparisonType").equals("none");
    }

    /**
     * Checks if NER indicates a temporal query
     */
    public boolean isTemporalQuery(JSONObject ner) {
        return ner != null && ner.has("temporalContext") && 
               !ner.getString("temporalContext").equals("none") &&
               !ner.getString("temporalContext").equals("general");
    }

    // ============================================================================
    // PRIVATE HELPER METHODS
    // ============================================================================

    /**
     * Generates prompt for document matching.
     * Uses English for internal processing, but preserves original language in content.
     */
    private String generateDocumentMatchingPrompt(String content, JSONObject ner) {
        return String.format("""
            You are a document matching system. Analyze if a document matches specified entities.
            
            Document content (may be in any language):
            "%s"
            
            Entities to match (JSON format):
            %s
            
            Task: Determine if this document contains information that semantically matches the specified entities.
            
            Matching criteria:
            - Consider semantic meaning, not just exact word matches
            - Consider context and relationships between entities
            - Match dates, people, topics, and other entities semantically
            - If multiple entities are specified, all should be present or relevant
            
            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """, content.substring(0, Math.min(2000, content.length())), ner.toString(2));
    }

    /**
     * Generates prompt for minute matching.
     * Uses English for internal processing, but preserves original language in metadata values.
     */
    private String generateMinuteMatchingPrompt(Minute minute, JSONObject ner) {
        return String.format("""
            You are a meeting metadata matching system. Analyze if meeting metadata matches specified entities.
            
            Meeting metadata (values may be in any language):
            Date: %s
            Place: %s
            President: %s
            Secretary: %s
            Topics: %s
            Decisions: %s
            Summary: %s
            
            Entities to match (JSON format):
            %s
            
            Task: Determine if this meeting metadata semantically matches ALL the specified entities.
            
            Matching criteria:
            - Consider semantic meaning, not just exact word matches
            - Consider context and relationships between entities
            - Match dates, people, topics, and other entities semantically
            - If multiple entities are specified, all should be present or relevant in the metadata
            
            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """,
            minute.date() != null ? minute.date() : "unknown",
            minute.place() != null ? minute.place() : "unknown",
            minute.president() != null ? minute.president() : "unknown",
            minute.secretary() != null ? minute.secretary() : "unknown",
            minute.topics() != null ? String.join(", ", minute.topics()) : "unknown",
            minute.decisions() != null ? String.join(", ", minute.decisions()) : "unknown",
            minute.summary() != null ? minute.summary() : "unknown",
            ner.toString(2)
        );
    }

    /**
     * Checks if document matches temporal context.
     * Uses English for internal processing.
     */
    /**
     * Checks if document matches temporal context.
     * Uses English for internal processing, but preserves original language in content.
     */
    private boolean matchesTemporalContext(Document doc, String temporalContext) {
        if (doc == null || doc.getText() == null || doc.getText().trim().isEmpty()) {
            return false;
        }
        
        String content = doc.getText();
        String prompt = String.format("""
            You are a temporal context matching system. Analyze if a document matches a temporal context.
            
            Document content (may be in any language):
            "%s"
            
            Temporal context to match: "%s"
            
            Temporal context types:
            - "latest" or "last": most recent meeting
            - "oldest" or "first": earliest meeting
            - "past": past meetings
            - "future": future meetings
            - "specific_date": specific date mentioned
            - "range": date range
            - "current": current/recent meetings
            
            Task: Determine if this document's date/temporal information matches the specified temporal context.
            
            Consider:
            - Dates mentioned in the document
            - Time references and temporal indicators
            - Relative time expressions
            
            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """, content.substring(0, Math.min(1000, content.length())), temporalContext);
        
        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in matchesTemporalContext, defaulting to false");
                return false;
            }
            
            // Use LLM to interpret boolean response
            return interpretBooleanResponse(result, "matchesTemporalContext");
        } catch (Exception e) {
            log().error("Error in matchesTemporalContext, defaulting to false", e);
            return false; // Default to false on error to avoid false positives
        }
    }

    /**
     * Checks if minute matches temporal context.
     * Uses English for internal processing, but preserves original language in metadata values.
     */
    private boolean matchesMinuteTemporalContext(Minute minute, String temporalContext) {
        if (minute == null) {
            return false;
        }
        
        String prompt = String.format("""
            You are a temporal context matching system. Analyze if meeting metadata matches a temporal context.
            
            Meeting metadata (values may be in any language):
            Date: %s
            Place: %s
            Topics: %s
            Summary: %s
            
            Temporal context to match: "%s"
            
            Temporal context types:
            - "latest" or "last": most recent meeting
            - "oldest" or "first": earliest meeting
            - "past": past meetings
            - "future": future meetings
            - "specific_date": specific date mentioned
            - "range": date range
            - "current": current/recent meetings
            
            Task: Determine if this meeting's date matches the specified temporal context.
            
            Consider:
            - The date field in the metadata
            - Time references in topics or summary
            - Relative time expressions
            
            Respond with ONLY one word: YES or NO.
            Do not include any explanation or additional text.
            """,
            minute.date() != null ? minute.date() : "unknown",
            minute.place() != null ? minute.place() : "unknown",
            minute.topics() != null ? String.join(", ", minute.topics()) : "unknown",
            minute.summary() != null ? minute.summary() : "unknown",
            temporalContext
        );
        
        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content();
            
            if (result == null || result.trim().isEmpty()) {
                log().warn("Empty response from LLM in matchesMinuteTemporalContext, defaulting to false");
                return false;
            }
            
            // Use LLM to interpret boolean response
            return interpretBooleanResponse(result, "matchesMinuteTemporalContext");
        } catch (Exception e) {
            log().error("Error in matchesMinuteTemporalContext, defaulting to false", e);
            return false; // Default to false on error to avoid false positives
        }
    }

    /**
     * Infers comparison type using LLM.
     * Uses English for internal processing.
     */
    private String inferComparisonTypeWithLLM(String query) {
        String prompt = String.format("""
            You are a query analysis system. Analyze a user query to determine the type of comparison requested.
            
            User query (may be in any language):
            "%s"
            
            Task: Determine what type of comparison the user wants to make.
            
            Valid comparison types (respond with ONLY the type name in English):
            - duration: comparing meeting durations (e.g., "which meeting was longer")
            - attendees: comparing number of attendees (e.g., "which meeting had more people")
            - topics: comparing topics discussed (e.g., "which meetings discussed security")
            - decisions: comparing decisions made (e.g., "which meetings had more decisions")
            - place: comparing meeting places (e.g., "which meetings were in different locations")
            - date: comparing dates (e.g., "which meeting was earlier")
            - general: general comparison that doesn't fit the above categories
            
            Respond with ONLY the comparison type in English (one word).
            Do not include any explanation or additional text.
            """, query);
        
        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip()
                    .toLowerCase();
            
            // Extract the comparison type from response
            String cleaned = result.split("\\s+")[0].trim();
            return switch (cleaned) {
                case "duration", "attendees", "topics", "decisions", "place", "date", "general" -> cleaned;
                default -> "general";
            };
        } catch (Exception e) {
            return "general"; // Default fallback
        }
    }

    /**
     * Infers answer type using LLM.
     * Uses English for internal processing.
     */
    private String inferAnswerTypeWithLLM(String query) {
        String prompt = String.format("""
            You are a query analysis system. Analyze a user query to determine the expected answer type.
            
            User query (may be in any language):
            "%s"
            
            Task: Determine what type of answer the user expects.
            
            Valid answer types (respond with ONLY the type name in English):
            - person: asking about a person (who, quién)
            - number: asking about a number (how many, cuántos)
            - date: asking about a date (when, cuándo)
            - location: asking about a place (where, dónde)
            - text: asking for text/summary (what, qué)
            - boolean: asking yes/no question (is, does, se)
            - list: asking for a list (list, lista)
            - comparison: asking for comparison (compare, comparar)
            - duration: asking about duration (how long, cuánto tiempo)
            - field: asking about a specific field (which field, qué campo)
            
            Respond with ONLY the answer type in English (one word).
            Do not include any explanation or additional text.
            """, query);
        
        try {
            String result = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip()
                    .toLowerCase();
            
            // Extract the answer type from response
            String cleaned = result.split("\\s+")[0].trim();
            return switch (cleaned) {
                case "person", "number", "date", "location", "text", "boolean", "list", 
                     "comparison", "duration", "field" -> cleaned;
                default -> "text";
            };
        } catch (Exception e) {
            return "text"; // Default fallback
        }
    }

    /**
     * Normalizes a date string
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
     * Normalizes a time string
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
     * Normalizes a name string
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
     * Interprets LLM response as boolean using another LLM call.
     */
    private boolean interpretBooleanResponse(String response, String context) {
        if (response == null || response.trim().isEmpty()) {
            return false;
        }
        
        String prompt = String.format("""
            Context: %s
            
            The LLM generated this response: "%s"
            
            Task: Interpret this response as a boolean answer.
            - If it means YES/TRUE/POSITIVE, respond with: YES
            - If it means NO/FALSE/NEGATIVE, respond with: NO
            
            Consider semantic meaning, not just exact words.
            
            Respond with ONLY one word: YES or NO.
            """, context, response);
        
        try {
            String interpretation = chatClient
                    .prompt()
                    .user(prompt)
                    .call()
                    .content()
                    .strip()
                    .toUpperCase();
            
            return interpretation.contains("YES");
        } catch (Exception e) {
            log().warn("Error interpreting boolean response in {}, defaulting to false", context, e);
            return false;
        }
    }
}
