package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.List;
import java.util.stream.Stream;

import static com.uniovi.rag.utils.InfoExtractor.containsAnyKeyword;
import static com.uniovi.rag.utils.InfoExtractor.containsRelevantPhrase;

public class DecisionExtractionTool extends AbstractTool {

    private static final String PROMPT = """
            A continuación se presentan decisiones tomadas en reuniones, relacionadas con el tema: "%s".
            
            Decisiones:
            %s
            
            Lista todas las decisiones encontradas relevantes para el tema. Resume cada decisión brevemente en una línea.
            """;

    public DecisionExtractionTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();

        List<Document> documents = retrieveAllDocuments(query);
        if (documents.isEmpty()) {
            throw new RuntimeException("No se encontraron documentos relevantes.");
        }

        String[] keywordList = extractKeywordsFromQuery(query).split("\\s+");

        List<String> decisionFragments = documents.stream()
                .flatMap(doc -> Stream.of(doc.getContent().split("(?<=[.:?])\\s*([\\n\\r])+")))
                .map(String::trim)
                .filter(p -> containsAnyKeyword(p, keywordList))
                .filter(p -> containsRelevantPhrase(p, query))
                .filter(this::isDecisionParagraph)
                .limit(10)
                .toList();

        if (decisionFragments.isEmpty()) {
            throw new RuntimeException("No se encontraron decisiones relevantes para la consulta.");
        }

        String prompt = PROMPT.formatted(query, String.join("\n", decisionFragments));
        String result = chatClient.prompt().user(prompt).call().content();

        return ToolResult.from(result, getClass());
    }

    private boolean isDecisionParagraph(String paragraph) {
        String prompt = """
                El siguiente texto es parte de un acta de una reunión:
                ---
                %s
                ---
                ¿Contiene este texto una decisión tomada durante la reunión?
                Responde únicamente con 'sí' o 'no'.
                """.formatted(paragraph.strip());

        String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();

        return response.contains("sí");
    }
}
