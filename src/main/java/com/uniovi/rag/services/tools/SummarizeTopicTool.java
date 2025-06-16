package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;

public class SummarizeTopicTool extends AbstractTool {

    public SummarizeTopicTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveDocuments(query);
        List<String> fragments = new ArrayList<>();

        for (Document doc : docs) {
            if (ner != null) {
                if (matchesNER(doc, ner)) {
                    fragments.addAll(extractRelevantFragments(doc, query));
                }
            } else {
                if (isRelevantByLLM(doc.getContent(), query)) {
                    fragments.addAll(extractRelevantFragments(doc, query));
                }
            }
            if (fragments.size() >= 10) break;
        }

        if (fragments.isEmpty()) {
            String notFound = generateNotFoundMessage(query);
            return ToolResult.from(notFound, getClass());
        }

        String summary = generateSummaryWithLLM(query, fragments);
        return ToolResult.from(summary, getClass());
    }

    private boolean matchesNER(Document doc, JSONObject ner) {
        String[] fields = {"date", "place", "startTime", "endTime", "president", "secretary", "attendees", "numberOfAttendees", "agenda", "decisions", "mentionedEntities", "topics", "section", "summary"};
        String content = doc.getContent().toLowerCase();
        for (String field : fields) {
            if (ner.has(field)) {
                JSONArray arr = ner.optJSONArray(field);
                if (arr != null && arr.length() > 0) {
                    boolean anyMatch = false;
                    for (int i = 0; i < arr.length(); i++) {
                        String value = arr.getString(i).toLowerCase();
                        if (!value.isBlank() && content.contains(value)) {
                            anyMatch = true;
                            break;
                        }
                    }
                    if (!anyMatch) return false;
                }
            }
        }
        return true;
    }

    private boolean isRelevantByLLM(String content, String query) {
        String prompt = """
            Given the following user query (in any language):
            "%s"
            And the following minutes content:
            "%s"
            Does this minutes document match all the conditions in the query? 
            Answer only YES or NO (in the language of the query).
            """.formatted(query, content.substring(0, Math.min(1000, content.length())));
        String result = chatClient.prompt().user(prompt).call().content().strip().toLowerCase();
        return result.startsWith("yes") || result.startsWith("sí");
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
