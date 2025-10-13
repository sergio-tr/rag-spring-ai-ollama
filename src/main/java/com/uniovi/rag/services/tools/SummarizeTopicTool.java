package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced SummarizeTopicTool for summarizing specific topics from meeting minutes with intelligent NER analysis.
 * 
 * Features:
 * - Intelligent NER-based filtering using EnhancedNERHandler
 * - Temporal context filtering
 * - Semantic relevance evaluation
 * - Enhanced topic summarization and analysis
 */
public class SummarizeTopicTool extends AbstractTool {

    public SummarizeTopicTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        
        log().debug("Executing summarize topic query: {} with NER: {}", query, ner != null ? ner.toString() : "null");
        
        List<Document> docs = retrieveDocuments(query);
        List<String> fragments = new ArrayList<>();

        // Filter documents based on NER if available
        if (ner != null) {
            // Use EnhancedNERHandler for intelligent filtering
            List<Document> filteredDocs = nerHandler.filterDocumentsByTemporalContext(docs, ner);
            
            for (Document doc : filteredDocs) {
                if (nerHandler.matchesDocumentWithNER(doc, ner)) {
                    fragments.addAll(extractRelevantFragments(doc, query));
                }
                if (fragments.size() >= 10) break;
            }
        } else {
            // Fallback to LLM-based relevance
            for (Document doc : docs) {
                if (isRelevantByLLM(doc.getContent(), query)) {
                    fragments.addAll(extractRelevantFragments(doc, query));
                }
                if (fragments.size() >= 10) break;
            }
        }

        if (fragments.isEmpty()) {
            String notFound = generateNotFoundMessage(query);
            return ToolResult.from(notFound, getClass());
        }

        String summary = generateSummaryWithLLM(query, fragments);
        return ToolResult.from(summary, getClass());
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
    
    private List<String> extractRelevantFragments(Document doc, String query) {
        List<String> relevant = new ArrayList<>();
        String content = doc.getContent();
        String[] paragraphs = content.split("(?<=[.:?])\\s*([\\n\\r])+");
        for (String p : paragraphs) {
            if (isParagraphRelevantByLLM(query, p)) {
                relevant.add(p.trim());
            }
        }
        return relevant;
    }

    private boolean isParagraphRelevantByLLM(String query, String paragraph) {
        String prompt = """
            Given the following user query (in any language):
            "%s"
            And this is a paragraph from the minutes:
            "%s"
            Does the paragraph clearly or partially answer the query? 
            Answer only YES or NO (in the language of the query).
            """.formatted(query, paragraph);
        String result = chatClient.prompt().user(prompt).call().content().strip().toLowerCase();
        return result.startsWith("yes") || result.startsWith("sí");
    }

    private String generateSummaryWithLLM(String query, List<String> fragments) {
        String joined = String.join("\n\n", fragments);
        String prompt = """
            Given the following user query (in any language):
            "%s"
            The following are relevant fragments from the minutes:
            "%s"
            Write a brief and clear summary in the same language as the query, 
            indicating the key points mentioned about the topic. 
            Avoid literal repetition and organize the information clearly.
            """.formatted(query, joined);
        return chatClient.prompt().user(prompt).call().content().strip();
    }

    private String generateNotFoundMessage(String query) {
        String prompt = """
            Given the following user query (in any language):
            "%s"
            Write a short message indicating that no information was found related to the query, in the same language as the query.
            """.formatted(query);
        return chatClient.prompt().user(prompt).call().content().strip();
    }
}
