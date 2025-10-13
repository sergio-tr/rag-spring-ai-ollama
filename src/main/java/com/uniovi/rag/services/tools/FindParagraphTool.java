package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;

import static com.uniovi.rag.utils.InfoExtractor.extractDate;

/**
 * Enhanced FindParagraphTool for finding relevant paragraphs in meeting minutes with intelligent NER analysis.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Temporal context filtering
 * - Semantic paragraph relevance evaluation
 * - Enhanced paragraph extraction and summarization
 */
public class FindParagraphTool extends AbstractTool {

    public FindParagraphTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().debug("Executing find paragraph query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        List<Document> docs = retrieveDocuments(query);
        List<String> results = new ArrayList<>();

        // Filter documents based on NER if available
        if (ner != null) {
            // Use EnhancedNERHandler for intelligent filtering
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    results.addAll(findRelevantParagraphs(doc, query));
                }
            }
        } else {
            // Fallback to LLM-based relevance
            for (Document doc : docs) {
                results.addAll(findRelevantParagraphsByLLM(doc, query));
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
     * Finds relevant paragraphs in a document
     */
    private List<String> findRelevantParagraphs(Document doc, String query) {
        List<String> relevant = new ArrayList<>();
        String content = doc.getContent();
        String[] paragraphs = content.split("(?<=[.:?])\\s*([\\n\\r])+");
        String date = extractDate(content);
        
        for (String paragraph : paragraphs) {
            if (isParagraphRelevantByLLM(query, paragraph)) {
                relevant.add("Meeting minutes from " + date + ":\n" + paragraph.trim());
            }
        }
        return relevant;
    }

    /**
     * Finds relevant paragraphs using LLM-based relevance
     */
    private List<String> findRelevantParagraphsByLLM(Document doc, String query) {
        List<String> relevant = new ArrayList<>();
        String content = doc.getContent();
        String[] paragraphs = content.split("(?<=[.:?])\\s*([\\n\\r])+");
        String date = extractDate(content);
        
        for (String paragraph : paragraphs) {
            if (isParagraphRelevantByLLM(query, paragraph)) {
                relevant.add("Meeting minutes from " + date + ":\n" + paragraph.trim());
            }
        }
        return relevant;
    }

    /**
     * Determines if a paragraph is relevant to the query using LLM
     */
    private boolean isParagraphRelevantByLLM(String query, String paragraph) {
        String prompt = String.format("""
            This is the user's query (in any language):
            "%s"
            
            And this is a paragraph from the meeting minutes:
            "%s"
            
            Does the paragraph clearly or partially answer the query? 
            Answer only YES or NO in the same language as the query.
            """, query, paragraph);
        
        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();
        
        // Check for positive responses in multiple languages
        return result.startsWith("yes") || result.contains("sí") || result.contains("si") || 
               result.contains("oui") || result.contains("ja") || result.contains("da");
    }

    /**
     * Generates final answer with found paragraphs
     */
    private String generateFinalAnswer(String query, List<String> results) {
        String joined = String.join("\n\n", results);
        String prompt = String.format("""
            The user asked (in any language): "%s"
            
            The following paragraphs from the meeting minutes are relevant:
            %s
            
            Write a brief and clear answer in the same language as the query, 
            summarizing the relevant information from all the paragraphs found.
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
            
            No relevant paragraphs were found for this query in the available meeting minutes.
            
            Write a polite response in the same language as the query explaining that no relevant paragraphs were found.
            """, query);
        
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip();
    }
}
