package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.model.Minute;
import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;

public class MetadataBooleanQueryTool extends AbstractMetadataTool {

    public MetadataBooleanQueryTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveDocuments(query);
        List<String> evidence = new ArrayList<>();
        for (Document doc : docs) {
            Minute minute = getMinuteFromMetadata(doc);
            if (minute == null) continue;
            if (ner != null) {
                if (matchesNER(minute, ner)) {
                    String ev = extractEvidenceFromMinute(query, minute);
                    if (!ev.isBlank()) evidence.add(ev);
                }
            } else {
                if (isRelevantByLLM(query, minute)) {
                    String ev = extractEvidenceFromMinute(query, minute);
                    if (!ev.isBlank()) evidence.add(ev);
                }
            }
        }
        String answer;
        if (!evidence.isEmpty()) {
            answer = generateBooleanAnswerWithLLM(query, evidence);
        } else {
            answer = generateNotFoundMessage(query);
        }
        return ToolResult.from(answer, getClass());
    }

    private boolean matchesNER(Minute minute, JSONObject ner) {
        return matchesMinuteWithNER(minute, ner);
    }

    private boolean isRelevantByLLM(String query, Minute minute) {
        String prompt = """
        Given the following user query (in any language):
        \"%s\"
        And the following meeting metadata:
        %s
        Does this meeting match all the conditions in the query? 
        Answer only YES or NO.
        """.formatted(query, minute.toString());
        String result = chatClient.prompt().user(prompt).call().content().strip().toLowerCase();
        return result.startsWith("yes");
    }

    private String extractEvidenceFromMinute(String query, Minute minute) {
        // Use LLM to extract relevant evidence instead of direct string matching
        String prompt = """
                Given the following user query (in any language):
                "%s"
                
                And the following meeting information:
                Decisions: %s
                Topics: %s
                Summary: %s
                
                Extract the most relevant evidence that helps answer the query.
                Format each piece of evidence with its type (Decision/Topic/Summary).
                If no evidence is relevant, return an empty string.
                """.formatted(
                    query,
                    minute.decisions() != null ? String.join(", ", minute.decisions()) : "",
                    minute.topics() != null ? String.join(", ", minute.topics()) : "",
                    minute.summary() != null ? minute.summary() : ""
                );
        
        return chatClient.prompt().user(prompt).call().content().strip();
    }

    private String generateBooleanAnswerWithLLM(String query, List<String> evidence) {
        String joined = String.join("\n\n", evidence);
        String prompt = """
        Given the following user query (in any language):
        \"%s\"
        The following is evidence from meeting metadata:
        %s
        Write a clear and direct answer in the same language as the query, 
        indicating if the evidence allows to answer YES, NO, or PARTIALLY to the query. Be concise but informative.
        """.formatted(query, joined);
        return chatClient.prompt().user(prompt).call().content().strip();
    }

    private String generateNotFoundMessage(String query) {
        String prompt = """
        Given the following user query (in any language):
        \"%s\"
        Write a short message indicating that no information was found related to the query, in the same language as the query.
        """.formatted(query);
        return chatClient.prompt().user(prompt).call().content().strip();
    }
}
