package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;

import static com.uniovi.rag.utils.InfoExtractor.containsAnyKeyword;
import static com.uniovi.rag.utils.InfoExtractor.containsRelevantPhrase;

public class CountDocumentsTool extends AbstractTool {

    private static final String COUNT_PROMPT_TEMPLATE = """
            Has recibido una pregunta del usuario cuya intención es contar en cuántos documentos se menciona un término concreto.
            
            La pregunta original fue:
            "%s"
            
            Ya se ha realizado el conteo y se ha determinado que el número total de documentos relevantes es: %d
            
            Tu tarea es:
            - Redactar una respuesta clara y breve en español.
            - Usar directamente el número proporcionado, sin modificarlo.
            - No inventes detalles adicionales ni des explicaciones del proceso.
            - No repitas la pregunta.
            """;

    public CountDocumentsTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject entities = ctx.nerEntities();

        List<Document> documents = retrieveAllDocuments(query);

        long count = (entities == null)
                ? countWithoutEntities(query, documents)
                : countWithEntities(query, entities, documents);

        String response = chatClient
                .prompt()
                .user(COUNT_PROMPT_TEMPLATE.formatted(query, count))
                .call()
                .content()
                .trim();

        return ToolResult.from(response, CountDocumentsTool.class);
    }

    private long countWithoutEntities(String query, List<Document> docs) {
        String keywordsRaw = extractKeywordsFromQuery(query);
        String[] keywords = keywordsRaw.split(" ");

        if (keywords.length == 0 || keywordsRaw.equals("[vacío]")) {
            throw new RuntimeException("No se han encontrado palabras clave relevantes para realizar la búsqueda.");
        }

        return docs.stream()
                .filter(doc -> containsAnyKeyword(doc.getContent(), keywords))
                .count();
    }

    private long countWithEntities(String query, JSONObject nerEntities, List<Document> docs) {
        JSONObject entidades = nerEntities.optJSONObject("entities");
        JSONObject filtros = entidades != null ? entidades.optJSONObject("filters") : null;

        List<String> fechas = safeExtractList(filtros, "date");
        List<String> personas = safeExtractList(entidades, "person");
        List<String> secciones = safeExtractList(filtros, "section");

        return docs.stream().filter(doc -> {
            String content = doc.getContent().toLowerCase();

            boolean matchFecha = fechas.isEmpty() || fechas.stream().anyMatch(f -> content.contains(f.toLowerCase()));
            boolean matchPersona = personas.isEmpty() || personas.stream().anyMatch(p -> content.contains(p.toLowerCase()));
            boolean matchSeccion = secciones.isEmpty() || secciones.stream().anyMatch(s -> content.contains(s.toLowerCase()));
            boolean matchSemantico = containsRelevantPhrase(content, query);

            return matchFecha && matchPersona && matchSeccion && matchSemantico;
        }).count();
    }

    private List<String> safeExtractList(JSONObject json, String key) {
        if (json == null || json.optJSONArray(key) == null) return List.of();
        return json.optJSONArray(key).toList().stream().map(Object::toString).toList();
    }
}
