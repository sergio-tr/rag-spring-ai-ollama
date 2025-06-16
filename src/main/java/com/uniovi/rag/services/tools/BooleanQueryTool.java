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
import static com.uniovi.rag.utils.InfoExtractor.extractRelevantFragment;

public class BooleanQueryTool extends AbstractTool {

    public BooleanQueryTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveDocuments(query);
        List<String> evidencias = new ArrayList<>();
        boolean found = false;

        if (ner != null) {
            // Usar NER para filtrar documentos y verificar la afirmación
            for (Document doc : docs) {
                if (matchesNER(doc, ner)) {
                    String fragment = extractRelevantFragment(doc.getContent(), query);
                    if (fragmentConfirmsClaim(query, fragment)) {
                        String fecha = extractDate(doc.getContent());
                        evidencias.add("Sí, se encontró evidencia en la reunión del " + fecha + ":\n" + fragment);
                        found = true;
                    }
                }
            }
        } else {
            // Baseline: para cada documento, pregunta al LLM si es relevante
            for (Document doc : docs) {
                String fragment = extractRelevantFragment(doc.getContent(), query);
                if (fragmentConfirmsClaim(query, fragment)) {
                    String fecha = extractDate(doc.getContent());
                    evidencias.add("Sí, se encontró evidencia en la reunión del " + fecha + ":\n" + fragment);
                    found = true;
                }
            }
        }

        String respuesta;
        if (found) {
            respuesta = generarRespuestaConLLM(query, evidencias);
        } else {
            respuesta = "No se encontró evidencia de que '" + query + "' en las actas disponibles.";
        }
        return ToolResult.from(respuesta, getClass());
    }

    private boolean matchesNER(Document doc, JSONObject ner) {
        // Extrae los campos de NER alineados con Minute
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
                    if (!anyMatch) return false; // Si hay valores y ninguno coincide, descarta
                }
            }
        }
        return true;
    }

    private boolean fragmentConfirmsClaim(String query, String fragment) {
        String prompt = """
            Esta es la afirmación a verificar:
            "%s"
            Y este es un fragmento del acta:
            "%s"
            ¿Este fragmento confirma la afirmación? Responde solo con 'sí' o 'no'.
            """.formatted(query, fragment);
        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();
        return result.contains("sí");
    }

    private String generarRespuestaConLLM(String query, List<String> evidencias) {
        String joined = evidencias.stream().distinct().collect(Collectors.joining("\n\n"));
        String prompt = """
            El usuario ha preguntado: "%s"
            Se ha encontrado la siguiente evidencia en las actas:
            %s
            Redacta una respuesta breve y clara en español, usando la evidencia encontrada.
            """.formatted(query, joined);
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip();
    }
}
