package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;

import static com.uniovi.rag.utils.InfoExtractor.*;

public class ExtractEntitiesTool extends AbstractTool {

    public ExtractEntitiesTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query().toLowerCase();
        JSONObject ner = ctx.nerEntities();
        JSONObject entidades = ner.optJSONObject("entities");
        JSONObject filtros = entidades != null ? entidades.optJSONObject("filters") : new JSONObject();

        List<String> fechas = filtros.optJSONArray("date") != null
                ? filtros.optJSONArray("date").toList().stream().map(Object::toString).toList()
                : List.of();

        String intent = classifyEntityListIntentWithLLM(query, entidades);

        if (intent.equals("desconocido")) {
            throw new RuntimeException("No se pudo inferir con claridad qué entidad deseas listar.");
        }

        List<Document> docs = retrieveAllDocuments(query);
        if (docs.isEmpty()) {
            throw new RuntimeException("No se encontraron documentos relevantes.");
        }

        StringBuilder output = new StringBuilder();
        boolean anyFound = false;

        for (Document doc : docs) {
            String content = doc.getContent();
            String fecha = extractDate(content);

            if (!fechas.isEmpty() && fechas.stream().noneMatch(fecha::contains)) continue;

            switch (intent) {
                case "asistentes" -> {
                    List<String> asistentes = extractAttendees(content);
                    if (!asistentes.isEmpty()) {
                        output.append("Reunión del ").append(fecha).append(":\n")
                                .append(asistentes.size()).append(" asistentes:\n")
                                .append("- ").append(String.join("\n- ", asistentes)).append("\n\n");
                        anyFound = true;
                    }
                }
                case "orden_dia" -> {
                    String agenda = extractAgenda(content);
                    if (agenda != null) {
                        output.append("Orden del día - ").append(fecha).append(":\n")
                                .append(agenda).append("\n\n");
                        anyFound = true;
                    }
                }
                case "secciones" -> {
                    output.append("Las secciones estándar del acta son:\n")
                            .append("- Título\n- Fecha\n- Lugar\n- Hora de inicio\n- Hora de finalización\n")
                            .append("- Asistentes\n- Orden del Día\n- Ruegos y Preguntas\n- Hora de cierre\n\n");
                    anyFound = true;
                }
                case "quorum" -> {
                    String fragmento = extractRelevantFragment(content, "quórum");
                    output.append("Fragmento sobre el quórum - ").append(fecha).append(":\n")
                            .append(fragmento).append("\n\n");
                    anyFound = true;
                }
                case "acuerdos" -> {
                    String fragmento = extractRelevantFragment(content, "acuerdo");
                    output.append("Fragmento sobre acuerdos - ").append(fecha).append(":\n")
                            .append(fragmento).append("\n\n");
                    anyFound = true;
                }
                case "resoluciones" -> {
                    String fragmento = extractRelevantFragment(content, "resolución");
                    output.append("Fragmento sobre resoluciones - ").append(fecha).append(":\n")
                            .append(fragmento).append("\n\n");
                    anyFound = true;
                }
                case "intervenciones" -> {
                    String fragmento = extractRelevantFragment(content, "ruegos y preguntas");
                    output.append("Ruegos y Preguntas - ").append(fecha).append(":\n")
                            .append(fragmento).append("\n\n");
                    anyFound = true;
                }
            }
        }

        if (!anyFound) {
            throw new RuntimeException("No se encontró información relevante para la entidad solicitada.");
        }

        return ToolResult.from(output.toString().strip(), getClass());
    }

    private String classifyEntityListIntentWithLLM(String query, JSONObject entidades) {
        String prompt = """
                A partir de esta pregunta del usuario:
                "%s"
                
                Y estas entidades detectadas (pueden estar vacías o incompletas):
                %s
                
                Clasifica la intención de qué entidad quiere listar. Solo responde con una de las siguientes opciones:
                - asistentes
                - orden_dia
                - secciones
                - quorum
                - acuerdos
                - resoluciones
                - intervenciones
                
                Si no puedes determinarlo claramente, responde solo con: desconocido.
                """.formatted(query, entidades != null ? entidades.toString() : "{}");

        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();

        return switch (result) {
            case "asistentes", "orden_dia", "secciones", "quorum", "acuerdos", "resoluciones", "intervenciones" ->
                    result;
            default -> "desconocido";
        };
    }
}
