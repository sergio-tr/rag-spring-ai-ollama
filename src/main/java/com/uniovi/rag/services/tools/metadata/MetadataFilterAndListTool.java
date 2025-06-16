package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.*;

import com.uniovi.rag.model.Minute;

public class MetadataFilterAndListTool extends AbstractMetadataTool {

    public MetadataFilterAndListTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveAllDocuments(query);
        if (docs.isEmpty()) {
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 1: Extract Minute objects from metadata
        List<Minute> minutes = new ArrayList<>();
        for (Document doc : docs) {
            Minute minute = getMinuteFromMetadata(doc);
            if (minute != null) minutes.add(minute);
        }
        if (minutes.isEmpty()) {
            return ToolResult.from(generateNotFoundMessage(query), getClass());
        }

        // Step 2: Filter minutes by NER or query relevance in metadata
        List<String> results = new ArrayList<>();
        for (Minute minute : minutes) {
            boolean matches = false;
            if (ner != null) {
                matches = matchesMinuteWithNER(minute, ner);
            } else {
                matches = isRelevantToQuery(query, minute);
            }
            if (matches) {
                results.add(buildSummaryExplanation(minute, query));
            }
        }

        if (results.isEmpty()) {
            return ToolResult.from(generateNoDataMessage(query), getClass());
        }

        // Step 3: Generate answer with LLM
        String answer = generateFilterAndListAnswerWithLLM(query, results);
        return ToolResult.from(answer, getClass());
    }

    private boolean isRelevantToQuery(String query, Minute minute) {
        return semanticallyMatchesMinute(minute, query);
    }

    private String buildSummaryExplanation(Minute minute, String query) {
        String prompt = """
                Given the following user query (in any language):
                "%s"
                
                And the following meeting metadata:
                Date: %s
                Place: %s
                Topics: %s
                Decisions: %s
                Summary: %s
                Agenda: %s
                
                Write a brief summary (2-3 sentences) in the same language as the query,
                focusing on the aspects most relevant to the query.
                """.formatted(
                    query,
                    minute.date() != null ? minute.date() : "",
                    minute.place() != null ? minute.place() : "",
                    minute.topics() != null ? String.join(", ", minute.topics()) : "",
                    minute.decisions() != null ? String.join(", ", minute.decisions()) : "",
                    minute.summary() != null ? minute.summary() : "",
                    minute.agenda() != null ? minute.agenda().toString() : ""
                );
        
        return chatClient.prompt().user(prompt).call().content().strip();
    }

    private String generateFilterAndListAnswerWithLLM(String query, List<String> results) {
        String joined = String.join("\n\n", results);
        String prompt = """
        Given the following user query (in any language):
        \"%s\"
        The following minutes matched the filters and their relevant content is:
        %s
        Write a brief and clear answer in the same language as the query, 
        listing the minutes and summarizing the relevant content for each.
        """.formatted(query, joined);
        return chatClient.prompt().user(prompt).call().content().strip();
    }

    private String generateNotFoundMessage(String query) {
        String prompt = """
        Given the following user query (in any language):
        \"%s\"
        Write a short message indicating that no relevant meeting minutes were found, in the same language as the query.
        """.formatted(query);
        return chatClient.prompt().user(prompt).call().content().strip();
    }

    private String generateNoDataMessage(String query) {
        String prompt = """
        Given the following user query (in any language):
        \"%s\"
        Write a short message indicating that no relevant minutes were found for the query, in the same language as the query.
        """.formatted(query);
        return chatClient.prompt().user(prompt).call().content().strip();
    }
}
