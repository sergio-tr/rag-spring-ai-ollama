package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.uniovi.rag.utils.InfoExtractor.containsAnyKeyword;
import static com.uniovi.rag.utils.InfoExtractor.extractRelevantFragment;

public class MetadataFindParagraphTool extends AbstractMetadataTool {

    public MetadataFindParagraphTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject nerEntities = ctx.nerEntities();
        String[] keywords = extractKeywordsFromQuery(query).split("\\s+");

        List<Document> docs = retrieveAllDocuments(query);

        // Agrupar por acta completa (minuteId) para evitar fragmentación
        Map<String, List<Document>> groupedByMinute = docs.stream()
                .filter(doc -> doc.getMetadata().containsKey("id"))
                .collect(Collectors.groupingBy(doc -> (String) doc.getMetadata().get("id")));

        List<String> resultados = new ArrayList<>();

        for (Map.Entry<String, List<Document>> entry : groupedByMinute.entrySet()) {
            List<Document> grupo = entry.getValue();
            Map<String, Object> metadata = grupo.getFirst().getMetadata();
            String fecha = (String) metadata.getOrDefault("date", null);
            String titulo = fecha != null ? "Acta del " + fecha : "[Acta sin fecha]";

            boolean match = grupo.stream().anyMatch(doc -> matchesBooleanCondition(doc, keywords, nerEntities));
            if (!match) continue;

            List<String> encontrados = grupo.stream()
                    .flatMap(doc -> Arrays.stream(doc.getContent().split("(?<=\\.)\\s*\\n+")))
                    .map(String::trim)
                    .filter(p -> containsAnyKeyword(p, keywords))
                    .distinct()
                    .limit(5)
                    .collect(Collectors.toList());

            if (encontrados.isEmpty()) {
                String combined = grupo.stream().map(Document::getContent).collect(Collectors.joining("\n"));
                String fallback = extractRelevantFragment(combined, query);
                if (!fallback.isBlank()) {
                    encontrados = List.of(fallback);
                }
            }

            if (!encontrados.isEmpty()) {
                String cuerpo = encontrados.stream()
                        .map(p -> "- " + p)
                        .collect(Collectors.joining("\n"));
                resultados.add("**" + titulo + "**\n" + cuerpo);
            }
        }

        if (resultados.isEmpty()) {
            return ToolResult.from("No se encontraron fragmentos relevantes en las actas para la consulta: \"" + query + "\".", getClass());
        }

        return ToolResult.from(String.join("\n\n", resultados), getClass());
    }
}
