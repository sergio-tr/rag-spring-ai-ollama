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
 * Enhanced FilterAndListTool for filtering and listing meeting minutes with intelligent NER analysis.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Temporal context filtering
 * - Semantic relevance evaluation
 * - Enhanced summarization and listing
 */
public class FilterAndListTool extends AbstractTool {

    public FilterAndListTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().debug("Executing filter and list query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        List<Document> docs = retrieveDocuments(query);
        List<String> results = new ArrayList<>();

        // Filter documents based on NER if available
        if (ner != null) {
            // Use EnhancedNERHandler for intelligent filtering
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    String content = doc.getContent();
                    String date = extractDate(content);
                    String summary = extractAndSummarize(content, query);
                    results.add("Meeting minutes from " + date + ":\n" + summary);
                }
            }
        } else {
            // Fallback to LLM-based relevance
            for (Document doc : docs) {
                String content = doc.getContent();
                String date = extractDate(content);
                if (isRelevantByLLM(content, query)) {
                    String summary = extractAndSummarize(content, query);
                    results.add("Meeting minutes from " + date + ":\n" + summary);
                }
            }
        }

        String answer;
        if (!results.isEmpty()) {
            answer = generateFinalAnswer(query, results);
        } else {
            answer = generateNotFoundResponse(query);
        }
        return ToolResult.from(answer, getClass());
    }

    /**
     * Determines if content is relevant to query using LLM
     */
    private boolean isRelevantByLLM(String content, String query) {
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            And the following meeting minutes content:
            "%s"
            
            Does this minutes document match all the conditions in the query? 
            Answer only YES or NO in the same language as the query.
            """, query, content.substring(0, Math.min(1000, content.length())));
        
        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();
        
        // Check for positive responses in multiple languages
        return result.startsWith("yes") || result.startsWith("sí") || result.startsWith("si") || 
               result.startsWith("oui") || result.startsWith("ja") || result.startsWith("da");
    }

    /**
     * Extracts and summarizes relevant content
     */
    private String extractAndSummarize(String content, String query) {
        String fragment = extractRelevantFragment(content, query);
        String prompt = String.format("""
            Summarize in at most two sentences the fragment of the following text that answers this query (in any language): "%s"
            
            Text:
            %s
            
            Write the summary in the same language as the query.
            """, query, fragment);
        
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip();
    }

    /**
     * Generates final answer with found results
     */
    private String generateFinalAnswer(String query, List<String> results) {
        String joined = results.stream().distinct().collect(Collectors.joining("\n\n"));
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            The following meeting minutes matched the filters and their relevant content is:
            %s
            
            Write a brief and clear answer in the same language as the query, 
            listing the minutes and summarizing the relevant content for each.
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
            
            No meeting minutes were found that match all the conditions specified in the query.
            
            Write a polite response in the same language as the query explaining that no matching minutes were found.
            """, query);
        
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip();
    }
}
