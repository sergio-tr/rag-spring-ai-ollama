package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.*;

public class MetadataCompareTool extends AbstractMetadataTool {

    public MetadataCompareTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject nerEntities = ctx.nerEntities();
        String[] keywords = extractKeywordsFromQuery(query).split("\\s+");

        List<Document> docs = retrieveAllDocuments(query);
        if (docs.isEmpty()) {
            return ToolResult.from("No se encontraron actas relevantes para la comparación.", getClass());
        }

        String fieldToCompare = inferComparisonField(nerEntities, keywords);
        if (fieldToCompare == null) {
            return ToolResult.from("No se ha podido determinar qué comparar (por ejemplo: número de asistentes, duración...).", getClass());
        }

        // Agrupar documentos por acta
        Map<String, Map<String, Object>> uniqueMinutes = new HashMap<>();
        for (Document doc : docs) {
            Map<String, Object> meta = doc.getMetadata();
            String minuteId = (String) meta.get("id"); // Usamos la key estandarizada
            if (minuteId != null && !uniqueMinutes.containsKey(minuteId)) {
                uniqueMinutes.put(minuteId, meta);
            }
        }

        Map<String, Integer> comparables = new HashMap<>();
        for (Map<String, Object> meta : uniqueMinutes.values()) {
            String label = buildLabel(meta, nerEntities);
            Integer value = extractNumericField(meta, fieldToCompare);
            if (label != null && value != null) {
                comparables.put(label, value);
            }
        }

        if (comparables.isEmpty()) {
            return ToolResult.from("No se encontraron datos numéricos para comparar el campo: " + fieldToCompare, getClass());
        }

        StringBuilder result = new StringBuilder("Comparación por *" + fieldToCompare + "*:\n");
        comparables.entrySet().stream()
                .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
                .forEach(e -> result.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n"));

        return ToolResult.from(result.toString(), getClass());
    }

    private String inferComparisonField(JSONObject nerEntities, String[] keywords) {
        Set<String> keySet = Set.of(keywords);

        if (keySet.contains("attendees") || keySet.contains("numberOfAttendees")) return "numberOfAttendees";
        if (keySet.contains("duration") || keySet.contains("startTime") || keySet.contains("endTime"))
            return "duration";

        if (nerEntities != null) {
            JSONObject entities = nerEntities.optJSONObject("entities");
            if (entities != null) {
                String answerType = entities.optString("answer_type", "").toLowerCase();
                return switch (answerType) {
                    case "attendees" -> "numberOfAttendees";
                    case "duration" -> "duration";
                    default -> null;
                };
            }
        }

        return null;
    }

    private Integer extractNumericField(Map<String, Object> meta, String field) {
        try {
            return switch (field) {
                case "numberOfAttendees" -> (Integer) meta.get("numberOfAttendees");
                case "duration" -> calculateDuration(meta);
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private int calculateDuration(Map<String, Object> meta) {
        try {
            String start = (String) meta.get("startTime");
            String end = (String) meta.get("endTime");
            if (start != null && end != null) {
                String[] s = start.split(":");
                String[] e = end.split(":");
                int startMin = Integer.parseInt(s[0]) * 60 + Integer.parseInt(s[1]);
                int endMin = Integer.parseInt(e[0]) * 60 + Integer.parseInt(e[1]);
                return endMin - startMin;
            }
        } catch (Exception ignored) {
        }
        return 0;
    }

    private String buildLabel(Map<String, Object> meta, JSONObject nerEntities) {
        List<String> keys = new ArrayList<>();
        if (nerEntities != null) {
            JSONObject filters = nerEntities.optJSONObject("entities").optJSONObject("filters");
            if (filters != null) {
                if (filters.has("date")) keys.add("date");
                if (filters.has("place")) keys.add("place");
            }
        }

        if (keys.isEmpty()) keys.add("date"); // fallback

        List<String> values = new ArrayList<>();
        for (String key : keys) {
            Object val = meta.get(key);
            if (val != null) values.add(val.toString());
        }
        return values.isEmpty() ? null : String.join(" - ", values);
    }
}
