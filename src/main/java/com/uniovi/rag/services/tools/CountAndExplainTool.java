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

public class CountAndExplainTool extends AbstractTool {

    public CountAndExplainTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveDocuments(query);
        List<String> explicaciones = new ArrayList<>();
        int count = 0;

        if (ner != null) {
            for (Document doc : docs) {
                if (matchesNER(doc, ner)) {
                    String content = doc.getContent();
                    String fecha = extractDate(content);
                    String fragmento = extractRelevantFragment(content, query);
                    explicaciones.add("Acta del " + fecha + ":\n" + fragmento);
                    count++;
                }
            }
        } else {
            for (Document doc : docs) {
                String content = doc.getContent();
                String fecha = extractDate(content);
                String fragmento = extractRelevantFragment(content, query);
                if (isRelevantToQuery(fragmento, query)) {
                    explicaciones.add("Acta del " + fecha + ":\n" + fragmento);
                    count++;
                }
            }
        }

        String respuesta;
        if (count > 0) {
            respuesta = generarRespuestaConLLM(query, count, explicaciones);
        } else {
            respuesta = "No se encontró información relevante para la consulta: '" + query + "' en las actas disponibles.";
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

    private boolean isRelevantToQuery(String fragment, String query) {
        // Usa el LLM para decidir si el fragmento es relevante para la pregunta
        String prompt = """
            Esta es la pregunta del usuario:
            "%s"
            Este es un fragmento de un acta:
            "%s"
            ¿El fragmento responde de forma clara o parcial a la pregunta? Responde solo con 'sí' o 'no'.
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

    private String generarRespuestaConLLM(String query, int count, List<String> explicaciones) {
        String joined = explicaciones.stream().distinct().collect(Collectors.joining("\n\n"));
        String prompt = """
            El usuario ha preguntado: "%s"
            Se han encontrado %d actas relevantes. A continuación se muestra el contexto encontrado:
            %s
            Redacta una respuesta breve y clara en español, indicando el número de actas y resumiendo el contexto encontrado.
            """.formatted(query, count, joined);
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip();
    }
}
