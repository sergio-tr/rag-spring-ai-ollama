package com.uniovi.rag.service.retriever;

import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.pgvector.PgVectorStore;

public class MinuteDocumentContextRetriever extends AbstractMetadataContextRetriever {

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
        if (doc == null) {
            return "";
        }
        String text = doc.getText();
        if (text == null || text.trim().isEmpty()) {
            return "";
        }
        
        if (query == null || query.trim().isEmpty()) {
            // If no query, return original content with optional metadata prefix
            return buildContentWithOptionalMetadataPrefix(doc, text);
        }

        String contentWithPrefix = buildContentWithOptionalMetadataPrefix(doc, text);
        String promptContent = truncateForPrompt(contentWithPrefix, DEFAULT_MAX_PROMPT_CHARS);

        // This prevents wasting LLM calls on documents that don't match basic criteria
        if (entities != null && !entities.isEmpty() && !matchesDocumentMetadata(doc, entities)) {
            log().info("Document {} filtered out by metadata/NER matching before content filtering", doc.getId());
            return ""; // Document doesn't match NER criteria, return empty
        }

        try {
            String filterPrompt = entities == null || entities.isEmpty() ?
                    String.format(PROMPT_TEMPLATE, promptContent, query) :
                    String.format(NER_PROMPT_TEMPLATE, promptContent, query, 
                                 entities != null ? entities.toString(2) : "{}");

            String rawContent = chatClient
                    .prompt()
                    .user(filterPrompt)
                    .call()
                    .content();
            String filteredContent = rawContent != null ? rawContent.trim() : "";

            // Validate filtered content
            if (filteredContent == null || filteredContent.isEmpty()) {
                return "";
            }

            return filteredContent;
        } catch (Exception e) {
            log().error("Error filtering document content, returning original content", e);
            // Return original content with optional metadata prefix as fallback
            return contentWithPrefix;
        }
    }
    
}
