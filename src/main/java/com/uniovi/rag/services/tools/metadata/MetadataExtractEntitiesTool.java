package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class MetadataExtractEntitiesTool extends AbstractMetadataTool {

    public MetadataExtractEntitiesTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject nerEntities = ctx.nerEntities();

        List<Document> docs = retrieveAllDocuments(query);
        if (docs.isEmpty()) throw new RuntimeException("No se encontraron documentos relacionados con la consulta.");

        Document doc = docs.getFirst();
        Map<String, Object> metadata = doc.getMetadata();

        // 1. NER explícito
        JSONObject entities = nerEntities != null ? nerEntities.optJSONObject("entities") : null;
        if (entities != null) {
            JSONArray persons = entities.optJSONArray("person");
            if (persons != null && !persons.isEmpty()) {
                List<String> asistentes = extractAsList(metadata, "attendees");
                return ToolResult.from("Personas asistentes: " + formatList(asistentes), getClass());
            }

            JSONObject filters = entities.optJSONObject("filters");
            if (filters != null) {
                if (filters.has("topic") || filters.has("section")) {
                    return ToolResult.from("Puntos del orden del día:\n" + formatAgenda(metadata), getClass());
                }
                if (filters.has("place")) {
                    return ToolResult.from("Lugar de la reunión: " + metadata.getOrDefault("place", "[no registrado]"), getClass());
                }
            }

            // Presidente / Secretario
            if (entities.optString("answer_type", "").equalsIgnoreCase("presidente") ||
                    entities.optString("answer_type", "").equalsIgnoreCase("secretario")) {
                return ToolResult.from(
                        "Presidente: " + metadata.getOrDefault("president", "[no registrado]") + "\n" +
                                "Secretario/a: " + metadata.getOrDefault("secretary", "[no registrado]"),
                        getClass()
                );
            }
        }
        // 2. Fallback por keywords

        String[] keywords = extractKeywordsFromQuery(query).split("\\s+");
        Set<String> lowerKeywords = Arrays.stream(keywords).map(String::toLowerCase).collect(Collectors.toSet());

        if (containsKeyword(lowerKeywords, List.of("attendees", "people", "persons"))) {
            List<String> asistentes = extractAsList(metadata, "attendees");
            return ToolResult.from("Asistieron " + asistentes.size() + " personas:\n" + formatList(asistentes), getClass());
        }

        if (containsKeyword(lowerKeywords, List.of("president"))) {
            return ToolResult.from("Presidente: " + metadata.getOrDefault("president", "[no registrado]"), getClass());
        }

        if (containsKeyword(lowerKeywords, List.of("secretary"))) {
            return ToolResult.from("Secretario/a: " + metadata.getOrDefault("secretary", "[no registrado]"), getClass());
        }

        if (containsKeyword(lowerKeywords, List.of("entities", "organizations", "companies"))) {
            List<String> entidades = extractAsList(metadata, "mentionedEntities");
            return ToolResult.from("Entidades mencionadas:\n" + formatList(entidades), getClass());
        }

        if (containsKeyword(lowerKeywords, List.of("agenda", "section", "topic"))) {
            return ToolResult.from("Puntos del orden del día:\n" + formatAgenda(metadata), getClass());
        }

        throw new RuntimeException("No se ha podido determinar qué tipo de entidad desea listar el usuario.");
    }

    private boolean containsKeyword(Set<String> keywords, List<String> terms) {
        return terms.stream().anyMatch(keywords::contains);
    }

    private List<String> extractAsList(Map<String, Object> metadata, String key) {
        Object value = metadata.getOrDefault(key, List.of());
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toList());
        }
        return List.of();
    }

    private String formatList(List<String> values) {
        return values.isEmpty()
                ? "[no se encontraron resultados]"
                : values.stream().map(v -> "- " + v).collect(Collectors.joining("\n"));
    }

    private String formatAgenda(Map<String, Object> metadata) {
        Object obj = metadata.get("agenda");
        if (obj instanceof Map<?, ?> map) {
            return map.keySet().stream()
                    .map(Object::toString)
                    .map(t -> "- " + t)
                    .collect(Collectors.joining("\n"));
        }
        return "[agenda no disponible]";
    }
}
