package com.uniovi.rag.services.tools.metadata;

import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.services.tools.ToolExecutionContext;
import com.uniovi.rag.services.tools.ToolResult;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.text.Normalizer;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.uniovi.rag.utils.InfoExtractor.containsAnyKeyword;

public class MetadataDecisionExtractionTool extends AbstractMetadataTool {

    public MetadataDecisionExtractionTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject nerEntities = ctx.nerEntities();
        List<Document> docs = retrieveAllDocuments(query);

        if (docs.isEmpty()) {
            throw new RuntimeException("No se encontraron actas relevantes.");
        }

        Set<String> keywords = collectKeywords(query, nerEntities);
        List<String> decisions = new ArrayList<>();

        for (Document doc : docs) {
            Map<String, Object> meta = doc.getMetadata();
            if (!matchesNERFilters(meta, nerEntities)) continue;

            Object field = meta.get("decisions");
            if (field instanceof List<?> rawList) {
                rawList.stream()
                        .map(Object::toString)
                        .map(this::normalize)
                        .filter(d -> containsAnyKeyword(d, keywords.toArray(new String[0])))
                        .distinct()
                        .forEach(decisions::add);
            }
        }

        if (decisions.isEmpty()) {
            throw new RuntimeException("No se encontraron decisiones explícitas sobre el tema consultado.");
        }

        // Ya que las decisiones están bien formadas, no usamos prompt si no es necesario:
        String response = decisions.stream()
                .map(d -> "- " + d)
                .distinct()
                .limit(10)
                .collect(Collectors.joining("\n"));

        return ToolResult.from(response, getClass());
    }

    private Set<String> collectKeywords(String query, JSONObject ner) {
        Set<String> terms = new HashSet<>(List.of(extractKeywordsFromQuery(query).split("\\s+")));

        if (ner != null && ner.has("entities")) {
            JSONObject entities = ner.getJSONObject("entities");
            JSONObject filters = entities.optJSONObject("filters");

            Stream.of("person", "topic", "section").forEach(key -> {
                JSONArray arr = (filters != null && filters.has(key)) ? filters.optJSONArray(key) : entities.optJSONArray(key);
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        terms.add(normalize(arr.getString(i)));
                    }
                }
            });
        }

        return terms;
    }

    private boolean matchesNERFilters(Map<String, Object> metadata, JSONObject nerEntities) {
        if (nerEntities == null) return true;

        JSONObject entities = nerEntities.optJSONObject("entities");
        if (entities == null) return true;

        JSONObject filters = entities.optJSONObject("filters");
        if (filters == null) return true;

        for (String field : List.of("date", "place", "section", "topic")) {
            JSONArray values = filters.optJSONArray(field);
            if (values != null && !values.isEmpty()) {
                String metaValue = Optional.ofNullable(metadata.get(field)).map(Object::toString).orElse("").toLowerCase();
                boolean match = false;
                for (int i = 0; i < values.length(); i++) {
                    if (normalize(metaValue).contains(normalize(values.getString(i)))) {
                        match = true;
                        break;
                    }
                }
                if (!match) return false;
            }
        }

        return true;
    }

    private String normalize(String text) {
        if (text == null) return "";
        return Normalizer.normalize(text.toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}", "")
                .trim();
    }
}
