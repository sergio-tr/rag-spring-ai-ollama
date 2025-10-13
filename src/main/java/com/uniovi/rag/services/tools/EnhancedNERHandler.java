package com.uniovi.rag.services.tools;

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
public class EnhancedNERHandler {

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

    public EnhancedNERHandler(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    /**
     * Intelligently matches a document against NER entities using semantic analysis
     */
    public boolean matchesDocumentWithNER(Document doc, JSONObject ner) {
        if (ner == null || ner.isEmpty()) return true;
        
        String content = doc.getContent();
        String prompt = generateDocumentMatchingPrompt(content, ner);
        
        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();
        
        return result.contains("yes") || result.contains("sí");
    }

    /**
     * Intelligently matches a minute against NER entities using semantic analysis
     */
    public boolean matchesMinuteWithNER(Minute minute, JSONObject ner) {
        if (ner == null || ner.isEmpty()) return true;
        
        String prompt = generateMinuteMatchingPrompt(minute, ner);
        
        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();
        
        return result.contains("yes") || result.contains("sí");
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
     * Generates prompt for document matching
     */
    private String generateDocumentMatchingPrompt(String content, JSONObject ner) {
        return String.format("""
            Given the following document content (in any language):
            "%s"
            
            And these NER entities to match:
            %s
            
            Does this document contain information that matches the specified entities?
            Consider semantic meaning, not just exact matches.
            Consider the context and relationships between entities.
            
            Answer only with YES or NO.
            """, content.substring(0, Math.min(2000, content.length())), ner.toString(2));
    }

    /**
     * Generates prompt for minute matching
     */
    private String generateMinuteMatchingPrompt(Minute minute, JSONObject ner) {
        return String.format("""
            Given the following meeting metadata:
            Date: %s
            Place: %s
            President: %s
            Secretary: %s
            Topics: %s
            Decisions: %s
            Summary: %s
            
            And these NER entities to match:
            %s
            
            Does this meeting match all the specified entities?
            Consider semantic meaning, not just exact matches.
            Consider the context and relationships between entities.
            
            Answer only with YES or NO.
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
     * Checks if document matches temporal context
     */
    private boolean matchesTemporalContext(Document doc, String temporalContext) {
        String content = doc.getContent();
        String prompt = String.format("""
            Given the following document content:
            "%s"
            
            And the temporal context: "%s"
            
            Does this document match the specified temporal context?
            Consider dates, time references, and temporal indicators.
            
            Answer only with YES or NO.
            """, content.substring(0, Math.min(1000, content.length())), temporalContext);
        
        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();
        
        return result.contains("yes") || result.contains("sí");
    }

    /**
     * Checks if minute matches temporal context
     */
    private boolean matchesMinuteTemporalContext(Minute minute, String temporalContext) {
        String prompt = String.format("""
            Given the following meeting metadata:
            Date: %s
            Place: %s
            Topics: %s
            Summary: %s
            
            And the temporal context: "%s"
            
            Does this meeting match the specified temporal context?
            Consider dates, time references, and temporal indicators.
            
            Answer only with YES or NO.
            """,
            minute.date() != null ? minute.date() : "unknown",
            minute.place() != null ? minute.place() : "unknown",
            minute.topics() != null ? String.join(", ", minute.topics()) : "unknown",
            minute.summary() != null ? minute.summary() : "unknown",
            temporalContext
        );
        
        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();
        
        return result.contains("yes") || result.contains("sí");
    }

    /**
     * Infers comparison type using LLM
     */
    private String inferComparisonTypeWithLLM(String query) {
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Determine what type of comparison the user wants to make. Choose one of the following:
            - duration: comparing meeting durations
            - attendees: comparing number of attendees
            - topics: comparing topics discussed
            - decisions: comparing decisions made
            - place: comparing meeting places
            - date: comparing dates
            - general: general comparison
            
            Answer with only the comparison type in English.
            """, query);
        
        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();
        
        return switch (result) {
            case "duration", "attendees", "topics", "decisions", "place", "date", "general" -> result;
            default -> "general";
        };
    }

    /**
     * Infers answer type using LLM
     */
    private String inferAnswerTypeWithLLM(String query) {
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Determine what type of answer the user expects. Choose one of the following:
            - person: asking about a person (who)
            - number: asking about a number (how many)
            - date: asking about a date (when)
            - location: asking about a place (where)
            - text: asking for text/summary
            - boolean: asking yes/no question
            - list: asking for a list
            - comparison: asking for comparison
            - duration: asking about duration
            - field: asking about a specific field
            
            Answer with only the answer type in English.
            """, query);
        
        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();
        
        return switch (result) {
            case "person", "number", "date", "location", "text", "boolean", "list", 
                 "comparison", "duration", "field" -> result;
            default -> "text";
        };
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
}
