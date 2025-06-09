package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.stream.Stream;

import static com.uniovi.rag.utils.InfoExtractor.containsAnyKeyword;
import static com.uniovi.rag.utils.InfoExtractor.containsRelevantPhrase;

public class SummarizeTopicTool extends AbstractTool {

    private static final String SUMMARY_PROMPT_WITH_ENTITIES = """
            A continuación se presentan fragmentos de actas de reuniones sobre el tema: "%s".
            
            Fragmentos:
            %s
            
            Entidades mencionadas:
            %s
            
            Redacta un resumen breve y claro en español, indicando los puntos clave mencionados sobre el tema.
            Evita repetir frases literales y organiza la información de forma clara.
            """;

    private static final String SUMMARY_PROMPT_NO_ENTITIES = """
            A continuación se presentan fragmentos de actas de reuniones sobre el tema: "%s".
            
            Fragmentos:
            %s
            
            Redacta un resumen breve y claro en español, indicando los puntos clave mencionados sobre el tema.
            Evita repetir frases literales y organiza la información de forma clara.
            """;

    public SummarizeTopicTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject nerEntities = ctx.nerEntities();

        List<Document> documents = retrieveAllDocuments(query);
        if (documents.isEmpty()) {
            throw new RuntimeException("No se encontraron documentos relevantes.");
        }

        String keywords = extractKeywordsFromQuery(query);
        if (keywords == null || keywords.isBlank()) {
            throw new RuntimeException("No se identificaron palabras clave relevantes para buscar en los documentos.");
        }

        List<String> fragments = documents.stream()
                .flatMap(doc -> Stream.of(doc.getContent().split("(?<=[.:?])\\s*([\\n\\r])+")))
                .map(String::trim)
                .filter(p -> containsAnyKeyword(p, keywords.split("\\s+")))
                .filter(p -> containsRelevantPhrase(p, query))
                .limit(10)
                .toList();

        if (fragments.isEmpty()) {
            throw new RuntimeException("No se encontraron fragmentos relevantes para resumir.");
        }

        String prompt = (nerEntities != null && nerEntities.has("entities"))
                ? SUMMARY_PROMPT_WITH_ENTITIES.formatted(query, String.join("\n", fragments), nerEntities.get("entities").toString())
                : SUMMARY_PROMPT_NO_ENTITIES.formatted(query, String.join("\n", fragments));

        String summary = chatClient.prompt().user(prompt).call().content().strip();
        return ToolResult.from(summary, getClass());
    }
}
