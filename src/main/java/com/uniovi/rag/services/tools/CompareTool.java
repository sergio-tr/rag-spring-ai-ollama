package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.uniovi.rag.utils.InfoExtractor.*;

public class CompareTool extends AbstractTool {

    public CompareTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities() != null ? ctx.nerEntities() : new JSONObject();
        JSONObject filtros = ner.optJSONObject("entities") != null
                ? ner.optJSONObject("entities").optJSONObject("filters")
                : new JSONObject();

        List<String> fechas = filtros.optJSONArray("date") != null
                ? filtros.optJSONArray("date").toList().stream().map(Object::toString).toList()
                : List.of();

        List<Document> docs = retrieveAllDocuments(query);
        if (docs.isEmpty()) {
            throw new RuntimeException("No se encontraron actas relevantes.");
        }

        Map<String, MinuteInfo> resumen = new LinkedHashMap<>();
        for (Document doc : docs) {
            String content = doc.getContent();
            String fecha = extractDate(content);

            if (!fechas.isEmpty() && fechas.stream().noneMatch(fecha::contains)) continue;

            resumen.put(fecha, new MinuteInfo(
                    fecha,
                    extractAttendeeCount(content),
                    calculateDuration(content),
                    countProposals(content),
                    countAgendaItems(content),
                    countQuestions(content),
                    extractLiteralField("place", content)
            ));
        }

        if (resumen.isEmpty()) {
            throw new RuntimeException("No se pudieron generar datos comparables.");
        }

        String tipoComparacion = inferComparisonTarget(query);
        if (tipoComparacion.equals("desconocido")) {
            return ToolResult.from("No se ha podido determinar claramente qué comparar. Estos son los valores disponibles:\n" +
                    resumen.values().stream().map(MinuteInfo::toString).reduce("", (a, b) -> a + b + "\n"), getClass());
        }

        String label = getComparisonLabel(tipoComparacion);
        return ToolResult.from(compare(resumen, tipoComparacion, label), getClass());
    }

    private String inferComparisonTarget(String query) {
        String prompt = """
                Dada la siguiente pregunta de usuario:
                
                "%s"
                
                Indica claramente qué tipo de valor quiere comparar el usuario.
                Las opciones válidas son:
                - asistentes
                - duracion
                - propuestas
                - puntos_orden_dia
                - ruegos
                - lugar
                - fecha
                
                Si no lo puedes inferir claramente, responde únicamente: desconocido
                """.formatted(query);

        String result = chatClient.prompt().user(prompt).call().content().strip().toLowerCase();
        return switch (result) {
            case "asistentes", "duracion", "propuestas", "puntos_orden_dia", "ruegos", "place", "date" -> result;
            default -> "desconocido";
        };
    }

    private String getComparisonLabel(String tipo) {
        return switch (tipo) {
            case "asistentes" -> "número de asistentes";
            case "duracion" -> "duración de la reunión";
            case "propuestas" -> "propuestas tratadas";
            case "puntos_orden_dia" -> "temas del orden del día";
            case "ruegos" -> "intervenciones finales";
            case "place" -> "lugar de celebración";
            case "date" -> "date";
            default -> tipo;
        };
    }
}
