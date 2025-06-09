package com.uniovi.rag.services.tools;

import com.uniovi.rag.services.retriever.ContextRetriever;
import com.uniovi.rag.utils.InfoExtractor;
import org.json.JSONObject;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;

import java.util.ArrayList;
import java.util.List;

public class FindParagraphTool extends AbstractTool {

    public FindParagraphTool(ChatClient chatClient, ContextRetriever retriever) {
        super(chatClient, retriever);
    }

    @Override
    public ToolResult execute(ToolExecutionContext ctx) {
        String query = ctx.query();
        JSONObject ner = ctx.nerEntities();

        JSONObject entities = ner.optJSONObject("entities") != null ? ner.optJSONObject("entities") : new JSONObject();
        JSONObject filters = entities.optJSONObject("filters") != null ? entities.optJSONObject("filters") : new JSONObject();

        List<String> people = entities.optJSONArray("person") != null ?
                entities.optJSONArray("person").toList().stream().map(Object::toString).toList() : List.of();
        List<String> times = filters.optJSONArray("time") != null ?
                filters.optJSONArray("time").toList().stream().map(Object::toString).toList() : List.of();
        List<String> sections = filters.optJSONArray("section") != null ?
                filters.optJSONArray("section").toList().stream().map(Object::toString).toList() : List.of();

        List<Document> docs = retrieveAllDocuments(query);
        List<String> results = new ArrayList<>();

        for (Document doc : docs) {
            String content = doc.getContent();
            String[] paragraphs = content.split("(?<=[.:?])\\s*([\\n\\r])+");

            for (String p : paragraphs) {
                String lower = p.toLowerCase();

                boolean matchTime = times.stream().anyMatch(f -> lower.contains(f.toLowerCase()));
                boolean matchPerson = people.stream().anyMatch(pe -> lower.contains(pe.toLowerCase()));
                boolean matchSection = sections.stream().anyMatch(s -> lower.contains(s.toLowerCase()));
                boolean matchSemantic = verifyParagraphSemantically(query, p);

                if ((times.isEmpty() || matchTime) &&
                        (people.isEmpty() || matchPerson) &&
                        (sections.isEmpty() || matchSection) &&
                        matchSemantic) {

                    String time = InfoExtractor.extractDate(content);
                    results.add("Acta del " + time + ":\n" + p.trim());
                }
            }
        }

        if (results.isEmpty()) {
            return ToolResult.from(" No se encontró ningún párrafo que responda directamente a: \"" + query + "\"", getClass());
        }

        return ToolResult.from("Párrafos relevantes:\n\n" + String.join("\n\n", results), getClass());
    }

    private boolean verifyParagraphSemantically(String query, String paragraph) {
        String prompt = """
                Esta es la consulta del usuario:
                "%s"
                
                Y este es un párrafo del acta:
                "%s"
                
                ¿El párrafo responde de forma clara o parcial a la consulta? Responde solo con "sí" o "no".
                """.formatted(query, paragraph);

        String result = chatClient
                .prompt()
                .user(prompt)
                .call()
                .content()
                .strip()
                .toLowerCase();

        return result.contains("sí");
    }
}
