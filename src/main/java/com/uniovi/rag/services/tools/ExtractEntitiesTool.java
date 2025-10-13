package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.uniovi.rag.utils.InfoExtractor.*;

/**
 * Enhanced ExtractEntitiesTool for extracting entities from meeting minutes.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Semantic analysis instead of literal matching
 * - Support for all NER fields including section and answerType
 * - Multilingual support with adaptive prompts
 * - Decoupled from literal word matching
 */
public class ExtractEntitiesTool extends AbstractTool {

    public ExtractEntitiesTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveDocuments(query);
        List<String> results = new ArrayList<>();

        if (ner != null) {
            // Use enhanced NER filtering with semantic analysis
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    String content = doc.getContent();
                    String date = extractDate(content);
                    String entities = extractRequestedEntities(content, query, ner);
                    if (!entities.isBlank()) {
                        results.add(generateEntityResult(date, entities));
                    }
                }
            }
        } else {
            // Baseline: extract entities from all documents
            for (Document doc : docs) {
                String content = doc.getContent();
                String date = extractDate(content);
                String entities = extractRequestedEntities(content, query, null);
                if (!entities.isBlank()) {
                    results.add(generateEntityResult(date, entities));
                }
            }
        }

        String response;
        if (!results.isEmpty()) {
            response = generateResponseWithLLM(query, results);
        } else {
            response = generateNotFoundResponse(query);
        }
        return ToolResult.from(response, getClass());
    }

    /**
     * Extracts requested entities using intelligent analysis
     */
    private String extractRequestedEntities(String content, String query, JSONObject ner) {
        String answerType = nerHandler.determineAnswerType(query, ner);
        List<String> sections = nerHandler.extractSections(ner);
        
        String attendees = String.join(", ", extractAttendees(content));
        String agenda = extractAgenda(content);
        String fragment = extractRelevantFragment(content, query);
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Query type: %s
            Target sections: %s
            
            This is the content of a meeting minute:
            "%s"
            
            Extracted attendees: %s
            Extracted agenda: %s
            Relevant fragment: %s
            
            Extract and list only the entities (people, attendees, positions, topics, etc.) 
            that are relevant to the query. Consider the query type and target sections.
            If no relevant entities are found, respond exactly: [EMPTY]
            """, 
            query, 
            answerType,
            sections.isEmpty() ? "all sections" : String.join(", ", sections),
            content.substring(0, Math.min(1000, content.length())), 
            attendees, 
            agenda != null ? agenda : "", 
            fragment);
        
        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip();
        
        return result.equalsIgnoreCase("[empty]") ? "" : result;
    }

    /**
     * Generates entity result with proper formatting
     */
    private String generateEntityResult(String date, String entities) {
        return String.format("Minutes from %s:\n%s", 
                           date != null ? date : "unknown date", entities);
    }

    /**
     * Generates response using LLM
     */
    private String generateResponseWithLLM(String query, List<String> results) {
        String joinedResults = results.stream().distinct().collect(Collectors.joining("\n\n"));
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            The following relevant entities were found in the meeting minutes:
            %s
            
            Write a clear, concise response in the same language as the query, 
            summarizing the entities found and their context.
            """, query, joinedResults);
        
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip();
    }

    /**
     * Generates not found response using LLM
     */
    private String generateNotFoundResponse(String query) {
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Write a short message indicating that no relevant entities were found for the query, 
            in the same language as the query.
            """, query);
        
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip();
    }
}