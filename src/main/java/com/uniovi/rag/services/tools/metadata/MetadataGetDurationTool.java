package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.uniovi.rag.utils.InfoExtractor.extractTime;

public class MetadataGetDurationTool extends AbstractMetadataTool {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    public MetadataGetDurationTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject nerEntities = ctx.nerEntities();

        List<Document> docs = retrieveAllDocuments(query);
        if (docs.isEmpty()) {
            throw new RuntimeException("No se encontraron documentos relacionados con la consulta.");
        }

        // Agrupar por acta
        Map<String, List<Document>> byMinute = docs.stream()
                .filter(d -> d.getMetadata().containsKey("id"))
                .collect(Collectors.groupingBy(d -> (String) d.getMetadata().get("id")));

        List<String> resultados = new ArrayList<>();

        for (List<Document> grupo : byMinute.values()) {
            Document doc = grupo.getFirst(); // usar cualquier chunk
            Map<String, Object> meta = doc.getMetadata();

            boolean match = matchesBooleanCondition(doc, extractKeywordsFromQuery(query).split("\\s+"), nerEntities);
            if (!match) continue;

            String startStr = getAsString(meta, "startTime");
            String endStr = getAsString(meta, "endTime");

            if (startStr == null || endStr == null) {
                // fallback a contenido si es posible
                startStr = grupo.stream().map(d -> extractTime(d.getContent(), "start")).filter(Objects::nonNull).findFirst().orElse(null);
                endStr = grupo.stream().map(d -> extractTime(d.getContent(), "end")).filter(Objects::nonNull).findFirst().orElse(null);
            }

            if (startStr != null && endStr != null) {
                try {
                    LocalTime start = LocalTime.parse(startStr, TIME_FORMAT);
                    LocalTime end = LocalTime.parse(endStr, TIME_FORMAT);
                    long mins = java.time.Duration.between(start, end).toMinutes();
                    long h = mins / 60;
                    long m = mins % 60;
                    String fecha = getAsString(meta, "date");
                    resultados.add((fecha != null ? "Acta del " + fecha + ": " : "") + h + "h" + (m > 0 ? " " + m + "min" : ""));
                } catch (DateTimeParseException ignored) {
                }
            }
        }

        if (resultados.isEmpty()) {
            return ToolResult.from("No se pudo determinar la duración de ninguna reunión relacionada con la consulta.", getClass());
        }

        if (resultados.size() == 1) {
            return ToolResult.from("Duración estimada: " + resultados.getFirst() + ".", getClass());
        }

        return ToolResult.from("Se encontraron " + resultados.size() + " reuniones relevantes:\n\n" + String.join("\n", resultados), getClass());
    }

    private String getAsString(Map<String, Object> metadata, String key) {
        Object val = metadata.get(key);
        return val instanceof String ? ((String) val).trim() : null;
    }
}
