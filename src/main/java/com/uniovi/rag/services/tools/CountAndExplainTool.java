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
 * Enhanced CountAndExplainTool for counting and explaining meeting minutes with intelligent NER analysis.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Temporal context filtering
 * - Semantic relevance evaluation
 * - Enhanced explanation generation
 */
public class CountAndExplainTool extends AbstractTool {

    public CountAndExplainTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().debug("Executing count and explain query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        List<Document> docs = retrieveDocuments(query);
        List<String> explanations = new ArrayList<>();
        int count = 0;

        // Filter documents based on NER if available
        if (ner != null) {
            // Use EnhancedNERHandler for intelligent filtering
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    String content = doc.getContent();
                    String date = extractDate(content);
                    String fragment = extractRelevantFragment(content, query);
                    explanations.add("Meeting minutes from " + date + ":\n" + fragment);
                    count++;
                }
            }
        } else {
            // Fallback to query-based relevance
            for (Document doc : docs) {
                String content = doc.getContent();
                String date = extractDate(content);
                String fragment = extractRelevantFragment(content, query);
                if (isRelevantToQuery(fragment, query)) {
                    explanations.add("Meeting minutes from " + date + ":\n" + fragment);
                    count++;
                }
            }
        }

        String response;
        if (count > 0) {
            response = generateResponseWithLLM(query, count, explanations);
        } else {
            response = generateNotFoundResponse(query);
        }
        return ToolResult.from(response, getClass());
    }

    /**
     * Determines if a fragment is relevant to the query using LLM
     */

    private boolean isRelevantToQuery(String fragment, String query) {
        // Use LLM to determine if the fragment is relevant to the question
        String prompt = String.format("""
            This is the user's question (in any language):
            "%s"
            
            This is a fragment from meeting minutes:
            "%s"
            
            Does this fragment clearly or partially answer the question? 
            Respond only with 'yes' or 'no' in the same language as the query.
            """, query, fragment);
        
        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();
        
        // Check for positive responses in multiple languages
        return result.contains("yes") || result.contains("sí") || result.contains("si") || 
               result.contains("oui") || result.contains("ja") || result.contains("da");
    }

    /**
     * Generates response with LLM using found explanations
     */
    private String generateResponseWithLLM(String query, int count, List<String> explanations) {
        String joined = explanations.stream().distinct().collect(Collectors.joining("\n\n"));
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Found %d relevant meeting minutes. Below is the context found:
            %s
            
            Write a brief and clear response in the same language as the query, 
            indicating the number of minutes found and summarizing the relevant context.
            """, query, count, joined);
        
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip();
    }
    
    /**
     * Generates not found response
     */
    private String generateNotFoundResponse(String query) {
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            No relevant information was found for this query in the available meeting minutes.
            
            Write a polite response in the same language as the query explaining that no relevant information was found.
            """, query);
        
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip();
    }
}
