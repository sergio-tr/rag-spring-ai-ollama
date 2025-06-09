package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;

import static com.uniovi.rag.utils.InfoExtractor.*;

public class SummarizeMeetingTool extends AbstractTool {

    private static final String SUMMARY_PROMPT_WITH_FILTERS = """
            A continuación se presentan fragmentos de actas de reuniones relacionadas con la consulta: "%s".
            
            Filtros contextuales aplicados: %s
            
            Fragmentos:
            %s
            
            Redacta un resumen breve y claro en español, indicando los puntos clave mencionados.
            Evita repetir frases literales y organiza la información de forma clara.
            """;

    private static final String SUMMARY_PROMPT_NO_FILTERS = """
            A continuación se presentan fragmentos de actas de reuniones relacionadas con la consulta: "%s".
            
            Fragmentos:
            %s
            
            Redacta un resumen breve y claro en español, indicando los puntos clave mencionados.
            Evita repetir frases literales y organiza la información de forma clara.
            """;

    public SummarizeMeetingTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();

        List<String> dates = new ArrayList<>();
        List<String> presidents = new ArrayList<>();
        List<String> secretaries = new ArrayList<>();

        if (ner != null && ner.has("entities")) {
            JSONObject filters = ner.optJSONObject("entities").optJSONObject("filters");
            if (filters != null) {
                dates = optArrayToList(filters, "date");
                presidents = optArrayToList(filters, "presidente");
                secretaries = optArrayToList(filters, "secretario");
            }
        } else {
            String filterPrompt = """
                    Extrae del siguiente texto posibles valores para los filtros: fecha, presidente y secretario, en formato JSON:
                    Consulta: "%s"
                    Resultado:
                    {
                      "fechas": [],
                      "presidentes": [],
                      "secretarios": []
                    }
                    """.formatted(query);
            String json = chatClient.prompt().user(filterPrompt).call().content();
            JSONObject parsed = new JSONObject(json);
            dates = optArrayToList(parsed, "fechas");
            presidents = optArrayToList(parsed, "presidentes");
            secretaries = optArrayToList(parsed, "secretarios");
        }

        List<Document> documents = retrieveAllDocuments(query);
        StringBuilder fragments = new StringBuilder();
        int included = 0;

        for (Document doc : documents) {
            String content = doc.getContent();
            String date = extractDate(content);
            String president = extractLiteralField("presidente", content);
            String secretary = extractLiteralField("secretario", content);
            String agenda = extractAgenda(content);

            boolean matchDate = dates.isEmpty() || dates.stream().anyMatch(date::contains);
            boolean matchPresident = presidents.isEmpty() || presidents.stream().anyMatch(p -> president != null && president.toLowerCase().contains(p.toLowerCase()));
            boolean matchSecretary = secretaries.isEmpty() || secretaries.stream().anyMatch(s -> secretary != null && secretary.toLowerCase().contains(s.toLowerCase()));

            boolean matchAgenda = false;
            if (agenda != null && !agenda.isBlank()) {
                matchAgenda = agenda.lines().anyMatch(line -> containsRelevantPhrase(line, query));
            }

            if (!(matchDate || matchPresident || matchSecretary || matchAgenda)) continue;

            for (String p : content.split("(?<=[.:?])\\s*([\\n\\r])+")) {
                if (containsRelevantPhrase(p, query)) {
                    fragments.append("• ").append(p.trim()).append("\n");
                    if (++included >= 10) break;
                }
            }

            if (included >= 10) break;
        }

        if (included == 0) {
            throw new RuntimeException("No se encontraron fragmentos relevantes para resumir.");
        }

        String filtersSummary = (!dates.isEmpty() || !presidents.isEmpty() || !secretaries.isEmpty())
                ? "fechas=" + dates + ", presidentes=" + presidents + ", secretarios=" + secretaries
                : "";

        String prompt = filtersSummary.isBlank()
                ? SUMMARY_PROMPT_NO_FILTERS.formatted(query, fragments.toString())
                : SUMMARY_PROMPT_WITH_FILTERS.formatted(query, filtersSummary, fragments.toString());

        String result = chatClient.prompt().user(prompt).call().content();
        return ToolResult.from(result.strip(), getClass());
    }

    private List<String> optArrayToList(JSONObject obj, String key) {
        return obj.optJSONArray(key) != null
                ? obj.getJSONArray(key).toList().stream().map(Object::toString).toList()
                : List.of();
    }
}
