package com.uniovi.rag.services.retriever;

import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.PgVectorStore;

public class FilteredContextRetriever extends AbstractContextRetriever {

    private static final String PROMPT_TEMPLATE = """
        You are a content filtering system for meeting minutes. Your task is to filter document content 
        by removing ONLY information that is irrelevant to the given question.
        
        CRITICAL REQUIREMENTS:
        1. Do NOT modify, summarize, or rephrase the content
        2. Do NOT add any headers, notes, comments, or explanations
        3. Preserve the ORIGINAL LANGUAGE of the content (if Spanish, keep Spanish; if English, keep English, etc.)
        4. Keep ALL information that might be useful to answer the question, even if indirectly related
        5. Only remove information that is completely irrelevant to the question
        
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
        
        Content to filter (may be in any language):
        "%s"
        
        Question (may be in any language):
        "%s"
        
        Key entities extracted from the question (JSON format):
        %s
        
        Return ONLY the filtered content in its original language, without any modifications or additions.
        If there is nothing relevant to the question or entities, return an empty string ('').
        """;

    public FilteredContextRetriever(PgVectorStore vectorStore, ChatClient chatClient, int topK, double similarityThreshold) {
        super(vectorStore, chatClient, topK, similarityThreshold);
    }

    @Override
    public String filterDocumentContent(Document doc, String query, JSONObject entities) {
        if (doc == null || doc.getContent() == null || doc.getContent().trim().isEmpty()) {
            return "";
        }
        
        if (query == null || query.trim().isEmpty()) {
            // If no query, return original content with optional metadata prefix
            return buildContentWithOptionalMetadataPrefix(doc, doc.getContent());
        }

        String contentWithPrefix = buildContentWithOptionalMetadataPrefix(doc, doc.getContent());
        String promptContent = truncateForPrompt(contentWithPrefix, DEFAULT_MAX_PROMPT_CHARS);

        try {
            String filterPrompt = entities == null || entities.isEmpty() ?
                    String.format(PROMPT_TEMPLATE, promptContent, query) :
                    String.format(NER_PROMPT_TEMPLATE, promptContent, query, 
                                 entities != null ? entities.toString(2) : "{}");

            String filteredContent = chatClient
                    .prompt()
                    .user(filterPrompt)
                    .call()
                    .content()
                    .trim();

            // Validate filtered content
            if (filteredContent == null || filteredContent.isEmpty()) {
                log().info("Filtered content is empty for document: {}", doc.getId());
                return "";
            }

            log().info("Filtered content length: {} (original: {})", 
                       filteredContent.length(), doc.getContent().length());
            return filteredContent;
        } catch (Exception e) {
            log().error("Error filtering document content, returning original content", e);
            // Return original content with optional metadata prefix as fallback
            return contentWithPrefix;
        }
    }
}
