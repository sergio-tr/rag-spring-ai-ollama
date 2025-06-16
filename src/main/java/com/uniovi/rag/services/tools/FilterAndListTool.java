package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.uniovi.rag.utils.InfoExtractor.extractDate;
import static com.uniovi.rag.utils.InfoExtractor.extractRelevantFragment;

public class FilterAndListTool extends AbstractTool {

    public FilterAndListTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveDocuments(query);
        List<String> results = new ArrayList<>();

        if (ner != null) {
            for (Document doc : docs) {
                if (matchesNER(doc, ner)) {
                    String content = doc.getContent();
                    String date = extractDate(content);
                    String summary = extractAndSummarize(content, query);
                    results.add("Minutes from " + date + ":\n" + summary);
                }
            }
        } else {
            for (Document doc : docs) {
                String content = doc.getContent();
                String date = extractDate(content);
                if (isRelevantByLLM(content, query)) {
                    String summary = extractAndSummarize(content, query);
                    results.add("Minutes from " + date + ":\n" + summary);
                }
            }
        }

        String answer;
        if (!results.isEmpty()) {
            answer = generateFinalAnswer(query, results);
        } else {
            answer = "No minutes found that match all the conditions specified in the query: '" + query + "'.";
        }
        return ToolResult.from(answer, getClass());
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
            Given the following user query:\n"%s"\nand the following minutes content:\n"%s"\n\nDoes this minutes document match all the conditions in the query? Answer only YES or NO.
            """.formatted(query, content.substring(0, Math.min(1000, content.length())));
        String result = chatClient.prompt().user(prompt).call().content().strip().toLowerCase();
        return result.startsWith("yes");
    }

    private String extractAndSummarize(String content, String query) {
        String fragment = extractRelevantFragment(content, query);
        String prompt = """
            Summarize in at most two sentences the fragment of the following text that answers this query: "%s"
            Text:
            %s
            """.formatted(query, fragment);
        return chatClient.prompt().user(prompt).call().content().strip();
    }

    private String generateFinalAnswer(String query, List<String> results) {
        String joined = results.stream().distinct().collect(Collectors.joining("\n\n"));
        String prompt = """
            The user asked: "%s"
            The following minutes matched the filters and their relevant content is:
            %s
            Write a brief and clear answer in Spanish, listing the minutes and summarizing the relevant content for each.
            """.formatted(query, joined);
        return chatClient.prompt().user(prompt).call().content().strip();
    }
}
