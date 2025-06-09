package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.uniovi.rag.utils.InfoExtractor.*;

public class FilterAndListTool extends AbstractTool {

    public FilterAndListTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();

        List<String> persons;
        List<String> dates;
        List<String> locations;
        List<String> sections;

        if (ner != null && ner.has("entities")) {
            JSONObject entities = ner.optJSONObject("entities");
            JSONObject filters = entities.optJSONObject("filters") != null ? entities.optJSONObject("filters") : new JSONObject();

            persons = extractListFromJSON(entities, "person");
            dates = extractListFromJSON(filters, "date");
            locations = extractListFromJSON(filters, "place");
            sections = extractListFromJSON(filters, "section");
        } else {
            String filterPrompt = """
                    Dado el texto de esta consulta:
                    "%s"
                    
                    Devuelve un JSON con los siguientes posibles filtros si se mencionan:
                    {
                      "personas": [],
                      "fechas": [],
                      "lugares": [],
                      "secciones": []
                    }
                    """.formatted(query);

            String jsonResult = chatClient.prompt().user(filterPrompt).call().content();
            JSONObject extracted = new JSONObject(jsonResult);

            persons = extractListFromJSON(extracted, "personas");
            dates = extractListFromJSON(extracted, "fechas");
            locations = extractListFromJSON(extracted, "lugares");
            sections = extractListFromJSON(extracted, "secciones");
        }

        List<Document> documents = retrieveAllDocuments(query);
        List<String> results = new ArrayList<>();

        for (Document document : documents) {
            String content = document.getContent();
            String lowerContent = content.toLowerCase();

            boolean matches = matchesAllFilters(query, lowerContent, dates, locations, persons, sections);

            if (matches) {
                String summary = summarizeMatchingFragment(content, query);
                results.add("Acta del " + extractDate(content) + ":\n" + summary);
            }
        }

        if (results.isEmpty()) {
            throw new RuntimeException("No se encontraron actas que cumplan todas las condiciones especificadas en la consulta.");
        }

        return ToolResult.from(String.join("\n\n", results), getClass());
    }

    private List<String> extractListFromJSON(JSONObject obj, String key) {
        if (obj != null && obj.has(key)) {
            return obj.getJSONArray(key).toList().stream().map(Object::toString).collect(Collectors.toList());
        }
        return List.of();
    }

    private String summarizeMatchingFragment(String content, String query) {
        String fragment = extractRelevantFragment(content, query);
        String prompt = """
                Resume en dos frases como máximo el fragmento del siguiente texto que conteste a esta consulta: "%s"
                Texto:
                %s
                """.formatted(query, fragment);

        return chatClient.prompt().user(prompt).call().content().strip();
    }

    private boolean matchesAllFilters(String query, String content, List<String> dates, List<String> locations, List<String> persons, List<String> sections) {
        boolean dateMatch = dates.isEmpty() || dates.stream().anyMatch(date -> content.contains(date.toLowerCase()));
        boolean locationMatch = locations.isEmpty() || locations.stream().anyMatch(loc -> content.contains(loc.toLowerCase()));
        boolean personMatch = persons.isEmpty() || persons.stream().anyMatch(per -> content.contains(per.toLowerCase()));
        boolean sectionMatch = sections.isEmpty() || sections.stream().anyMatch(sec -> content.contains(sec.toLowerCase()));
        boolean semanticMatch = containsRelevantPhrase(content, query);

        return dateMatch && locationMatch && personMatch && sectionMatch && semanticMatch;
    }
}
