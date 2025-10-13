package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.uniovi.rag.utils.InfoExtractor.extractDate;

/**
 * Enhanced CountDocumentsTool for counting meeting minutes based on specific criteria.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Semantic analysis instead of literal matching
 * - Support for all NER fields including temporalContext and answerType
 * - Multilingual support with adaptive prompts
 * - Decoupled from literal word matching
 */
public class CountDocumentsTool extends AbstractTool {

    public CountDocumentsTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveDocuments(query);
        List<String> dates = new ArrayList<>();
        long count;

        if (ner != null) {
            // Use enhanced NER filtering with semantic analysis
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            count = filteredDocs.stream()
                    .filter(doc -> nerHandler.matchesDocumentWithNER(doc, ner))
                    .peek(doc -> dates.add(extractDate(doc.getContent())))
                    .count();
        } else {
            // Baseline: filter by query relevance
            count = docs.stream()
                    .filter(doc -> isRelevantToQuery(doc, query))
                    .peek(doc -> dates.add(extractDate(doc.getContent())))
                    .count();
        }

        String response = generateResponseWithLLM(query, count, dates);
        return ToolResult.from(response, getClass());
    }

    /**
     * Checks if document is relevant to query using intelligent analysis
     */
    private boolean isRelevantToQuery(Document doc, String query) {
        String answerType = nerHandler.determineAnswerType(query, null);
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            Query type: %s
            
            This is the content of a meeting minute:
            "%s"
            
            Does this meeting minute clearly or partially answer the query?
            Consider semantic meaning, not just exact matches.
            
            Answer only with 'yes' or 'no'.
            """, 
            query, 
            answerType,
            doc.getContent().substring(0, Math.min(1000, doc.getContent().length())));
        
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
     * Generates response using LLM
     */
    private String generateResponseWithLLM(String query, long count, List<String> dates) {
        String datesStr = dates.stream()
                .filter(date -> date != null && !date.isBlank())
                .distinct()
                .collect(Collectors.joining(", "));
        
        String prompt = String.format("""
            Given the following user query (in any language):
            "%s"
            
            %d relevant documents were found.
            The dates of the relevant documents are: %s
            
            Write a clear, concise response in the same language as the query, 
            using the number and dates.
            """, query, count, datesStr.isBlank() ? "[no dates]" : datesStr);
        
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip();
    }
}