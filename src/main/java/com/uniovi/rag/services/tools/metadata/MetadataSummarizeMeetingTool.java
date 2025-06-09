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

public class MetadataSummarizeMeetingTool extends AbstractMetadataTool {

    private static final int MAX_MINUTES = 5;
    private static final int MAX_TOKENS_PER_MINUTE = 800;

    private static final String RESUMEN_PROMPT = """
            A continuación se presentan resumenes parciales y datos extraídos de distintas actas relevantes a la consulta:
            
            Consulta: "%s"
            
            Datos disponibles:
            %s
            
            Redacta un unico resumen completo en español, estructurado y claro, que incluya:
            - Temas discutidos
            - Decisiones tomadas
            - Problemáticas
            - Propuestas
            - Entidades/personas relevantes
            
            No repitas fragmentos ni incluyas encabezados. Sé objetivo, conciso y formal.
            """;

    private static final String RESUMEN_MINUTA_PROMPT = """
            A continuación se presentan fragmentos de una misma acta.
            
            Consulta: "%s"
            
            Fragmentos del acta:
            %s
            
            Redacta un resumen en español de esta acta relacionado con la consulta. Sé breve, claro y enfócate unicamente en el tema.
            """;

    public MetadataSummarizeMeetingTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();

        Set<String> fechas = extractListFromNER(ner, "date");
        Set<String> presidentes = extractListFromNER(ner, "president");
        Set<String> secretarios = extractListFromNER(ner, "secretary");

        List<Document> documents = retrieveAllDocuments(query);
        if (documents.isEmpty()) throw new RuntimeException("No se encontraron actas relevantes.");

        // Agrupar por acta
        Map<String, List<Document>> actas = documents.stream()
                .filter(doc -> doc.getMetadata().containsKey("id"))
                .collect(Collectors.groupingBy(doc -> doc.getMetadata().get("id").toString()));

        List<String> resumenes = new ArrayList<>();

        for (List<Document> group : actas.values()) {
            Map<String, Object> metadata = group.getFirst().getMetadata();

            if (!matchFilters(metadata, fechas, presidentes, secretarios)) continue;

            List<String> contenido = new ArrayList<>();

            for (Document doc : group) {
                String resumenMeta = Optional.ofNullable(doc.getMetadata().get("summary"))
                        .map(Object::toString)
                        .orElse(null);

                if (resumenMeta != null && !resumenMeta.isBlank()) {
                    contenido.add(resumenMeta);
                } else {
                    Arrays.stream(doc.getContent().split("(?<=[.:?])\\s*([\\n\\r])+"))
                            .filter(p -> !p.isBlank() && p.length() > 60)
                            .limit(5)
                            .forEach(p -> contenido.add(p.trim()));
                }

                // Limita por tokens aproximados
                int estimatedTokens = contenido.stream().mapToInt(String::length).sum() / 4;
                if (estimatedTokens > MAX_TOKENS_PER_MINUTE) break;
            }

            if (!contenido.isEmpty()) {
                String actaResumen = chatClient
                        .prompt()
                        .user(RESUMEN_MINUTA_PROMPT.formatted(query, String.join("\n", contenido)))
                        .call()
                        .content()
                        .strip();

                resumenes.add(actaResumen);
            }

            if (resumenes.size() >= MAX_MINUTES) break;
        }

        if (resumenes.isEmpty()) {
            throw new RuntimeException("No se encontraron fragmentos relevantes para resumir.");
        }

        String joined = String.join("\n\n", resumenes);

        String resumenFinal = chatClient
                .prompt()
                .user(RESUMEN_PROMPT.formatted(query, joined))
                .call()
                .content()
                .strip();

        return ToolResult.from(resumenFinal, getClass());
    }

    private boolean matchFilters(Map<String, Object> metadata, Set<String> fechas, Set<String> presidentes, Set<String> secretarios) {
        String all = metadata.toString().toLowerCase();

        boolean matchFecha = fechas.isEmpty() || fechas.stream().anyMatch(f -> all.contains(f.toLowerCase()));
        boolean matchPresidente = presidentes.isEmpty() || presidentes.stream().anyMatch(p -> all.contains(p.toLowerCase()));
        boolean matchSecretario = secretarios.isEmpty() || secretarios.stream().anyMatch(s -> all.contains(s.toLowerCase()));

        return matchFecha && matchPresidente && matchSecretario;
    }

    private Set<String> extractListFromNER(JSONObject ner, String key) {
        if (ner == null || !ner.has("entities")) return Set.of();
        JSONObject entities = ner.getJSONObject("entities");
        JSONObject filters = entities.optJSONObject("filters");

        JSONArray arr = (filters != null && filters.has(key)) ? filters.optJSONArray(key) : entities.optJSONArray(key);
        if (arr == null) return Set.of();

        return arr.toList().stream().map(Object::toString).map(String::trim).filter(s -> !s.isEmpty()).collect(Collectors.toSet());
    }
}
