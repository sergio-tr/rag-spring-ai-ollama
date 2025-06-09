package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;

import static com.uniovi.rag.utils.InfoExtractor.*;

public class CountAndExplainTool extends AbstractTool {

    private static final String PROMPT_TEMPLATE = """
            El usuario quiere saber en qué actas se trata el siguiente tema:
            
            Pregunta: "%s"
            
            Ya se han detectado %d documentos relevantes. A continuación se listan fragmentos de los documentos que contienen el tema:
            
            %s
            
            Tu tarea es redactar una respuesta breve y clara en español indicando dónde se menciona el tema, utilizando los fragmentos mostrados.
            """;

    public CountAndExplainTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject entities = ctx.nerEntities();

        List<Document> docs = retrieveAllDocuments(query);
        List<String> fragments;

        if (entities == null) {
            fragments = docs.stream()
                    .filter(doc -> containsAnyKeyword(doc.getContent(), extractKeywordsFromQuery(query).split(" ")))
                    .map(doc -> "- Acta: " + extractDate(doc.getContent()) + "\n" +
                            extractRelevantFragment(doc.getContent().toLowerCase(), query))
                    .distinct()
                    .toList();

//            fragments = docs.stream()
//                    .filter(doc -> containsAnyKeyword(doc.getContent(), extractKeywordsFromQuery(query).split(" ")))
//                    .map(doc -> "- Acta: " + extractDate(doc.getContent()) + "\n" +
//                            extractRelevantFragment(doc.getContent().toLowerCase(), query))
//                    .distinct()
//                    .limit(10)
//                    .toList();

        } else {
            JSONObject ents = entities.optJSONObject("entities");
            JSONObject filtros = ents != null ? ents.optJSONObject("filters") : null;

            List<String> personas = safeExtractList(ents, "person");
            List<String> fechas = safeExtractList(filtros, "date");

            fragments = docs.stream()
                    .filter(doc -> {
                        String content = doc.getContent().toLowerCase();
                        boolean matchFecha = fechas.isEmpty() || fechas.stream().anyMatch(f -> content.contains(f.toLowerCase()));
                        boolean matchPersona = personas.isEmpty() || personas.stream().anyMatch(p -> content.contains(p.toLowerCase()));
                        boolean matchSemantico = containsRelevantPhrase(content, query);
                        return matchFecha && matchPersona && matchSemantico;
                    })
                    .map(doc -> "- Acta: " + extractDate(doc.getContent()) + "\n" +
                            extractRelevantFragment(doc.getContent().toLowerCase(), query))
                    .distinct()
                    .limit(10)
                    .toList();
        }

        if (fragments.isEmpty()) {
            throw new RuntimeException("No se encontró información relacionada con la consulta: \"" + query + "\" en las actas disponibles.");
        }

        String joined = String.join("\n", fragments);
        String prompt = PROMPT_TEMPLATE.formatted(query, fragments.size(), joined);

        String respuesta = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip();

        return ToolResult.from(respuesta, getClass());
    }

    private List<String> safeExtractList(JSONObject json, String key) {
        if (json == null || json.optJSONArray(key) == null) return List.of();
        return json.optJSONArray(key).toList().stream().map(Object::toString).toList();
    }
}
