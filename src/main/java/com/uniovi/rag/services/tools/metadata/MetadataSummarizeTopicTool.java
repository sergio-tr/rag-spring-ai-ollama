package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.*;
import java.util.stream.Collectors;

import static com.uniovi.rag.utils.InfoExtractor.containsAnyKeyword;

public class MetadataSummarizeTopicTool extends AbstractMetadataTool {

    private static final int MAX_TOTAL_EXAMPLES = 10;
    private static final int MAX_PER_MINUTE = 2;

    private static final String PROMPT_TEMPLATE = """
            El usuario quiere un resumen claro y preciso sobre el tema siguiente:
            
            Pregunta: "%s"
            
            Fragmentos relevantes extraídos de varias actas:
            
            %s
            
            Redacta un resumen informativo y objetivo en español centrado exclusivamente en ese tema. No inventes datos. Menciona fechas si son útiles para el contexto.
            """;

    public MetadataSummarizeTopicTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject nerEntities = ctx.nerEntities();
        List<String> terms = extractRelevantTerms(nerEntities, query);

        List<Document> docs = retrieveAllDocuments(query);
        if (docs.isEmpty()) {
            throw new RuntimeException("No se encontraron documentos relacionados con la consulta.");
        }

        Map<String, List<Document>> docsByMinute = docs.stream()
                .filter(doc -> doc.getMetadata().containsKey("id"))
                .collect(Collectors.groupingBy(doc -> (String) doc.getMetadata().get("id")));

        List<String> fragmentosRelevantes = new ArrayList<>();

        for (List<Document> group : docsByMinute.values()) {
            Map<String, Object> metadata = group.getFirst().getMetadata();
            String fecha = Optional.ofNullable(metadata.get("date")).map(Object::toString).orElse("acta sin fecha");

            List<String> matchedFragments = group.stream()
                    .flatMap(doc -> Arrays.stream(doc.getContent().split("(?<=\\.)\\s*\\n+")))
                    .map(String::trim)
                    .filter(frag -> containsAnyKeyword(frag, terms.toArray(new String[0])))
                    .distinct()
                    .limit(MAX_PER_MINUTE)
                    .toList();

            if (matchedFragments.isEmpty()) {
                List<String> topics = (List<String>) metadata.getOrDefault("topics", List.of());
                if (containsAnyKeyword(String.join(" ", topics), terms.toArray(new String[0]))) {
                    matchedFragments = List.of("(Mención en metadatos: " + String.join(", ", topics) + ")");
                }
            }

            if (!matchedFragments.isEmpty()) {
                fragmentosRelevantes.add("**Acta del " + fecha + "**\n" + matchedFragments.stream()
                        .map(f -> "- " + f)
                        .collect(Collectors.joining("\n")));
            }

            if (fragmentosRelevantes.size() >= MAX_TOTAL_EXAMPLES) break;
        }

        if (fragmentosRelevantes.isEmpty()) {
            return ToolResult.from("No se encontraron fragmentos relevantes sobre el tema: \"" + query + "\".", getClass());
        }

        String joined = String.join("\n\n", fragmentosRelevantes.subList(0, Math.min(fragmentosRelevantes.size(), MAX_TOTAL_EXAMPLES)));

        String resumen = chatClient
                .prompt()
                .user(PROMPT_TEMPLATE.formatted(query, joined))
                .call()
                .content()
                .strip();

        return ToolResult.from(resumen, getClass());
    }

    private List<String> extractRelevantTerms(JSONObject nerEntities, String query) {
        Set<String> terms = new LinkedHashSet<>();

        if (nerEntities != null && nerEntities.has("entities")) {
            JSONObject entities = nerEntities.getJSONObject("entities");
            JSONObject filters = entities.optJSONObject("filters");

            for (String field : List.of("topic", "section")) {
                JSONArray arr = (filters != null && filters.has(field)) ? filters.optJSONArray(field) : null;
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        String term = arr.optString(i).trim().toLowerCase();
                        if (!term.isBlank()) terms.add(term);
                    }
                }
            }
        }

        if (terms.isEmpty()) {
            String[] keywords = extractKeywordsFromQuery(query).split("\\s+");
            terms.addAll(Arrays.asList(keywords));
        }

        return new ArrayList<>(terms);
    }
}
