package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.uniovi.rag.utils.InfoExtractor.extractDate;

public class DecisionExtractionTool extends AbstractTool {

    public DecisionExtractionTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveDocuments(query);
        List<String> decisiones = new ArrayList<>();

        if (ner != null) {
            for (Document doc : docs) {
                if (matchesNER(doc, ner)) {
                    String content = doc.getContent();
                    String fecha = extractDate(content);
                    List<String> fragmentos = extraerDecisiones(content, query);
                    for (String frag : fragmentos) {
                        decisiones.add("Acta del " + fecha + ":\n" + frag);
                    }
                }
            }
        } else {
            for (Document doc : docs) {
                String content = doc.getContent();
                String fecha = extractDate(content);
                List<String> fragmentos = extraerDecisiones(content, query);
                for (String frag : fragmentos) {
                    if (isDecisionRelevantToQuery(frag, query)) {
                        decisiones.add("Acta del " + fecha + ":\n" + frag);
                    }
                }
            }
        }

        String respuesta;
        if (!decisiones.isEmpty()) {
            respuesta = generarRespuestaConLLM(query, decisiones);
        } else {
            respuesta = "No se encontraron decisiones relevantes para la consulta: '" + query + "' en las actas disponibles.";
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

    private List<String> extraerDecisiones(String content, String query) {
        // Divide el contenido en fragmentos y usa el LLM para decidir si es una decisión relevante
        return Stream.of(content.split("(?<=[.:?])\\s*([\\n\\r])+"))
                .map(String::trim)
                .filter(p -> !p.isBlank())
                .filter(p -> isDecisionRelevantToQuery(p, query))
                .limit(10)
                .collect(Collectors.toList());
    }

    private boolean isDecisionRelevantToQuery(String fragment, String query) {
        String prompt = """
            Esta es la consulta del usuario:
            "%s"
            Este es un fragmento de un acta:
            "%s"
            ¿Este fragmento contiene una decisión relevante para la consulta? Responde solo con 'sí' o 'no'.
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

    private String generarRespuestaConLLM(String query, List<String> decisiones) {
        String joined = decisiones.stream().distinct().collect(Collectors.joining("\n\n"));
        String prompt = """
            El usuario ha preguntado: "%s"
            Se han encontrado las siguientes decisiones relevantes en las actas:
            %s
            Redacta una respuesta breve y clara en español, resumiendo las decisiones encontradas y su contexto.
            """.formatted(query, joined);
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip();
    }
}
