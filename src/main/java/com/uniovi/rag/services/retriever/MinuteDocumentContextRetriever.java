package com.uniovi.rag.services.retriever;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.PgVectorStore;

import java.util.Map;

public class MinuteDocumentContextRetriever extends FilteredContextRetriever {

    private final static String PROMPT_TEMPLATE = """
        You are a content filtering system for meeting minutes. Your task is to filter document content 
        by removing ONLY information that is irrelevant to the given question.
        
        CRITICAL REQUIREMENTS:
        1. Do NOT modify, summarize, or rephrase the content
        2. Do NOT add any headers, notes, comments, or explanations
        3. Preserve the ORIGINAL LANGUAGE of the content (if Spanish, keep Spanish; if English, keep English, etc.)
        4. Keep ALL information that might be useful to answer the question, even if indirectly related
        5. Only remove information that is completely irrelevant to the question
        
        The content of homeowners' association meeting minutes follows a structure like this:
        - MINUTES OF THE HOMEOWNERS' ASSOCIATION MEETING
        - Date (day, month, and year)
        - Location
        - Start time
        - End time
        - List of Attendees: number of attendees, names, and roles (for example: chairperson, secretary)
        - Agenda: topics discussed during the meeting, including Agreements, Announcements, Decisions Made, and approved or voted resolutions
        - Questions and Requests: open interventions at the end of the session
        - Meeting end time
        
        Content to filter (may be in any language):
        "%s"
        
        Question (may be in any language):
        "%s"
        
        Return ONLY the filtered content in its original language, without any modifications or additions.
        If there is nothing relevant to the question, return an empty string ('').
        """;

    private static final String NER_PROMPT_TEMPLATE = """
        You are a content filtering system for meeting minutes. Your task is to filter document content 
        by removing ONLY information that is irrelevant to the given question and extracted entities.
        
        CRITICAL REQUIREMENTS:
        1. Do NOT modify, summarize, or rephrase the content
        2. Do NOT add any headers, notes, comments, or explanations
        3. Preserve the ORIGINAL LANGUAGE of the content (if Spanish, keep Spanish; if English, keep English, etc.)
        4. Keep ALL information that might be useful to answer the question, even if indirectly related
        5. Use the extracted entities to help identify relevant content
        6. Only remove information that is completely irrelevant to both the question AND the entities
        
        The content of homeowners' association meeting minutes follows a structure like this:
        - MINUTES OF THE HOMEOWNERS' ASSOCIATION MEETING
        - Date (day, month, and year)
        - Location
        - Start time
        - End time
        - List of Attendees: number of attendees, names, and roles (for example: chairperson, secretary)
        - Agenda: topics discussed during the meeting, including Agreements, Announcements, Decisions Made, and approved or voted resolutions
        - Questions and Requests: open interventions at the end of the session
        - Meeting end time
        
        Content to filter (may be in any language):
        "%s"
        
        Question (may be in any language):
        "%s"
        
        Key entities extracted from the question (JSON format):
        %s
        
        Return ONLY the filtered content in its original language, without any modifications or additions.
        If there is nothing relevant to the question or entities, return an empty string ('').
        """;



    public MinuteDocumentContextRetriever(PgVectorStore vectorStore, ChatClient chatClient, int topK, double similarityThreshold) {
        super(vectorStore, chatClient, topK, similarityThreshold);
    }

    @Override
    public String filterDocumentContent(Document doc, String query, JSONObject entities) {
        if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
            return "";
        }
        
        if (query == null || query.trim().isEmpty()) {
            // If no query, return original content
            return doc.getContent();
        }

        // This prevents wasting LLM calls on documents that don't match basic criteria
        if (entities != null && !entities.isEmpty() && !matchesDocumentMetadata(doc, entities)) {
            log().debug("Document {} filtered out by metadata/NER matching before content filtering", doc.getId());
            return ""; // Document doesn't match NER criteria, return empty
        }

