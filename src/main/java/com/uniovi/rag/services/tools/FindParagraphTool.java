package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;

import static com.uniovi.rag.utils.InfoExtractor.extractDate;

public class FindParagraphTool extends AbstractTool {

    public FindParagraphTool(ChatClient chatClient, ContextRetriever retriever) {
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
                    results.addAll(findRelevantParagraphs(doc, query));
                }
            }
        } else {
            for (Document doc : docs) {
                results.addAll(findRelevantParagraphsByLLM(doc, query));
            }
        }

        String answer;
        if (!results.isEmpty()) {
            answer = generateFinalAnswer(query, results);
        } else {
            answer = "No relevant paragraphs found for the query: '" + query + "'.";
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

    private List<String> findRelevantParagraphs(Document doc, String query) {
        List<String> relevant = new ArrayList<>();
        String content = doc.getContent();
        String[] paragraphs = content.split("(?<=[.:?])\\s*([\\n\\r])+");
        String date = extractDate(content);
        for (String p : paragraphs) {
            if (isParagraphRelevantByLLM(query, p)) {
                relevant.add("Minutes from " + date + ":\n" + p.trim());
            }
        }
        return relevant;
    }

    private List<String> findRelevantParagraphsByLLM(Document doc, String query) {
        List<String> relevant = new ArrayList<>();
        String content = doc.getContent();
        String[] paragraphs = content.split("(?<=[.:?])\\s*([\\n\\r])+");
        String date = extractDate(content);
        for (String p : paragraphs) {
            if (isParagraphRelevantByLLM(query, p)) {
                relevant.add("Minutes from " + date + ":\n" + p.trim());
            }
        }
        return relevant;
    }

    private boolean isParagraphRelevantByLLM(String query, String paragraph) {
        String prompt = """
            This is the user's query:
            "%s"
            And this is a paragraph from the minutes:
            "%s"
            Does the paragraph clearly or partially answer the query? Answer only YES or NO.
            """.formatted(query, paragraph);
        String result = chatClient.prompt().user(prompt).call().content().strip().toLowerCase();
        return result.startsWith("yes") || result.contains("sí");
    }

    private String generateFinalAnswer(String query, List<String> results) {
        String joined = String.join("\n\n", results);
        String prompt = """
            The user asked: "%s"
            The following paragraphs from the minutes are relevant:
            %s
            Write a brief and clear answer in Spanish, summarizing the relevant information from all the paragraphs found.
            """.formatted(query, joined);
        return chatClient.prompt().user(prompt).call().content().strip();
    }
}
