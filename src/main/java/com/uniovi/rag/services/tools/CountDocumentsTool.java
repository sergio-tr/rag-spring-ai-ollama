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

public class CountDocumentsTool extends AbstractTool {

    public CountDocumentsTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();

        List<Document> docs = retrieveDocuments(query); // Recupera solo los relevantes
        List<String> fechas = new ArrayList<>();
        long count;

        if (ner != null) {
            count = docs.stream()
                    .filter(doc -> matchesNER(doc, ner))
                    .peek(doc -> fechas.add(extractDate(doc.getContent())))
                    .count();
        } else {
            count = docs.stream()
                    .filter(doc -> isRelevantToQuery(doc, query))
                    .peek(doc -> fechas.add(extractDate(doc.getContent())))
                    .count();
        }

        String respuesta = generarRespuestaConLLM(query, count, fechas);
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

    private boolean isRelevantToQuery(Document doc, String query) {
        // Usa el LLM para decidir si el documento es relevante para la pregunta
        String prompt = """
            Esta es la pregunta del usuario:
            "%s"
            Este es el contenido de un acta:
            "%s"
            ¿El acta responde de forma clara o parcial a la pregunta? Responde solo con 'sí' o 'no'.
            """.formatted(
                query, 
                doc.getContent()
                    .substring(0, Math.min(1000, doc.getContent().length()))
            );
        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();
        return result.contains("sí");
    }

    private String generarRespuestaConLLM(String query, long count, List<String> fechas) {
        String fechasStr = fechas.stream().filter(f -> f != null && !f.isBlank()).distinct().collect(Collectors.joining(", "));
        String prompt = """
            El usuario ha preguntado: "%s"
            Se han encontrado %d documentos relevantes.
            Las fechas de los documentos relevantes son: %s
            Redacta una respuesta breve y clara en español, usando el número y las fechas.
            """.formatted(query, count, fechasStr.isBlank() ? "[sin fechas]" : fechasStr);
        return chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip();
    }
}
