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

import static com.uniovi.rag.utils.InfoExtractor.extractLiteralField;

public class MetadataGetFieldTool extends AbstractMetadataTool {

    public MetadataGetFieldTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query().trim();
        JSONObject nerEntities = ctx.nerEntities();
        String[] keywords = extractKeywordsFromQuery(query).split("\\s+");

        List<Document> docs = retrieveAllDocuments(query);
        if (docs.isEmpty()) {
            throw new RuntimeException("No se encontraron documentos relevantes para la consulta.");
        }

        for (Document doc : docs) {
            Map<String, Object> metadata = doc.getMetadata();

            // 1. Si hay NER, usar filtros primero
            if (nerEntities != null) {
                JSONObject entities = nerEntities.optJSONObject("entities");
                if (entities != null) {
                    JSONObject filters = entities.optJSONObject("filters");
                    if (filters != null) {
                        for (String field : List.of("date", "place", "startTime", "endTime")) {
                            if (filters.has(field) && metadata.containsKey(field)) {
                                return ToolResult.from(formatField(field, metadata.get(field)), getClass());
                            }
                        }
                    }

                    if (entities.has("person")) {
                        JSONArray persons = entities.getJSONArray("person");
                        for (int i = 0; i < persons.length(); i++) {
                            String name = persons.getString(i).toLowerCase();
                            for (String role : List.of("president", "secretary")) {
                                String metaValue = Optional.ofNullable(metadata.get(role)).map(Object::toString).orElse("");
                                if (metaValue.toLowerCase().contains(name)) {
                                    return ToolResult.from(formatField(role, metaValue), getClass());
                                }
                            }
                        }
                    }
                }
            }

            // 2. Fallback por keywords si no hay NER
            Set<String> keywordSet = Arrays.stream(keywords).map(String::toLowerCase).collect(Collectors.toSet());
            for (String field : List.of("date", "place", "startTime", "endTime", "president", "secretary")) {
                if (keywordSet.contains(field.toLowerCase()) && metadata.containsKey(field)) {
                    return ToolResult.from(formatField(field, metadata.get(field)), getClass());
                }
            }


            // 3. Intento final: extraer literal del contenido textual si no está en metadatos
            String value = extractLiteralField(query, doc.getContent());
            if (value != null) {
                return ToolResult.from("Dato encontrado en el contenido: " + value, getClass());
            }
        }

        throw new RuntimeException("No se pudo extraer el valor literal solicitado.");
    }

    private String formatField(String key, Object value) {
        String label = key.replace("_", " ");
        return capitalize(label) + ": " + value;
    }

    private String capitalize(String text) {
        if (text == null || text.isBlank()) return "";
        return text.substring(0, 1).toUpperCase() + text.substring(1);
    }
}
