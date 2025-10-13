package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.uniovi.rag.utils.InfoExtractor.extractDate;
import static com.uniovi.rag.utils.InfoExtractor.extractRelevantFragment;

/**
 * Enhanced BooleanQueryTool for answering yes/no questions about meeting minutes.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Semantic analysis instead of literal matching
 * - Support for all NER fields including new ones
 * - Multilingual support with adaptive prompts
 * - Decoupled from literal word matching
 */
public class BooleanQueryTool extends AbstractTool {

    public BooleanQueryTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveDocuments(query);
        List<String> evidence = new ArrayList<>();
        boolean found = false;

        if (ner != null) {
            // Use enhanced NER filtering with semantic analysis
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    String fragment = extractRelevantFragment(doc.getContent(), query);
                    if (fragmentConfirmsClaim(query, fragment)) {
                        String date = extractDate(doc.getContent());
                        evidence.add(generateEvidenceMessage(date, fragment));
                        found = true;
                    }
                }
            }
        } else {
            // Baseline: for each document, ask LLM if it's relevant
            for (Document doc : docs) {
                String fragment = extractRelevantFragment(doc.getContent(), query);
                if (fragmentConfirmsClaim(query, fragment)) {
                    String date = extractDate(doc.getContent());
                    evidence.add(generateEvidenceMessage(date, fragment));
                    found = true;
                }
            }
        }

        String response;
        if (found) {
            response = generateResponseWithLLM(query, evidence);
        } else {
            response = generateNotFoundResponse(query);
        }
        return ToolResult.from(response, getClass());
    }

    /**
     * Checks if fragment confirms the claim using intelligent analysis
     */
    private boolean fragmentConfirmsClaim(String query, String fragment) {
        String answerType = nerHandler.determineAnswerType(query, null);
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Query type: %s
            
            And this fragment from meeting minutes:
            "%s"
            
            Does this fragment confirm or support the claim made in the query?
            Consider semantic meaning, not just exact matches.
            Consider the context and intent of the query.
            
            Answer only with 'yes' or 'no'.
            """, query, answerType, fragment);
        
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
     * Generates evidence message with proper formatting
     */
    private String generateEvidenceMessage(String date, String fragment) {
        return String.format("Yes, evidence found in the meeting of %s:\n%s", 
                           date != null ? date : "unknown date", fragment);
    }

    /**
     * Generates response using LLM with evidence
     */
    private String generateResponseWithLLM(String query, List<String> evidence) {
        String joinedEvidence = evidence.stream().distinct().collect(Collectors.joining("\n\n"));
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            The following evidence was found in the meeting minutes:
            %s
            
            Write a clear, concise response in the same language as the query, 
            using the evidence found. Be direct and factual.
            """, query, joinedEvidence);
        
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
            
            Write a short message indicating that no evidence was found for this claim, 
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