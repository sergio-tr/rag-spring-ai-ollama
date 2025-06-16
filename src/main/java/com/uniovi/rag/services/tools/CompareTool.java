package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.stream.Collectors;

import static com.uniovi.rag.utils.InfoExtractor.*;

public class CompareTool extends AbstractTool {

    public CompareTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveDocuments(query);
        if (docs.isEmpty()) {
            throw new RuntimeException("No se encontraron actas relevantes.");
        }

        Map<String, MinuteInfo> resumen = new LinkedHashMap<>();
        if (ner != null) {
            // Usar NER para filtrar y agrupar/comparar
            for (Document doc : docs) {
                if (matchesNER(doc, ner)) {
                    String content = doc.getContent();
                    String fecha = extractDate(content);
                    resumen.put(fecha, buildMinuteInfo(content, fecha));
                }
            }
        } else {
            // Baseline: agrupar y comparar por heurística (por ejemplo, por mes, asistentes, propuestas...)
            for (Document doc : docs) {
                String content = doc.getContent();
                String fecha = extractDate(content);
                resumen.put(fecha, buildMinuteInfo(content, fecha));
            }
        }

        if (resumen.isEmpty()) {
            throw new RuntimeException("No se pudieron generar datos comparables.");
        }

        String tipoComparacion = inferComparisonTarget(query, resumen);
        if (tipoComparacion.equals("desconocido")) {
            return ToolResult.from("No se ha podido determinar claramente qué comparar. Estos son los valores disponibles:\n" +
                    resumen.values().stream().map(MinuteInfo::toString).reduce("", (a, b) -> a + b + "\n"), getClass());
        }

        String comparacion = compararValores(resumen, tipoComparacion, query);
        String respuesta = generarRespuestaConLLM(query, comparacion);
        return ToolResult.from(respuesta, getClass());
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

    private MinuteInfo buildMinuteInfo(String content, String fecha) {
        return new MinuteInfo(
                fecha,
                extractAttendeeCount(content),
                calculateDuration(content),
                countProposals(content),
                countAgendaItems(content),
                countQuestions(content),
                extractLiteralField("place", content)
        );
    }

    private String inferComparisonTarget(String query, Map<String, MinuteInfo> resumen) {
        // Usa el LLM para inferir el tipo de comparación (asistentes, duración, propuestas, etc.)
        String prompt = """
            Dada la siguiente pregunta de usuario:
            "%s"
            Estos son los datos disponibles:
            %s
            Indica claramente qué tipo de valor quiere comparar el usuario. Las opciones válidas son:
            - asistentes
            - duracion
            - propuestas
            - puntos_orden_dia
            - ruegos
            - lugar
            - fecha
            Si no lo puedes inferir claramente, responde únicamente: desconocido
            """.formatted(query, resumen.values().stream().map(MinuteInfo::toString).collect(Collectors.joining("\n")));
        String result = chatClient.prompt().user(prompt).call().content().strip().toLowerCase();
        return switch (result) {
            case "asistentes", "duracion", "propuestas", "puntos_orden_dia", "ruegos", "place", "fecha" -> result;
            default -> "desconocido";
        };
    }

    private String compararValores(Map<String, MinuteInfo> resumen, String tipoComparacion, String query) {
        // Agrupa por mes si la pregunta lo sugiere
        if (query.toLowerCase().contains("mes") || query.toLowerCase().contains("febrero") || query.toLowerCase().contains("agosto") || query.toLowerCase().contains("abril")) {
            Map<String, List<MinuteInfo>> porMes = new HashMap<>();
            for (MinuteInfo info : resumen.values()) {
                String mes = extraerMes(info.date());
                porMes.computeIfAbsent(mes, k -> new ArrayList<>()).add(info);
            }
            // Sumariza por mes
            Map<String, Integer> valoresPorMes = new HashMap<>();
            for (Map.Entry<String, List<MinuteInfo>> entry : porMes.entrySet()) {
                int valor = entry.getValue().stream().mapToInt(info -> getValue(info, tipoComparacion)).sum();
                valoresPorMes.put(entry.getKey(), valor);
            }
            // Genera texto comparativo
            return valoresPorMes.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> "En " + e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining(". "));
        } else {
            // Comparación directa entre actas
            return resumen.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> "En la reunión del " + e.getKey() + ": " + getValue(e.getValue(), tipoComparacion))
                    .collect(Collectors.joining(". "));
        }
    }

    private String extraerMes(String fecha) {
        // Extrae el mes de una fecha tipo "25 de febrero de 2025"
        try {
            String[] parts = fecha.split(" de ");
            if (parts.length >= 2) {
                return parts[1].toLowerCase();
            }
        } catch (Exception ignored) {}
        return fecha;
    }

    private String generarRespuestaConLLM(String query, String comparacion) {
        String prompt = """
            El usuario ha preguntado: "%s"
            Esta es la comparación obtenida:
            %s
            Redacta una respuesta breve y clara en español, comparando los valores y explicando cuál es mayor o si hay empate.
            """.formatted(query, comparacion);
        return chatClient.prompt().user(prompt).call().content().strip();
    }
}
