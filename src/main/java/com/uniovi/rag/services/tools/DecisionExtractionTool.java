package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.uniovi.rag.utils.InfoExtractor.extractDate;

/**
 * Enhanced DecisionExtractionTool for extracting decisions from meeting minutes with intelligent NER analysis.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Temporal context filtering
 * - Semantic decision relevance evaluation
 * - Enhanced decision extraction and summarization
 */
public class DecisionExtractionTool extends AbstractTool {

    public DecisionExtractionTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().debug("Executing decision extraction query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        List<Document> docs = retrieveDocuments(query);
        List<String> decisions = new ArrayList<>();

        // Filter documents based on NER if available
        if (ner != null) {
            // Use EnhancedNERHandler for intelligent filtering
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    String content = doc.getContent();
                    String date = extractDate(content);
                    List<String> fragments = extractDecisions(content, query);
                    for (String fragment : fragments) {
                        decisions.add("Meeting minutes from " + date + ":\n" + fragment);
                    }
                }
            }
        } else {
            // Fallback to query-based relevance
            for (Document doc : docs) {
                String content = doc.getContent();
                String date = extractDate(content);
                List<String> fragments = extractDecisions(content, query);
                for (String fragment : fragments) {
                    if (isDecisionRelevantToQuery(fragment, query)) {
                        decisions.add("Meeting minutes from " + date + ":\n" + fragment);
                    }
                }
            }
        }

        String response;
        if (!decisions.isEmpty()) {
            response = generateResponseWithLLM(query, decisions);
        } else {
            response = generateNotFoundResponse(query);
        }
        return ToolResult.from(response, getClass());
    }

    /**
     * Extracts decisions from content using intelligent fragment analysis
     */
    private List<String> extractDecisions(String content, String query) {
        // Split content into fragments and use LLM to determine if it's a relevant decision
        return Stream.of(content.split("(?<=[.:?])\\s*([\\n\\r])+"))
                .map(String::trim)
                .filter(p -> !p.isBlank())
                .filter(p -> isDecisionRelevantToQuery(p, query))
                .limit(10)
                .collect(Collectors.toList());
    }

    /**
     * Determines if a fragment contains a decision relevant to the query
     */
    private boolean isDecisionRelevantToQuery(String fragment, String query) {
        String prompt = String.format("""
            This is the user's query (in any language):
            "%s"
            
            This is a fragment from meeting minutes:
            "%s"
            
            Does this fragment contain a decision relevant to the query? 
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
     * Generates response with LLM using found decisions
     */
    private String generateResponseWithLLM(String query, List<String> decisions) {
        String joined = decisions.stream().distinct().collect(Collectors.joining("\n\n"));
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            Found the following relevant decisions in the meeting minutes:
            %s
            
            Write a brief and clear response in the same language as the query, 
            summarizing the decisions found and their context.
            """, query, joined);
        
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
            
            No relevant decisions were found for this query in the available meeting minutes.
            
            Write a polite response in the same language as the query explaining that no relevant decisions were found.
            """, query);
        
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip();
    }
}
