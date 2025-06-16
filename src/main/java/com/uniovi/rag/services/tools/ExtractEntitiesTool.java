package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.uniovi.rag.utils.InfoExtractor.extractDate;
import static com.uniovi.rag.utils.InfoExtractor.extractAttendees;
import static com.uniovi.rag.utils.InfoExtractor.extractAgenda;
import static com.uniovi.rag.utils.InfoExtractor.extractRelevantFragment;

public class ExtractEntitiesTool extends AbstractTool {

    public ExtractEntitiesTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveDocuments(query);
        List<String> resultados = new ArrayList<>();

        if (ner != null) {
            for (Document doc : docs) {
                if (matchesNER(doc, ner)) {
                    String content = doc.getContent();
                    String fecha = extractDate(content);
                    String entidades = extraerEntidadesSolicitadas(content, query);
                    if (!entidades.isBlank()) {
                        resultados.add("Acta del " + fecha + ":\n" + entidades);
                    }
                }
            }
        } else {
            for (Document doc : docs) {
                String content = doc.getContent();
                String fecha = extractDate(content);
                String entidades = extraerEntidadesSolicitadas(content, query);
                if (!entidades.isBlank()) {
                    resultados.add("Acta del " + fecha + ":\n" + entidades);
                }
            }
        }

        String respuesta;
        if (!resultados.isEmpty()) {
            respuesta = generarRespuestaConLLM(query, resultados);
        } else {
            respuesta = "No se encontraron entidades relevantes para la consulta: '" + query + "' en las actas disponibles.";
        }
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

    private String extraerEntidadesSolicitadas(String content, String query) {
        // Usa el LLM para extraer las entidades relevantes para la pregunta
        String asistentes = String.join(", ", extractAttendees(content));
        String agenda = extractAgenda(content);
        String fragmento = extractRelevantFragment(content, query);
        String prompt = """
            Esta es la pregunta del usuario:
            "%s"
            Este es el contenido de un acta:
            "%s"
            Asistentes extraídos: %s
            Agenda extraída: %s
            Fragmento relevante: %s
            Extrae y lista únicamente las entidades (personas, asistentes, cargos, temas, etc.) relevantes para la pregunta. Si no hay ninguna, responde exactamente: [VACÍO]
            """.formatted(query, content.substring(0, Math.min(1000, content.length())), asistentes, agenda != null ? agenda : "", fragmento);
        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip();
        return result.equalsIgnoreCase("[vacío]") ? "" : result;
    }

    private String generarRespuestaConLLM(String query, List<String> resultados) {
        String joined = resultados.stream().distinct().collect(Collectors.joining("\n\n"));
        String prompt = """
            El usuario ha preguntado: "%s"
            Se han encontrado las siguientes entidades relevantes en las actas:
            %s
            Redacta una respuesta breve y clara en español, resumiendo las entidades encontradas y su contexto.
            """.formatted(query, joined);
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip();
    }
}
