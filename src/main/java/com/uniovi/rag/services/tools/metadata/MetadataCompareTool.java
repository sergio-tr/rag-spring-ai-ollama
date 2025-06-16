package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import com.uniovi.rag.model.Minute;

import java.util.*;

public class MetadataCompareTool extends AbstractMetadataTool {

    public MetadataCompareTool(ChatClient chatClient, ContextRetriever retriever) {
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

        // Step 2: Infer which field to compare (attendees, duration, etc.)
        String fieldToCompare = inferComparisonField(query, ner, minutes);
        if (fieldToCompare == null) {
            return ToolResult.from(generateUnknownFieldMessage(query), getClass());
        }

        // Step 3: Filter and label minutes for comparison
        Map<String, Integer> comparables = new LinkedHashMap<>();
        for (Minute minute : minutes) {
            if (ner != null && !matchesMinuteWithNER(minute, ner)) continue;
            String label = buildLabel(minute, ner);
            Integer value = extractNumericField(minute, fieldToCompare);
            if (label != null && value != null) {
                comparables.put(label, value);
            }
        }
        if (comparables.isEmpty()) {
            return ToolResult.from(generateNoDataMessage(fieldToCompare, query), getClass());
        }

        // Step 4: Generate a comparative answer using the LLM in the query's language
        String answer = generateComparisonAnswerWithLLM(query, fieldToCompare, comparables);
        return ToolResult.from(answer, getClass());
    }

    private String inferComparisonField(String query, JSONObject ner, List<Minute> minutes) {
        // Use LLM to infer the field to compare, based on the query and available fields
        StringBuilder availableFields = new StringBuilder();
        availableFields.append("Available fields for comparison:\n");
        availableFields.append("- numberOfAttendees\n- duration\n- topics\n- decisions\n");
        String prompt = """
        Given the following user query (in any language):
        \"%s\"
        %s
        Which field does the user want to compare? 
        Respond with one of: numberOfAttendees, duration, topics, decisions. If unclear, respond only: unknown
        """.formatted(query, availableFields);
        String result = chatClient.prompt().user(prompt).call().content().strip().toLowerCase();
        return switch (result) {
            case "numberofattendees" -> "numberOfAttendees";
            case "duration" -> "duration";
            case "topics" -> "topics";
            case "decisions" -> "decisions";
            default -> null;
        };
    }

    private Integer extractNumericField(Minute minute, String field) {
        return switch (field) {
            case "numberOfAttendees" -> minute.numberOfAttendees();
            case "duration" -> calculateDurationFromMinute(minute);
            default -> null;
        };
    }

    private String buildLabel(Minute minute, JSONObject ner) {
        // Prefer date + place for clarity, fallback to date
        StringBuilder label = new StringBuilder();
        if (minute.date() != null) label.append(minute.date());
        if (minute.place() != null) label.append(" - ").append(minute.place());
        return label.length() > 0 ? label.toString() : minute.id();
    }

    private String generateComparisonAnswerWithLLM(String query, String field, Map<String, Integer> comparables) {
        StringBuilder comparison = new StringBuilder();
        comparables.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEach(e -> comparison.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n"));
        String prompt = """
        Given the following user query (in any language):
        \"%s\"
        This is the comparison for field '%s':
        %s
        Write a clear, concise answer in the same language as the query, comparing the values and explaining which is higher, lower, or if there is a tie.
        """.formatted(query, field, comparison);
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

    private String generateUnknownFieldMessage(String query) {
        String prompt = """
        Given the following user query (in any language):
        \"%s\"
        Write a short message indicating that it was not possible to determine what to compare, in the same language as the query.
        """.formatted(query);
        return chatClient.prompt().user(prompt).call().content().strip();
    }

    private String generateNoDataMessage(String field, String query) {
        String prompt = """
        Given the following user query (in any language):
        \"%s\"
        Write a short message indicating that no data was found for the field '%s', in the same language as the query.
        """.formatted(query, field);
        return chatClient.prompt().user(prompt).call().content().strip();
    }
}
