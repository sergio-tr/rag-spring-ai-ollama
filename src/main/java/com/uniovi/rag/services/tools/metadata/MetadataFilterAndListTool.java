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

public class MetadataFilterAndListTool extends AbstractMetadataTool {

    public MetadataFilterAndListTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();
        List<Document> docs = retrieveAllDocuments(query);

        if (docs.isEmpty()) {
            throw new RuntimeException("No se encontraron documentos relacionados con la consulta.");
        }

        // Extraer filtros NER
        Set<String> fechas = extractNERSet(ner, "date");
        Set<String> lugares = extractNERSet(ner, "place");
        Set<String> presidentes = extractNERSet(ner, "president");
        Set<String> secretarios = extractNERSet(ner, "secretary");
        Set<String> temas = extractNERSet(ner, "topic");

        // Si no hay NER, usar fallback a keywords
        if (fechas.isEmpty() && lugares.isEmpty() && presidentes.isEmpty() && secretarios.isEmpty() && temas.isEmpty()) {
            String[] keywords = extractKeywordsFromQuery(query).split("\\s+");
            temas = Arrays.stream(keywords).collect(Collectors.toSet());
        }

        List<String> resultados = new ArrayList<>();

        for (Document doc : docs) {
            Map<String, Object> meta = doc.getMetadata();

            if (!matches(meta, fechas, "date")) continue;
            if (!matches(meta, lugares, "place")) continue;
            if (!matches(meta, presidentes, "president")) continue;
            if (!matches(meta, secretarios, "secretary")) continue;
            if (!matchesList(meta, temas, "topics")) continue;

            String fecha = Optional.ofNullable(meta.get("date")).map(Object::toString).orElse("Fecha desconocida");

            String resumen = Optional.ofNullable(meta.get("summary"))
                    .map(Object::toString)
                    .filter(s -> !s.isBlank())
                    .orElseGet(() -> fallbackSummary(doc.getContent(), query));

            resultados.add("🗓️ *Acta del " + fecha + "*:\n" + resumen.strip());
        }

        if (resultados.isEmpty()) {
            throw new RuntimeException("No se encontraron actas que cumplan los filtros y la consulta.");
        }

        return ToolResult.from(String.join("\n\n", resultados), getClass());
    }

    private boolean matches(Map<String, Object> metadata, Set<String> filters, String field) {
        if (filters.isEmpty()) return true;
        String value = Optional.ofNullable(metadata.get(field)).map(Object::toString).orElse("").toLowerCase();
        return filters.stream().anyMatch(f -> value.contains(f.toLowerCase()));
    }

    private boolean matchesList(Map<String, Object> metadata, Set<String> filters, String field) {
        if (filters.isEmpty()) return true;
        Object val = metadata.get(field);
        if (val instanceof List<?> list) {
            List<String> normalized = list.stream().map(Object::toString).map(String::toLowerCase).toList();
            return filters.stream().anyMatch(f -> normalized.stream().anyMatch(i -> i.contains(f.toLowerCase())));
        }
        return false;
    }

    private Set<String> extractNERSet(JSONObject ner, String key) {
        if (ner == null || !ner.has("entities")) return Set.of();

        JSONObject entities = ner.optJSONObject("entities");
        JSONObject filters = entities.optJSONObject("filters");

        JSONArray values = (filters != null && filters.has(key))
                ? filters.optJSONArray(key)
                : entities.optJSONArray(key);

        if (values == null) return Set.of();

        return values.toList().stream()
                .map(Object::toString)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());
    }

    private String fallbackSummary(String content, String query) {
        String fragment = content.length() > 1000 ? content.substring(0, 1000) + "..." : content;
        String prompt = """
                Resume en 3 frases como máximo lo más relevante de este fragmento relacionado con:
                "%s"
                ---
                %s
                ---
                """.formatted(query, fragment);

        return chatClient.prompt().user(prompt).call().content().strip();
    }
}