        try {
            String filterPrompt = entities == null || entities.isEmpty() ?
                    String.format(PROMPT_TEMPLATE, doc.getContent(), query) :
                    String.format(NER_PROMPT_TEMPLATE, doc.getContent(), query, 
                                 entities != null ? entities.toString(2) : "{}");

            String filteredContent = chatClient
                    .prompt()
                    .user(filterPrompt)
                    .call()
                    .content()
                    .trim();

            // Validate filtered content
            if (filteredContent == null || filteredContent.isEmpty()) {
                return "";
            }

            return filteredContent;
        } catch (Exception e) {
            log().error("Error filtering document content, returning original content", e);
            // Return original content as fallback instead of empty string
            return doc.getContent();
        }
    }
    
    /**
     * Pre-filters documents using metadata and NER entities before expensive LLM filtering.
     * Checks if document metadata matches NER entities (date, person, place, etc.).
     * This is a fast pre-filter to avoid LLM calls on clearly irrelevant documents.
     */
    private boolean matchesDocumentMetadata(Document doc, JSONObject ner) {
        if (doc == null || ner == null || ner.isEmpty()) {
            return true; // If no NER, don't filter out
        }
        
        Map<String, Object> metadata = doc.getMetadata();
        if (metadata == null || metadata.isEmpty()) {
            return true; // If no metadata, can't filter, so keep it
        }
        
        // Check date matching
        if (ner.has("date") && !ner.getJSONArray("date").isEmpty()) {
            String docDate = (String) metadata.get("date");
            if (docDate != null && !docDate.trim().isEmpty()) {
                JSONArray nerDates = ner.getJSONArray("date");
                boolean dateMatches = false;
                for (int i = 0; i < nerDates.length(); i++) {
                    String nerDate = nerDates.getString(i);
                    // Simple date matching (can be improved with date normalization)
                    if (docDate.contains(nerDate) || nerDate.contains(docDate) || 
                        datesAreSimilar(docDate, nerDate)) {
                        dateMatches = true;
                        break;
                    }
                }
                if (!dateMatches) {
                    log().debug("Document {} filtered out by date mismatch: docDate={}, nerDates={}", 
                               doc.getId(), docDate, nerDates);
                    return false;
                }
            }
        }
        
        // Check person matching (president, secretary)
        if (ner.has("person") && !ner.getJSONArray("person").isEmpty()) {
            String docPresident = (String) metadata.get("president");
            String docSecretary = (String) metadata.get("secretary");
            JSONArray nerPersons = ner.getJSONArray("person");
            
            boolean personMatches = false;
            for (int i = 0; i < nerPersons.length(); i++) {
                String nerPerson = nerPersons.getString(i).toLowerCase();
                if ((docPresident != null && docPresident.toLowerCase().contains(nerPerson)) ||
                    (docSecretary != null && docSecretary.toLowerCase().contains(nerPerson))) {
                    personMatches = true;
                    break;
                }
            }
            if (!personMatches) {
                log().debug("Document {} filtered out by person mismatch: president={}, secretary={}, nerPersons={}", 
                           doc.getId(), docPresident, docSecretary, nerPersons);
                return false;
            }
        }
        
        // Check place matching
        if (ner.has("place") && !ner.getJSONArray("place").isEmpty()) {
            String docPlace = (String) metadata.get("place");
            if (docPlace != null && !docPlace.trim().isEmpty()) {
                JSONArray nerPlaces = ner.getJSONArray("place");
                boolean placeMatches = false;
                for (int i = 0; i < nerPlaces.length(); i++) {
                    String nerPlace = nerPlaces.getString(i).toLowerCase();
                    if (docPlace.toLowerCase().contains(nerPlace)) {
                        placeMatches = true;
                        break;
                    }
                }
                if (!placeMatches) {
                    log().debug("Document {} filtered out by place mismatch: docPlace={}, nerPlaces={}", 
                               doc.getId(), docPlace, nerPlaces);
                    return false;
                }
            }
        }
        
        // If we get here, document passed all metadata filters
        return true;
    }
    
    /**
     * Simple heuristic to check if two date strings are similar.
     * Can be improved with proper date parsing and normalization.
     */
    private boolean datesAreSimilar(String date1, String date2) {
        if (date1 == null || date2 == null) return false;
        
        // Extract year from both dates
        String year1 = extractYear(date1);
        String year2 = extractYear(date2);
        
        if (year1 != null && year2 != null && year1.equals(year2)) {
            // Same year, check if month/day are similar
            String monthDay1 = extractMonthDay(date1);
            String monthDay2 = extractMonthDay(date2);
            return monthDay1 != null && monthDay2 != null && 
                   (monthDay1.contains(monthDay2) || monthDay2.contains(monthDay1));
        }
        
        return false;
    }
    
    private String extractYear(String date) {
        // Try to extract 4-digit year
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\b(\\d{4})\\b");
        java.util.regex.Matcher matcher = pattern.matcher(date);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
    
    private String extractMonthDay(String date) {
        // Extract month name or number
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile(
            "(?i)(enero|febrero|marzo|abril|mayo|junio|julio|agosto|septiembre|octubre|noviembre|diciembre|january|february|march|april|may|june|july|august|september|october|november|december)");
        java.util.regex.Matcher matcher = pattern.matcher(date);
        if (matcher.find()) {
            return matcher.group(1).toLowerCase();
        }
        // Try to extract day number
        pattern = java.util.regex.Pattern.compile("\\b(\\d{1,2})\\b");
        matcher = pattern.matcher(date);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

}
