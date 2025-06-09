package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;

import static com.uniovi.rag.utils.InfoExtractor.*;

public class GetFieldTool extends AbstractTool {

    public GetFieldTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        JSONObject entities = ner.optJSONObject("entities");
        JSONObject filtros = entities != null ? entities.optJSONObject("filters") : new JSONObject();

        List<String> fechas = filtros.optJSONArray("date") != null ?
                filtros.optJSONArray("date").toList().stream().map(Object::toString).toList() : List.of();

        List<Document> documents = retrieveAllDocuments(query);
        if (documents.isEmpty()) {
            throw new RuntimeException("No se encontraron documentos relevantes.");
        }

        for (Document doc : documents) {
            String content = doc.getContent();
            String docFecha = extractDate(content);

            if (!fechas.isEmpty() && fechas.stream().noneMatch(docFecha::contains)) continue;

            String field = extractLiteralFieldByIntent(query, entities, content);
            if (field != null) {
                return ToolResult.from(field, getClass());
            }
        }

        throw new RuntimeException("No se encontró un valor literal relacionado con la consulta.");
    }

    private String extractLiteralFieldByIntent(String query, JSONObject entities, String content) {
        String lower = query.toLowerCase();

        if (entities != null) {
            if (entities.has("date")) return extractDate(content);
            if (entities.has("hora de inicio")) return extractTime(content, "start");
            if (entities.has("hora de finalización")) return extractTime(content, "end");
            if (entities.has("place")) return extractLiteralField("place", content);
            if (entities.has("presidente")) return extractLiteralField("presidente", content);
            if (entities.has("secretario")) return extractLiteralField("secretario", content);
        }

        // Fallback: uso de LLM para detectar el campo literal
        String detectedIntent = classifyLiteralIntentWithLLM(query);
        return switch (detectedIntent) {
            case "date" -> extractDate(content);
            case "startTime" -> extractTime(content, "start");
            case "endTime" -> extractTime(content, "end");
            case "place" -> extractLiteralField("place", content);
            case "presidente" -> extractLiteralField("presidente", content);
            case "secretario" -> extractLiteralField("secretario", content);
            case "asistentes_lista" -> String.join(", ", extractAttendees(content));
            case "asistentes_numero" -> String.valueOf(extractAttendeeCount(content));
            case "orden_dia" -> extractAgenda(content);
            default -> null;
        };
    }

    private String classifyLiteralIntentWithLLM(String query) {
        String prompt = """
                Dada la siguiente pregunta de un usuario:
                
                "%s"
                
                Determina cuál es el campo literal que desea consultar. Elige uno de los siguientes:
                - fecha
                - lugar
                - hora_inicio
                - hora_fin
                - presidente
                - secretario
                - asistentes_lista
                - asistentes_numero
                - orden_dia
                
                Si no puedes determinarlo, responde exactamente: desconocido
                """.formatted(query);

        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();
    }
}
