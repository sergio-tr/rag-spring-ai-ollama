package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.*;

import com.uniovi.rag.model.Minute;

public class MetadataDecisionExtractionTool extends AbstractMetadataTool {

    public MetadataDecisionExtractionTool(ChatClient chatClient, ContextRetriever retriever) {
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

        // Step 2: Filter minutes and extract relevant decisions
        List<String> relevantDecisions = new ArrayList<>();
        for (Minute minute : minutes) {
            boolean matches = false;
            if (ner != null) {
                matches = matchesMinuteWithNER(minute, ner);
            } else {
                matches = isRelevantToQuery(query, minute);
            }
            if (matches && minute.decisions() != null) {
                for (String decision : minute.decisions()) {
                    if (isDecisionRelevantToQuery(decision, query)) {
                        relevantDecisions.add(buildDecisionExplanation(minute, decision));
                    }
                }
            }
        }

        if (relevantDecisions.isEmpty()) {
            return ToolResult.from(generateNoDataMessage(query), getClass());
        }

        // Step 3: Generate answer with LLM
        String answer = generateDecisionAnswerWithLLM(query, relevantDecisions);
        return ToolResult.from(answer, getClass());
    }

    private boolean isRelevantToQuery(String query, Minute minute) {
        // Check if the query matches any metadata field (topics, decisions, summary, agenda, etc.)
        String q = query.toLowerCase();
        return (minute.topics() != null && minute.topics().stream().anyMatch(t -> q.contains(t.toLowerCase()) || t.toLowerCase().contains(q)))
        || (minute.decisions() != null && minute.decisions().stream().anyMatch(d -> q.contains(d.toLowerCase()) || d.toLowerCase().contains(q)))
        || (minute.summary() != null && minute.summary().toLowerCase().contains(q))
        || (minute.agenda() != null && minute.agenda().values().stream().anyMatch(a -> q.contains(a.toLowerCase()) || a.toLowerCase().contains(q)));
    }

    private boolean isDecisionRelevantToQuery(String decision, String query) {
        // Use LLM to decide if the decision is relevant to the query
        String prompt = """
        Given the following user query (in any language):
        \"%s\"
        And the following decision from a meeting minute:
        \"%s\"
        Does this decision answer or relate to the query? Answer only YES or NO (in the language of the query).
        """.formatted(query, decision);
        String result = chatClient.prompt().user(prompt).call().content().strip().toLowerCase();
        return result.startsWith("yes") || result.startsWith("sí");
    }

    private String buildDecisionExplanation(Minute minute, String decision) {
        StringBuilder sb = new StringBuilder();
        if (minute.date() != null) sb.append("Acta del ").append(minute.date());
        if (minute.place() != null) sb.append(" (Lugar: ").append(minute.place()).append(")");
        sb.append(": ").append(decision);
        return sb.toString();
    }

    private String generateDecisionAnswerWithLLM(String query, List<String> decisions) {
        String joined = decisions.stream().distinct().limit(10).collect(java.util.stream.Collectors.joining("\n"));
        String prompt = """
        Given the following user query (in any language):
        \"%s\"
        The following are relevant decisions from meeting minutes:
        %s
        Write a clear, concise answer in the same language as the query, summarizing the relevant decisions and their context. Do not invent information.
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
        Write a short message indicating that no relevant decisions were found for the query, in the same language as the query.
        """.formatted(query);
        return chatClient.prompt().user(prompt).call().content().strip();
    }
}
